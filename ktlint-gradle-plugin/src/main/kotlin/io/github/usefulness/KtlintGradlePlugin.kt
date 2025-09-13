package io.github.usefulness

import io.github.usefulness.EditorConfigValidationMode.None
import io.github.usefulness.pluginapplier.AndroidSourceSetApplier
import io.github.usefulness.pluginapplier.KotlinSourceSetApplier
import io.github.usefulness.support.ReporterType
import io.github.usefulness.tasks.CheckEditorConfigTask
import io.github.usefulness.tasks.FormatTask
import io.github.usefulness.tasks.KtlintWorkTask
import io.github.usefulness.tasks.LintTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
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
        "com.android.library" to AndroidSourceSetApplier,
        "com.android.application" to AndroidSourceSetApplier,
        "com.android.dynamic-feature" to AndroidSourceSetApplier,
        "com.android.kotlin.multiplatform.library" to AndroidSourceSetApplier,
    )

    public override fun apply(project: Project): Unit = with(project) {
        val pluginExtension = extensions.create("ktlint", KtlintGradleExtension::class.java)

        val ktlintConfiguration = createKtlintConfiguration(pluginExtension)
        val ruleSetConfiguration = createRuleSetConfiguration(ktlintConfiguration)
        val reportersConfiguration = createReportersConfiguration(ktlintConfiguration)
        val recognisedEditorConfigs = generateSequence(projectDir) { if (it == rootProject.projectDir) null else it.parentFile }
            .map { it.resolve(".editorconfig") }
            .toList()

        tasks.register("validateEditorConfigForKtlint", CheckEditorConfigTask::class.java) {
            it.editorConfigFiles.from(recognisedEditorConfigs)
            it.mode.set(pluginExtension.editorConfigValidation)
        }

        extendablePlugins.forEach { (pluginId, sourceResolver) ->
            pluginManager.withPlugin(pluginId) {
                val lintKotlin = registerParentLintTask()
                val formatKotlin = registerParentFormatTask()

                tasks.withType(KtlintWorkTask::class.java).configureEach { task ->
                    task.ktlintClasspath.setFrom(ktlintConfiguration)
                    task.ruleSetsClasspath.setFrom(ruleSetConfiguration)
                    task.reportersConfiguration.setFrom(reportersConfiguration)
                    task.chunkSize.set(pluginExtension.chunkSize)
                    task.editorConfigFiles.from(recognisedEditorConfigs)
                }

                sourceResolver.applyToAll(this, pluginExtension) { id, resolvedSources ->
                    val checkWorker = tasks.register(
                        "lintKotlin${id.replaceFirstChar(Char::titlecase)}",
                        LintTask::class.java,
                    ) { task ->
                        task.source(resolvedSources)
                        task.experimentalRules.set(pluginExtension.experimentalRules)
                        task.disabledRules.set(pluginExtension.disabledRules)
                        task.ignoreFailures.set(pluginExtension.ignoreFailures)
                        task.baselineFile.set(pluginExtension.baselineFile)
                        task.reports.set(
                            pluginExtension.reporters.map {
                                it.associateWith { reporterId ->
                                    val type = ReporterType.getById(reporterId)
                                    reportFile("$id-lint.${type.fileExtension}")
                                }
                            },
                        )
                    }
                    lintKotlin.configure { it.dependsOn(checkWorker) }

                    val formatWorker = tasks.register(
                        "formatKotlin${id.replaceFirstChar(Char::titlecase)}",
                        FormatTask::class.java,
                    ) { task ->
                        task.source(resolvedSources)
                        task.experimentalRules.set(pluginExtension.experimentalRules)
                        task.disabledRules.set(pluginExtension.disabledRules)
                        task.ignoreFailures.set(true)
                        task.baselineFile.set(pluginExtension.baselineFile)
                        task.reports.set(
                            pluginExtension.reporters.map {
                                it.associateWith { reporterId ->
                                    val type = ReporterType.getById(reporterId)
                                    reportFile("$id-format.${type.fileExtension}")
                                }
                            },
                        )
                    }

                    formatKotlin.configure { it.dependsOn(formatWorker) }
                }

                tasks.named("lintKotlin") { task ->
                    if (pluginExtension.editorConfigValidation.get() == None) return@named
                    task.dependsOn("validateEditorConfigForKtlint")
                }
                tasks.named("formatKotlin") { task ->
                    if (pluginExtension.editorConfigValidation.get() == None) return@named
                    task.dependsOn("validateEditorConfigForKtlint")
                }
            }
        }
    }

    private fun Project.registerParentLintTask() = tasks.register("lintKotlin") {
        it.group = "formatting"
        it.description = "Runs lint on the Kotlin source files."
    }.also { lintKotlin ->
        tasks.named("check").configure { check -> check.dependsOn(lintKotlin) }
    }

    private fun Project.registerParentFormatTask() = tasks.register("formatKotlin") {
        it.group = "formatting"
        it.description = "Formats the Kotlin source files."
    }

    private fun Project.createKtlintConfiguration(pluginExtension: KtlintGradleExtension): Configuration =
        configurations.maybeCreate(KTLINT_CONFIGURATION_NAME).apply {
            isCanBeResolved = true
            isCanBeConsumed = false

            val dependencyProvider = provider {
                val ktlintVersion = pluginExtension.ktlintVersion.get()
                val ktlintDependency = if (ktlintVersion.startsWith("0.")) {
                    "com.pinterest:ktlint:$ktlintVersion"
                } else {
                    "com.pinterest.ktlint:ktlint-cli:$ktlintVersion"
                }
                this@createKtlintConfiguration.dependencies.create(ktlintDependency)
            }

            dependencies.addLater(dependencyProvider)
        }

    @Suppress("UnstableApiUsage")
    private fun Project.createRuleSetConfiguration(ktlintConfiguration: Configuration): Configuration =
        configurations.maybeCreate(RULE_SET_CONFIGURATION_NAME).apply {
            isCanBeResolved = true
            isCanBeConsumed = false

            shouldResolveConsistentlyWith(ktlintConfiguration)
        }

    @Suppress("UnstableApiUsage")
    private fun Project.createReportersConfiguration(ktlintConfiguration: Configuration): Configuration =
        configurations.maybeCreate(REPORTERS_CONFIGURATION_NAME).apply {
            isCanBeResolved = true
            isCanBeConsumed = false

            shouldResolveConsistentlyWith(ktlintConfiguration)
        }
}

internal val String.id: String
    get() = split(" ").first()

internal fun Project.reportFile(name: String): File = file("${layout.buildDirectory.get()}/reports/ktlint/$name")
