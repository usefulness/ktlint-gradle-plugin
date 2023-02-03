package io.github.usefulness.tasks

import io.github.usefulness.tasks.workers.LintWorker
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.work.InputChanges
import org.gradle.workers.WorkerExecutor
import javax.inject.Inject

@CacheableTask
public open class LintTask @Inject constructor(
    private val workerExecutor: WorkerExecutor,
    objectFactory: ObjectFactory,
    private val projectLayout: ProjectLayout,
) : KtlintWorkTask(
    projectLayout = projectLayout,
    objectFactory = objectFactory,
) {

    @OutputDirectory
    public val discoveredErrors: DirectoryProperty = objectFactory.directoryProperty().apply {
        value(projectLayout.buildDirectory.dir("ktlint_errors/$name"))
    }

    @TaskAction
    public fun run(inputChanges: InputChanges) {
        val workQueue = workerExecutor.processIsolation { spec ->
            spec.classpath.setFrom(ktlintClasspath, ruleSetsClasspath)
            spec.forkOptions { options ->
                options.maxHeapSize = workerMaxHeapSize.get()
            }
        }

        val chunks = getChangedSources(inputChanges)
            .chunked(chunkSize.get())
        val changedEditorConfigFiles = getChangedEditorconfigFiles(inputChanges)

        chunks.forEachIndexed { index, sources ->
            workQueue.submit(LintWorker::class.java) { p ->
                p.name.set(name)
                p.files.from(sources)
                p.projectDirectory.set(projectLayout.projectDirectory.asFile)
                p.ktLintParams.set(getKtLintParams())
                p.changedEditorConfigFiles.from(changedEditorConfigFiles)
                p.discoveredErrors.set(discoveredErrors.get().file("errors_$index.bin"))
            }
        }

        workQueue.await()
    }
}
