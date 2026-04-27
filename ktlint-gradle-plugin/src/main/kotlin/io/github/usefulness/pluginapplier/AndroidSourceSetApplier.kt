package io.github.usefulness.pluginapplier

import com.android.build.api.AndroidPluginVersion
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
import java.io.File

internal object AndroidSourceSetApplier : SourceSetApplier {

    override fun applyToAll(project: Project, extension: KtlintGradleExtension, action: SourceSetAction) {
        val android = project.extensions.findByName("androidComponents") as? AndroidComponentsExtension<*, *, *> ?: return
        android.finalizeDsl { commonExtension ->
            commonExtension as CommonExtension
            commonExtension.sourceSets.configureEach { sourceSet ->
                val id = sourceSet.name.id
                action(id, getKotlinFiles(project, sourceSet, android.pluginVersion))
            }
        }
    }

    private fun getKotlinFiles(project: Project, sourceSet: AndroidSourceSet, appliedAgpVersion: AndroidPluginVersion): FileTree {
        val fixedAgpVersion = AndroidPluginVersion(9, 0)

        val javaSources = if (appliedAgpVersion >= fixedAgpVersion) sourceSet.java.directories else sourceSet.java.srcDirsInterop
        val kotlinSources = if (appliedAgpVersion >= fixedAgpVersion) sourceSet.kotlin.directories else sourceSet.kotlin.srcDirsInterop

        val emptyFileTree = project.files().asFileTree

        return (javaSources + kotlinSources)
            .map { dir -> project.fileTree(dir) { it.include("**/*.kt") } }
            .fold(emptyFileTree) { merged, tree -> merged + tree }
    }
}

@Suppress("UNCHECKED_CAST")
private val AndroidSourceDirectorySet.srcDirsInterop
    get() = (javaClass.getMethod("getSrcDirs").invoke(this) as Set<File>)
        .map { it.path }
