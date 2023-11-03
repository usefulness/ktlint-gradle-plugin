package io.github.usefulness.pluginapplier

import io.github.usefulness.KtlintGradleExtension
import io.github.usefulness.SourceSetAction
import io.github.usefulness.SourceSetApplier
import io.github.usefulness.id
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetContainer
import java.io.File

internal object KotlinSourceSetApplier : SourceSetApplier {

    override fun applyToAll(project: Project, extension: KtlintGradleExtension, action: SourceSetAction) {
        project.extensions.getByType(KotlinSourceSetContainer::class.java).sourceSets.configureEach { sourceSet ->
            val directories = project.provider {
                if (extension.ignoreFilesUnderBuildDir.get()) {
                    sourceSet.kotlin.srcDirTrees.mapNotNull { dirTree ->
                        dirTree.dir.takeUnless { it.isUnder(project.layout.buildDirectory.asFile.get()) }
                    }
                } else {
                    sourceSet.kotlin.sourceDirectories
                }
            }

            action(sourceSet.kotlin.name.id, directories)
        }
    }

    private fun File.isUnder(rootDirectory: File) = canonicalPath.startsWith(rootDirectory.canonicalPath)
}
