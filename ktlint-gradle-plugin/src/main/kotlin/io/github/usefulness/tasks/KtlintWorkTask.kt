package io.github.usefulness.tasks

import io.github.usefulness.KtlintGradleExtension.Companion.DEFAULT_CHUNK_SIZE
import io.github.usefulness.KtlintGradleExtension.Companion.DEFAULT_DISABLED_RULES
import io.github.usefulness.KtlintGradleExtension.Companion.DEFAULT_EXPERIMENTAL_RULES
import io.github.usefulness.KtlintGradleExtension.Companion.DEFAULT_IGNORE_FAILURES
import io.github.usefulness.support.KtlintRunMode
import io.github.usefulness.support.findApplicableEditorConfigFiles
import io.github.usefulness.tasks.workers.ConsoleReportWorker
import io.github.usefulness.tasks.workers.GenerateReportsWorker
import io.github.usefulness.tasks.workers.KtlintWorker
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileType
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.util.PatternFilterable
import org.gradle.api.tasks.util.PatternSet
import org.gradle.work.ChangeType
import org.gradle.work.FileChange
import org.gradle.work.Incremental
import org.gradle.work.InputChanges
import org.gradle.workers.WorkerExecutor
import java.io.File
import java.util.concurrent.Callable

public abstract class KtlintWorkTask(
    private val workerExecutor: WorkerExecutor,
    private val projectLayout: ProjectLayout,
    objectFactory: ObjectFactory,
    private val patternFilterable: PatternFilterable = PatternSet(),
) : DefaultTask(), PatternFilterable by patternFilterable {

    @Classpath
    public val ktlintClasspath: ConfigurableFileCollection = objectFactory.fileCollection()

    @Classpath
    public val ruleSetsClasspath: ConfigurableFileCollection = objectFactory.fileCollection()

    @Classpath
    public val reportersConfiguration: ConfigurableFileCollection = objectFactory.fileCollection()

    @Input
    public val experimentalRules: Property<Boolean> = objectFactory.property(default = DEFAULT_EXPERIMENTAL_RULES)

    @Input
    public val chunkSize: Property<Int> = objectFactory.property(default = DEFAULT_CHUNK_SIZE)

    @Input
    public val disabledRules: ListProperty<String> = objectFactory.listProperty(default = DEFAULT_DISABLED_RULES.toList())

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Incremental
    internal val editorconfigFiles = objectFactory.fileCollection().apply {
        from(projectLayout.findApplicableEditorConfigFiles().toList())
    }

    @Input
    public val workerMaxHeapSize: Property<String> = objectFactory.property(default = "256m")

    @Input
    public val ignoreFailures: Property<Boolean> = objectFactory.property(default = DEFAULT_IGNORE_FAILURES)

    @PathSensitive(PathSensitivity.RELATIVE)
    @InputFile
    @Optional
    public val baselineFile: RegularFileProperty = objectFactory.fileProperty()

    @OutputFiles
    public val reports: MapProperty<String, File> = objectFactory.mapProperty(default = emptyMap())

    private val allSourceFiles = objectFactory.fileCollection()

    @SkipWhenEmpty // Marks the input incremental: https://github.com/gradle/gradle/issues/17593
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    @IgnoreEmptyDirectories
    public val source: FileCollection = objectFactory.fileCollection()
        .from(Callable { allSourceFiles.asFileTree.matching(patternFilterable) })

    public fun source(vararg sources: Any?): KtlintWorkTask = also { allSourceFiles.from(*sources) }

    public fun setSource(source: Any) {
        allSourceFiles.setFrom(source)
    }

    @Internal
    override fun getIncludes(): MutableSet<String> = patternFilterable.includes

    @Internal
    override fun getExcludes(): MutableSet<String> = patternFilterable.excludes

    internal fun runKtlint(inputChanges: InputChanges, mode: KtlintRunMode) {
        val tempErrorsDir = projectLayout.buildDirectory.dir("ktlint_errors/$name").get()

        val workerQueue = workerExecutor.processIsolation { spec ->
            spec.classpath.setFrom(ktlintClasspath, ruleSetsClasspath)
            spec.forkOptions { options ->
                options.maxHeapSize = workerMaxHeapSize.get()
                options.jvmArgs = listOf("--add-opens=java.base/java.lang=ALL-UNNAMED") // https://youtrack.jetbrains.com/issue/KT-51619
            }
        }

        val chunks = getChangedSources(inputChanges)
            .chunked(chunkSize.get())
        val changedEditorConfigFiles = getChangedEditorconfigFiles(inputChanges)

        chunks.forEachIndexed { index, sources ->
            workerQueue.submit(KtlintWorker::class.java) { p ->
                p.name.set(name)
                p.files.from(sources)
                p.projectDirectory.set(projectLayout.projectDirectory.asFile)
                p.experimentalRules.set(experimentalRules)
                p.disabledRules.set(disabledRules)
                p.changedEditorConfigFiles.from(changedEditorConfigFiles)
                p.discoveredErrors.set(tempErrorsDir.file("errors_$index.bin"))
                p.mode.set(mode)
            }
        }
        workerQueue.await()

        val reporterQueue = workerExecutor.processIsolation { spec ->
            spec.classpath.setFrom(ktlintClasspath, reportersConfiguration)
            spec.forkOptions { options ->
                options.maxHeapSize = workerMaxHeapSize.get()
            }
        }

        val reports = reports.get()
        if (reports.any()) {
            reporterQueue.submit(GenerateReportsWorker::class.java) { p ->
                p.errorsContainer.set(tempErrorsDir)
                p.projectDirectory.set(projectLayout.projectDirectory.asFile)
                p.reporters.putAll(reports)
                p.baselineFile.set(baselineFile)
            }
        }

        reporterQueue.submit(ConsoleReportWorker::class.java) { p ->
            p.errorsContainer.set(tempErrorsDir)
            p.ignoreFailures.set(ignoreFailures)
            p.projectDirectory.set(projectLayout.projectDirectory.asFile)
            p.mode.set(mode)
            p.baselineFile.set(baselineFile)
        }
        reporterQueue.await()
        tempErrorsDir.asFile.delete()
    }
}

internal inline fun <reified T> ObjectFactory.property(default: T? = null): Property<T> =
    property(T::class.java).apply {
        set(default)
    }

internal inline fun <reified T> ObjectFactory.listProperty(default: Iterable<T> = emptyList()): ListProperty<T> =
    listProperty(T::class.java).apply {
        set(default)
    }

internal inline fun <reified K, reified V> ObjectFactory.mapProperty(default: Map<K, V> = emptyMap()): MapProperty<K, V> =
    mapProperty(K::class.java, V::class.java).apply {
        set(default)
    }

internal fun KtlintWorkTask.getChangedEditorconfigFiles(inputChanges: InputChanges) =
    inputChanges.getFileChanges(editorconfigFiles).map(FileChange::getFile)

internal fun KtlintWorkTask.getChangedSources(inputChanges: InputChanges) =
    if (inputChanges.isIncremental && inputChanges.getFileChanges(editorconfigFiles).none()) {
        inputChanges.getFileChanges(source)
            .asSequence()
            .filter { it.fileType == FileType.FILE && it.changeType != ChangeType.REMOVED }
            .map(FileChange::getFile)
            .toList()
    } else {
        source
    }
