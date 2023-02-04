package io.github.usefulness

import io.github.usefulness.support.ReporterType
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import io.github.usefulness.pluginapplier.AndroidSourceSetApplier
import io.github.usefulness.pluginapplier.KotlinSourceSetApplier
import io.github.usefulness.support.KtlintRunMode
import io.github.usefulness.tasks.KtlintWorkTask
import io.github.usefulness.tasks.FormatTask
import io.github.usefulness.tasks.GenerateReportsTask
import io.github.usefulness.tasks.LintTask
import java.io.File

public class KtlintGradlePlugin : Plugin<Project> {

    internal companion object {
        private const val KTLINT_CONFIGURATION_NAME = "ktlint"
        private const val RULE_SET_CONFIGURATION_NAME = "ktlintRuleSet"
        private const val REPORTERS_CONFIGURATION_NAME = "ktlintReporters"
    }

    private val extendablePlugins = mapOf(
        "org.jetbrains.kotlin.jvm" to KotlinSourceSetApplier,
        "org.jetbrains.kotlin.multiplatform" to KotlinSourceSetApplier,
        "org.jetbrains.kotlin.js" to KotlinSourceSetApplier,
        "kotlin-android" to AndroidSourceSetApplier,
    )

    public override fun apply(project: Project): Unit = with(project) {
        val pluginExtension = extensions.create("ktlint", KtlintGradleExtension::class.java)

        extendablePlugins.forEach { (pluginId, sourceResolver) ->
            pluginManager.withPlugin(pluginId) {
                val lintKotlin = registerParentLintTask()
                val formatKotlin = registerParentFormatTask()

                val ktlintConfiguration = createKtlintConfiguration(pluginExtension)
                val ruleSetConfiguration = createRuleSetConfiguration(ktlintConfiguration)
                val reportersConfiguration = createReportersConfiguration(ktlintConfiguration)

                tasks.withType(KtlintWorkTask::class.java).configureEach { task ->
                    task.ktlintClasspath.setFrom(ktlintConfiguration)
                    task.ruleSetsClasspath.setFrom(ruleSetConfiguration)
                    task.chunkSize.set(provider { pluginExtension.chunkSize })
                }
                tasks.withType(GenerateReportsTask::class.java).configureEach { task ->
                    task.ktlintClasspath.setFrom(ktlintConfiguration)
                    task.reportersConfiguration.setFrom(reportersConfiguration)
                }

                sourceResolver.applyToAll(this) { id, resolvedSources ->
                    val checkWorker = tasks.register(
                        "lintKotlin${id.capitalize()}Worker",
                        LintTask::class.java,
                    ) { task ->
                        task.source(resolvedSources)

                        task.experimentalRules.set(provider { pluginExtension.experimentalRules })
                        task.disabledRules.set(provider { pluginExtension.disabledRules.toList() })
                    }
                    val checkReporter = tasks.register(
                        "lintKotlin${id.capitalize()}Reporter",
                        GenerateReportsTask::class.java,
                    ) { task ->
                        task.errorsContainer.set(checkWorker.get().discoveredErrors)
                        task.mode.set(KtlintRunMode.Check)
                        task.ignoreFailures.set(provider { pluginExtension.ignoreFailures })
                        task.reports.set(
                            provider {
                                pluginExtension.reporters.associateWith { reporterId ->
                                    val type = ReporterType.getById(reporterId)
                                    reportFile("$id-lint.${type.fileExtension}")
                                }
                            },
                        )
                    }
                    lintKotlin.configure {
                        it.dependsOn(checkWorker)
                        it.dependsOn(checkReporter)
                    }

                    val formatWorker = tasks.register(
                        "formatKotlin${id.capitalize()}Worker",
                        FormatTask::class.java,
                    ) { task ->
                        task.source(resolvedSources)
                        task.experimentalRules.set(provider { pluginExtension.experimentalRules })
                        task.disabledRules.set(provider { pluginExtension.disabledRules.toList() })
                    }
                    val formatReporter = tasks.register(
                        "formatKotlin${id.capitalize()}Reporter",
                        GenerateReportsTask::class.java,
                    ) { task ->
                        task.errorsContainer.set(formatWorker.get().discoveredErrors)
                        task.mode.set(KtlintRunMode.Format)
                        task.ignoreFailures.set(provider { true })
                        task.reports.set(
                            provider {
                                pluginExtension.reporters.associateWith { reporterId ->
                                    val type = ReporterType.getById(reporterId)
                                    reportFile("$id-lint.${type.fileExtension}")
                                }
                            },
                        )
                    }
                    formatKotlin.configure {
                        it.dependsOn(formatWorker)
                        it.dependsOn(formatReporter)
                    }
                }
            }
        }
    }

    private fun Project.registerParentLintTask() =
        tasks.register("lintKotlin") {
            it.group = "formatting"
            it.description = "Runs lint on the Kotlin source files."
        }.also { lintKotlin ->
            tasks.named("check").configure { check -> check.dependsOn(lintKotlin) }
        }

    private fun Project.registerParentFormatTask() =
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

    @Suppress("UnstableApiUsage")
    private fun Project.createReportersConfiguration(
        ktlintConfiguration: Configuration,
    ): Configuration = configurations.maybeCreate(REPORTERS_CONFIGURATION_NAME).apply {
        isCanBeResolved = true
        isCanBeConsumed = false
        isVisible = false

        shouldResolveConsistentlyWith(ktlintConfiguration)
    }
}

internal val String.id: String
    get() = split(" ").first()

internal fun Project.reportFile(name: String): File = file("$buildDir/reports/ktlint/$name")
