package io.github.usefulness.tasks

import io.github.usefulness.support.KtlintRunMode
import org.gradle.api.file.ProjectLayout
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.TaskAction
import org.gradle.work.InputChanges
import org.gradle.workers.WorkerExecutor
import javax.inject.Inject

public open class FormatTask @Inject constructor(
    workerExecutor: WorkerExecutor,
    objectFactory: ObjectFactory,
    projectLayout: ProjectLayout,
) : KtlintWorkTask(
    workerExecutor = workerExecutor,
    projectLayout = projectLayout,
    objectFactory = objectFactory,
) {

    @TaskAction
    public fun run(inputChanges: InputChanges): Unit = runKtlint(
        inputChanges = inputChanges,
        mode = KtlintRunMode.Format,
    )
}
