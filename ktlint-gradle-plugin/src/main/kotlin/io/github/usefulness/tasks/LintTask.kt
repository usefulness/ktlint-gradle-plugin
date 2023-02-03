package io.github.usefulness.tasks

import io.github.usefulness.tasks.lint.LintWorkerAction
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.work.InputChanges
import org.gradle.workers.WorkerExecutor
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

    @OutputFile
    public val discoveredErrors: RegularFileProperty = objectFactory.fileProperty().apply {
        value(projectLayout.buildDirectory.file("ktlint_errors_$name.bin"))
    }

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
            p.ktLintParams.set(getKtLintParams())
            p.changedEditorConfigFiles.from(getChangedEditorconfigFiles(inputChanges))
            p.discoveredErrors.set(discoveredErrors)
        }

        workQueue.await()
    }
}
