package io.github.usefulness.tasks.workers

import io.github.usefulness.support.KtlintError
import io.github.usefulness.support.KtlintErrorResult
import io.github.usefulness.support.KtlintRunMode
import io.github.usefulness.support.api.resolveKtlintApi
import io.github.usefulness.support.writeTo
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logging
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.internal.logging.slf4j.DefaultContextAwareTaskLogger
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import java.io.File

internal abstract class KtlintWorker : WorkAction<KtlintWorker.Parameters> {
    private val logger = DefaultContextAwareTaskLogger(Logging.getLogger(KtlintWorker::class.java))

    private val name = parameters.name.get()

    override fun execute() {
        val projectDir = parameters.projectDirectory.asFile.get()
        val files = parameters.files

        val ktLintEngine = resolveKtlintApi().createEngine(
            disabledRules = parameters.disabledRules.get(),
            experimentalRules = parameters.experimentalRules.get(),
        )
        val changedEditorconfigFiles = parameters.changedEditorConfigFiles.files
        if (changedEditorconfigFiles.any()) {
            logger.info("Editorconfig changed, resetting KtLint caches")
            changedEditorconfigFiles.map(File::toPath).forEach(ktLintEngine::reloadEditorConfigFile)
        }
        if (logger.isInfoEnabled) {
            logger.info("$name - resolved ${ktLintEngine.ruleProvidersCount} RuleProviders")
            logger.info("$name - executing against ${files.count()} file(s)")
        }
        if (logger.isDebugEnabled) {
            logger.debug("Resolved RuleSetProviders = ${ktLintEngine.ruleIds().joinToString()}")
        }

        val errors = mutableListOf<KtlintErrorResult>()

        files.forEach { file ->
            val relativePath = file.toRelativeString(projectDir)
            logger.debug("$name linting: $relativePath")

            if (file.extension !in supportedExtensions) {
                logger.info("$name ignoring non-Kotlin file: $relativePath")
                return@forEach
            }

            val fileErrors = mutableListOf<KtlintError>()
            runCatching {
                when (parameters.mode.get()) {
                    KtlintRunMode.Check -> ktLintEngine.lint(file, callback = fileErrors::add)

                    KtlintRunMode.Format -> {
                        val fixedContent = ktLintEngine.format(file, callback = fileErrors::add)

                        if (fileErrors.any { it.status == KtlintError.Status.FORMAT_IS_AUTOCORRECTED }) {
                            file.writeText(fixedContent)
                        }
                    }
                }
            }
                .onFailure { throwable ->
                    logger.quiet("ktlint failed when parsing file: ${file.path}")
                    throw throwable
                }
            if (fileErrors.isNotEmpty()) {
                errors += KtlintErrorResult(
                    file = file,
                    errors = fileErrors,
                )
            }
        }

        errors.writeTo(parameters.discoveredErrors.get().asFile)
    }

    interface Parameters : WorkParameters {
        val name: Property<String>
        val changedEditorConfigFiles: ConfigurableFileCollection
        val files: ConfigurableFileCollection
        val projectDirectory: RegularFileProperty
        val experimentalRules: Property<Boolean>
        val disabledRules: ListProperty<String>
        val discoveredErrors: RegularFileProperty
        val mode: Property<KtlintRunMode>
    }
}

private val supportedExtensions = setOf("kt", "kts")
