package io.github.usefulness

import org.gradle.api.Project
import org.gradle.api.file.FileTree
import org.gradle.api.provider.Provider

internal typealias SourceSetAction = (String, Provider<FileTree>) -> Unit

internal interface SourceSetApplier {
    fun applyToAll(project: Project, action: SourceSetAction)
}
