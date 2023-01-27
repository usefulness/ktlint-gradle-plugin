package io.github.usefulness.tasks.lint

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.workers.WorkParameters
import io.github.usefulness.support.KtLintParams
import io.github.usefulness.support.ReporterType
import java.io.File

internal interface LintWorkerParameters : WorkParameters {
    val name: Property<String>
    val changedEditorConfigFiles: ConfigurableFileCollection
    val files: ConfigurableFileCollection
    val projectDirectory: RegularFileProperty
    val reporters: MapProperty<ReporterType, File>
    val ktLintParams: Property<KtLintParams>
}
