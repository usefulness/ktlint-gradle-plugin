package io.github.usefulness.pluginapplier

import io.github.usefulness.KtlintGradleExtension
import io.github.usefulness.SourceSetAction
import io.github.usefulness.SourceSetApplier
import io.github.usefulness.id
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension

internal object KotlinSourceSetApplier : SourceSetApplier {

    override fun applyToAll(project: Project, extension: KtlintGradleExtension, action: SourceSetAction) {
        project.extensions.getByType(KotlinProjectExtension::class.java).sourceSets.configureEach { sourceSet ->
            if (!sourceSet.name.startsWith("generatedByKsp") || !extension.ignoreKspGeneratedSources.get()) {
                action(sourceSet.kotlin.name.id, project.provider { sourceSet.kotlin })
            }
        }
    }
}
