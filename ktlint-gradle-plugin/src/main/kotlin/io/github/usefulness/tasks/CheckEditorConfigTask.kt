package io.github.usefulness.tasks

import io.github.usefulness.EditorConfigValidationMode
import io.github.usefulness.EditorConfigValidationMode.BuildFailure
import io.github.usefulness.EditorConfigValidationMode.None
import io.github.usefulness.EditorConfigValidationMode.PrintWarningLogs
import io.github.usefulness.support.isRootEditorConfig
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import javax.inject.Inject

@CacheableTask
public open class CheckEditorConfigTask @Inject constructor(objectFactory: ObjectFactory) : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    internal val editorConfigFiles = objectFactory.fileCollection()

    @Input
    public val mode: Property<EditorConfigValidationMode> = objectFactory.property<EditorConfigValidationMode>(default = null)

    @get:OutputFile
    internal val resultsFile = project.layout.buildDirectory.file("reports/ktlintValidation/result.txt")

    @TaskAction
    public fun run() {
        val files = editorConfigFiles.files
        val messageFn = {
            "None of the recognised `.editorconfig` files contain `root=true` entry, this may result in non-deterministic builds. " +
                "Please add `root=true` entry to the top-most editorconfig file\n" +
                "`.editorconfig` files:\n${files.joinToString(separator = "\n")}"
        }
        if (files.none { it.isRootEditorConfig }) {
            resultsFile.get().asFile.writeText("Failure")
            when (mode.get()) {
                None -> Unit
                PrintWarningLogs -> logger.warn(messageFn())

                BuildFailure,
                null,
                -> throw GradleException(messageFn())
            }
        } else {
            resultsFile.get().asFile.writeText("OK")
        }
    }
}
