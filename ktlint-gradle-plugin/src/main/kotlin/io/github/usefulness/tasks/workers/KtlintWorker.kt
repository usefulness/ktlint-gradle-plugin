package io.github.usefulness.tasks.workers

import com.pinterest.ktlint.cli.reporter.core.api.KtlintCliError
import com.pinterest.ktlint.cli.reporter.core.api.KtlintCliError.Status
import com.pinterest.ktlint.rule.engine.api.Code
import com.pinterest.ktlint.rule.engine.api.LintError
import io.github.usefulness.support.KtlintErrorResult
import io.github.usefulness.support.KtlintRunMode
import io.github.usefulness.support.createKtlintEngine
import io.github.usefulness.support.resetEditorconfigCacheIfNeeded
import io.github.usefulness.support.writeTo
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logging
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.internal.logging.slf4j.DefaultContextAwareTaskLogger
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.jetbrains.kotlin.utils.addToStdlib.applyIf

internal abstract class KtlintWorker : WorkAction<KtlintWorker.Parameters> {
    private val logger = DefaultContextAwareTaskLogger(Logging.getLogger(KtlintWorker::class.java))

    private val name = parameters.name.get()

    override fun execute() {
        val projectDir = parameters.projectDirectory.asFile.get()
        val files = parameters.files

        val ktLintEngine = createKtlintEngine(
            disabledRules = parameters.disabledRules.get(),
            experimentalRules = parameters.experimentalRules.get(),
        )
        ktLintEngine.resetEditorconfigCacheIfNeeded(
            changedEditorconfigFiles = parameters.changedEditorConfigFiles,
            logger = logger,
        )
        logger.info("$name - resolved ${ktLintEngine.ruleProviders.size} RuleProviders")
        logger.info("$name - executing against ${files.count()} file(s)")
        if (logger.isDebugEnabled) {
            logger.debug(
                "Resolved RuleSetProviders = ${ktLintEngine.ruleProviders.joinToString { it.createNewRuleInstance().ruleId.value }}",
            )
        }

        val errors = mutableListOf<KtlintErrorResult>()

        files.forEach { file ->
            val relativePath = file.toRelativeString(projectDir)
            logger.debug("$name linting: $relativePath")

            if (file.extension !in supportedExtensions) {
                logger.info("$name ignoring non-Kotlin file: $relativePath")
                return@forEach
            }

            val fileErrors = mutableListOf<KtlintCliError>()
            when (parameters.mode.get()) {
                KtlintRunMode.Check,
                null,
                -> ktLintEngine.lint(
                    code = Code.fromFile(file),
                    callback = { fileErrors.add(it.toKtlintCliErrorForLint()) },
                )

                KtlintRunMode.Format -> {
                    var fileFixed = false
                    val fixedContent = ktLintEngine.format(
                        code = Code.fromFile(file),
                        callback = { error, corrected ->
                            if (corrected) {
                                fileFixed = true
                            }
                            fileErrors.add(error.toKtlintCliErrorForFormat(corrected))
                        },
                    )

                    if (fileFixed) {
                        file.writeText(fixedContent)
                    }
                }
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

private fun LintError.toKtlintCliErrorForLint() = KtlintCliError(
    line = line,
    col = col,
    ruleId = ruleId.value,
    detail = detail,
    status = if (canBeAutoCorrected) {
        Status.LINT_CAN_BE_AUTOCORRECTED
    } else {
        Status.LINT_CAN_NOT_BE_AUTOCORRECTED
    },
)

private fun LintError.toKtlintCliErrorForFormat(corrected: Boolean): KtlintCliError = KtlintCliError(
    line = line,
    col = col,
    ruleId = ruleId.value,
    detail = detail.applyIf(!corrected) { "$this (cannot be auto-corrected)" },
    status = if (corrected) {
        Status.FORMAT_IS_AUTOCORRECTED
    } else {
        Status.LINT_CAN_NOT_BE_AUTOCORRECTED
    },
)

private val supportedExtensions = setOf("kt", "kts")
