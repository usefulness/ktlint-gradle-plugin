package io.github.usefulness.tasks

import io.github.usefulness.KtlintGradleExtension.Companion.DEFAULT_IGNORE_FAILURES
import io.github.usefulness.tasks.workers.ConsoleReportWorker
import io.github.usefulness.tasks.workers.GenerateReportsWorker
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.WorkerExecutor
import java.io.File
import javax.inject.Inject

public open class GenerateReportsTask @Inject constructor(
    private val workerExecutor: WorkerExecutor,
    objectFactory: ObjectFactory,
    private val projectLayout: ProjectLayout,
) : DefaultTask() {

    @Classpath
    public val ktlintClasspath: ConfigurableFileCollection = objectFactory.fileCollection()

    @Classpath
    public val reportersConfiguration: ConfigurableFileCollection = objectFactory.fileCollection()

    @Input
    public val workerMaxHeapSize: Property<String> = objectFactory.property(default = "256m")

    @Input
    public val ignoreFailures: Property<Boolean> = objectFactory.property(default = DEFAULT_IGNORE_FAILURES)

    @SkipWhenEmpty
    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    @IgnoreEmptyDirectories
    public val errorsContainer: DirectoryProperty = objectFactory.directoryProperty()

    @OutputFiles
    public val reports: MapProperty<String, File> = objectFactory.mapProperty(default = emptyMap())

    @TaskAction
    public fun run() {
        val workQueue = workerExecutor.processIsolation { spec ->
            spec.classpath.setFrom(ktlintClasspath, reportersConfiguration)
            spec.forkOptions { options ->
                options.maxHeapSize = workerMaxHeapSize.get()
            }
        }

        workQueue.submit(GenerateReportsWorker::class.java) { p ->
            p.errorsContainer.set(errorsContainer)
            p.projectDirectory.set(projectLayout.projectDirectory.asFile)
            p.reporters.putAll(reports.get())
        }

        workQueue.submit(ConsoleReportWorker::class.java) { param ->
            param.errorsContainer.set(errorsContainer)
            param.ignoreFailures.set(ignoreFailures)
            param.projectDirectory.set(projectLayout.projectDirectory.asFile)
        }

        workQueue.await()
    }
}
