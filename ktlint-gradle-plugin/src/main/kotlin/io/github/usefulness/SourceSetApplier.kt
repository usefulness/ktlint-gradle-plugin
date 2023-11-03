package io.github.usefulness

import org.gradle.api.Project

internal typealias SourceSetAction = (String, Any) -> Unit

internal interface SourceSetApplier {
    fun applyToAll(project: Project, extension: KtlintGradleExtension, action: SourceSetAction)
}
