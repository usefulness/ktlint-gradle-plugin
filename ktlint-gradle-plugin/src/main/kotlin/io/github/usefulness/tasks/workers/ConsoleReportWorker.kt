package io.github.usefulness.tasks.workers

import com.pinterest.ktlint.core.LintError
import io.github.usefulness.support.KtlintRunMode
import io.github.usefulness.support.doesNotContain
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

        val errorsFound = discoveredErrors.any()
        if (errorsFound) {
            discoveredErrors.forEach { (file, errors) ->
                val baselineErrors = baselineContent[file.getBaselineKey(projectDir)].orEmpty()
                errors.forEach { (lintError, corrected) ->
                    if (baselineErrors.doesNotContain(lintError)) {
                        printError(
                            file = file,
                            lintError = lintError,
                            corrected = corrected,
                        )
                    }
                }
            }
        }

        if (!parameters.ignoreFailures.get() && errorsFound) {
            val message = when (mode) {
                KtlintRunMode.Check -> "ktlint check failed"
                KtlintRunMode.Format -> "Format failed to autocorrect"
            }
            throw GradleException(message)
        }
    }

    private fun printError(file: File, lintError: LintError, corrected: Boolean) = when (mode) {
        KtlintRunMode.Check -> logger.warn(lintError.generateMessage(file, message = "Lint error"))

        KtlintRunMode.Format -> when (corrected) {
            true -> logger.quiet(lintError.generateMessage(file, message = "Format fixed"))
            false -> logger.warn(lintError.generateMessage(file, message = "Format could not fix"))
        }
    }

    private fun LintError.generateMessage(file: File, message: String) = "${file.path}:$line:$col: $message > [$ruleId] $detail"

    interface Parameters : WorkParameters {
        val errorsContainer: DirectoryProperty
        val ignoreFailures: Property<Boolean>
        val projectDirectory: RegularFileProperty
        val mode: Property<KtlintRunMode>
        val baselineFile: RegularFileProperty
    }
}
