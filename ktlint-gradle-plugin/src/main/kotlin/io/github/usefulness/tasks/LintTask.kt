package io.github.usefulness.tasks

import io.github.usefulness.KtlintGradleExtension.Companion.DEFAULT_IGNORE_FAILURES
import io.github.usefulness.support.ReporterType
import io.github.usefulness.tasks.lint.LintWorkerAction
import org.gradle.api.file.ProjectLayout
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.TaskAction
import org.gradle.work.InputChanges
import org.gradle.workers.WorkerExecutionException
import org.gradle.workers.WorkerExecutor
import java.io.File
import javax.inject.Inject

@CacheableTask
public open class LintTask @Inject constructor(
    private val workerExecutor: WorkerExecutor,
    objectFactory: ObjectFactory,
    private val projectLayout: ProjectLayout,
) : ConfigurableKtLintTask(
    projectLayout = projectLayout,
    objectFactory = objectFactory,
) {

    @OutputFiles
    public val reports: MapProperty<String, File> = objectFactory.mapProperty(default = emptyMap())

    @Input
    public val ignoreFailures: Property<Boolean> = objectFactory.property(default = DEFAULT_IGNORE_FAILURES)

    @TaskAction
    public fun run(inputChanges: InputChanges) {
        val workQueue = workerExecutor.processIsolation { spec ->
            spec.classpath.setFrom(ktlintClasspath, ruleSetsClasspath)
            spec.forkOptions { options ->
                options.maxHeapSize = workerMaxHeapSize.get()
            }
        }

        workQueue.submit(LintWorkerAction::class.java) { p ->
            p.name.set(name)
            p.files.from(getChangedSources(inputChanges))
            p.projectDirectory.set(projectLayout.projectDirectory.asFile)
            p.reporters.putAll(getReports())
            p.ktLintParams.set(getKtLintParams())
            p.changedEditorConfigFiles.from(getChangedEditorconfigFiles(inputChanges))
        }

        runCatching { workQueue.await() }
            .onFailure { failure ->
                when {
                    ignoreFailures.get() -> Unit
                    failure is WorkerExecutionException -> throw failure.cause ?: failure
                    else -> throw failure
                }
            }
    }

    private fun getReports() = reports.get()
        .mapKeys { (id, _) -> ReporterType.getById(id = id) }
}
