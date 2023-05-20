package io.github.usefulness.pluginapplier

import io.github.usefulness.SourceSetAction
import io.github.usefulness.SourceSetApplier
import io.github.usefulness.id
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension

internal object KotlinSourceSetApplier : SourceSetApplier {
    override fun applyToAll(project: Project, action: SourceSetAction) {
        getSourceSets(project).configureEach { sourceSet ->
            sourceSet.kotlin.let { directorySet ->
                action(directorySet.name.id, project.provider { directorySet })
            }
        }
    }

    private fun getSourceSets(project: Project) = project.extensions.getByType(KotlinProjectExtension::class.java).sourceSets
}
