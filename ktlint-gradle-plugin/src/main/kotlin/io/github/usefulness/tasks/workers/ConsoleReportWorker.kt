package io.github.usefulness.tasks.workers

import com.pinterest.ktlint.cli.reporter.baseline.doesNotContain
import com.pinterest.ktlint.cli.reporter.core.api.KtlintCliError
import com.pinterest.ktlint.cli.reporter.core.api.KtlintCliError.Status
import io.github.usefulness.support.KtlintRunMode
import io.github.usefulness.support.getBaselineKey
import io.github.usefulness.support.readKtlintBaseline
import io.github.usefulness.support.readKtlintErrors
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Property
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import java.io.File

internal abstract class ConsoleReportWorker : WorkAction<ConsoleReportWorker.Parameters> {

    private val logger = Logging.getLogger(ConsoleReportWorker::class.java)
    private val mode get() = parameters.mode.get().let(::checkNotNull)

    override fun execute() {
        val projectDir = parameters.projectDirectory.get().asFile

        val discoveredErrors = parameters.errorsContainer.readKtlintErrors()
        val baselineContent = parameters.baselineFile.orNull?.asFile?.readKtlintBaseline().orEmpty()

        var hasUncoveredErrors = false
        discoveredErrors.forEach { (file, errors) ->
            val baselineErrors = baselineContent[file.getBaselineKey(projectDir)].orEmpty()
            errors.forEach { lintError ->
                when (mode) {
                    KtlintRunMode.Check ->
                        if (baselineErrors.doesNotContain(lintError)) {
                            logger.warn(lintError.generateMessage(file = file, message = "Lint error"))
                            hasUncoveredErrors = true
                        }

                    KtlintRunMode.Format -> {
                        when (lintError.status) {
                            Status.BASELINE_IGNORED -> Unit

                            Status.LINT_CAN_NOT_BE_AUTOCORRECTED -> {
                                hasUncoveredErrors = true
                                logger.warn(lintError.generateMessage(file = file, message = "Format could not fix"))
                            }

                            Status.FORMAT_IS_AUTOCORRECTED -> logger.quiet(lintError.generateMessage(file = file, message = "Format fixed"))

                            Status.LINT_CAN_BE_AUTOCORRECTED,
                            Status.KOTLIN_PARSE_EXCEPTION,
                            Status.KTLINT_RULE_ENGINE_EXCEPTION,
                            -> logger.warn(
                                lintError.generateMessage(file = file, message = "Internal exception status=${lintError.status}"),
                            )
                        }.let { }
                    }
                }
            }
        }

        if (!parameters.ignoreFailures.get() && hasUncoveredErrors) {
            val message = when (mode) {
                KtlintRunMode.Check -> "ktlint check failed"
                KtlintRunMode.Format -> "Format failed to autocorrect"
            }
            throw GradleException(message)
        }
    }

    private fun KtlintCliError.generateMessage(file: File, message: String) = "${file.path}:$line:$col: $message > [$ruleId] $detail"

    interface Parameters : WorkParameters {
        val errorsContainer: DirectoryProperty
        val ignoreFailures: Property<Boolean>
        val projectDirectory: RegularFileProperty
        val mode: Property<KtlintRunMode>
        val baselineFile: RegularFileProperty
    }
}
