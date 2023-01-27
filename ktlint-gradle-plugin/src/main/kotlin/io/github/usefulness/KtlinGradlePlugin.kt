package io.github.usefulness

import io.github.usefulness.support.ReporterType
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.TaskProvider
import io.github.usefulness.pluginapplier.AndroidSourceSetApplier
import io.github.usefulness.pluginapplier.KotlinSourceSetApplier
import io.github.usefulness.tasks.ConfigurableKtLintTask
import io.github.usefulness.tasks.FormatTask
import io.github.usefulness.tasks.LintTask
import java.io.File

class KtlinGradlePlugin : Plugin<Project> {

    companion object {
        private const val KTLINT_CONFIGURATION_NAME = "ktlint"
        private const val RULE_SET_CONFIGURATION_NAME = "ktlintRuleSet"
    }

    private val extendablePlugins = mapOf(
        "org.jetbrains.kotlin.jvm" to KotlinSourceSetApplier,
        "org.jetbrains.kotlin.multiplatform" to KotlinSourceSetApplier,
        "org.jetbrains.kotlin.js" to KotlinSourceSetApplier,
        "kotlin-android" to AndroidSourceSetApplier,
    )

    override fun apply(project: Project) = with(project) {
        val pluginExtension = extensions.create("ktlint", KtlintGradleExtension::class.java)

        extendablePlugins.forEach { (pluginId, sourceResolver) ->
            pluginManager.withPlugin(pluginId) {
                val lintKotlin = registerParentLintTask()
                val formatKotlin = registerParentFormatTask()

                val ktlintConfiguration = createKtlintConfiguration(pluginExtension)
                val ruleSetConfiguration = createRuleSetConfiguration(ktlintConfiguration)

                tasks.withType(ConfigurableKtLintTask::class.java).configureEach { task ->
                    task.ktlintClasspath.setFrom(ktlintConfiguration)
                    task.ruleSetsClasspath.setFrom(ruleSetConfiguration)
                }

                sourceResolver.applyToAll(this) { id, resolvedSources ->
                    val lintTaskPerSourceSet = tasks.register("lintKotlin${id.capitalize()}", LintTask::class.java) { lintTask ->
                        lintTask.source(resolvedSources)
                        lintTask.ignoreFailures.set(provider { pluginExtension.ignoreFailures })
                        lintTask.reports.set(
                            provider {
                                pluginExtension.reporters.associateWith { reporterId ->
                                    val type = ReporterType.getById(reporterId)
                                    reportFile("$id-lint.${type.fileExtension}")
                                }
                            },
                        )
                        lintTask.experimentalRules.set(provider { pluginExtension.experimentalRules })
                        lintTask.disabledRules.set(provider { pluginExtension.disabledRules.toList() })
                    }
                    lintKotlin.configure { lintTask ->
                        lintTask.dependsOn(lintTaskPerSourceSet)
                    }

                    val formatKotlinPerSourceSet = tasks.register("formatKotlin${id.capitalize()}", FormatTask::class.java) { formatTask ->
                        formatTask.source(resolvedSources)
                        formatTask.report.set(reportFile("$id-format.txt"))
                        formatTask.experimentalRules.set(provider { pluginExtension.experimentalRules })
                        formatTask.disabledRules.set(provider { pluginExtension.disabledRules.toList() })
                    }
                    formatKotlin.configure { formatTask ->
                        formatTask.dependsOn(formatKotlinPerSourceSet)
                    }
                }
            }
        }
    }

    private fun Project.registerParentLintTask(): TaskProvider<Task> =
        tasks.register("lintKotlin") {
            it.group = "formatting"
            it.description = "Runs lint on the Kotlin source files."
        }.also { lintKotlin ->
            tasks.named("check").configure { check -> check.dependsOn(lintKotlin) }
        }

    private fun Project.registerParentFormatTask(): TaskProvider<Task> =
        tasks.register("formatKotlin") {
            it.group = "formatting"
            it.description = "Formats the Kotlin source files."
        }

    private fun Project.createKtlintConfiguration(pluginExtension: KtlintGradleExtension): Configuration =
        configurations.maybeCreate(KTLINT_CONFIGURATION_NAME).apply {
            isCanBeResolved = true
            isCanBeConsumed = false
            isVisible = false

            val dependencyProvider = provider {
                val ktlintVersion = pluginExtension.ktlintVersion
                this@createKtlintConfiguration.dependencies.create("com.pinterest:ktlint:$ktlintVersion")
            }

            dependencies.addLater(dependencyProvider)
        }

    @Suppress("UnstableApiUsage")
    private fun Project.createRuleSetConfiguration(
        ktlintConfiguration: Configuration,
    ): Configuration = configurations.maybeCreate(RULE_SET_CONFIGURATION_NAME).apply {
        isCanBeResolved = true
        isCanBeConsumed = false
        isVisible = false

        shouldResolveConsistentlyWith(ktlintConfiguration)
    }
}

internal val String.id: String
    get() = split(" ").first()

internal fun Project.reportFile(name: String): File = file("$buildDir/reports/ktlint/$name")
