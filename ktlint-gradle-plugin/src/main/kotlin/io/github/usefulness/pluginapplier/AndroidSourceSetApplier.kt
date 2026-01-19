package io.github.usefulness.pluginapplier

import com.android.build.api.dsl.AndroidSourceDirectorySet
import com.android.build.api.dsl.AndroidSourceSet
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.variant.AndroidComponentsExtension
import io.github.usefulness.KtlintGradleExtension
import io.github.usefulness.SourceSetAction
import io.github.usefulness.SourceSetApplier
import io.github.usefulness.id
import org.gradle.api.Project
import org.gradle.api.file.FileTree

internal object AndroidSourceSetApplier : SourceSetApplier {

    override fun applyToAll(project: Project, extension: KtlintGradleExtension, action: SourceSetAction) {
        val android = project.extensions.findByName("androidComponents") as? AndroidComponentsExtension<*, *, *> ?: return
        android.finalizeDsl { commonExtension ->
            commonExtension as CommonExtension
            commonExtension.sourceSets.configureEach { sourceSet ->
                val id = sourceSet.name.id
                action(id, getKotlinFiles(project, sourceSet))
            }
        }
    }

    private fun getKotlinFiles(project: Project, sourceSet: AndroidSourceSet): FileTree {
        val javaSources = sourceSet.java.srcDirs
        val kotlinSources = sourceSet.kotlin.srcDirs

        val emptyFileTree = project.files().asFileTree

        return (javaSources + kotlinSources)
            .map { dir -> project.fileTree(dir) { it.include("**/*.kt") } }
            .fold(emptyFileTree) { merged, tree -> merged + tree }
    }
}

@Suppress("DEPRECATION") // https://issuetracker.google.com/issues/170650362
internal val AndroidSourceDirectorySet.srcDirs
    get() = (this as com.android.build.gradle.api.AndroidSourceDirectorySet).srcDirs
