package io.github.usefulness.tasks.format

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.workers.WorkParameters
import io.github.usefulness.support.KtLintParams

interface FormatWorkerParameters : WorkParameters {
    val name: Property<String>
    val changedEditorConfigFiles: ConfigurableFileCollection
    val files: ConfigurableFileCollection
    val projectDirectory: RegularFileProperty
    val ktLintParams: Property<KtLintParams>
    val output: RegularFileProperty
}