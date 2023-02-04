package io.github.usefulness.tasks.workers

import com.pinterest.ktlint.core.LintError
import io.github.usefulness.support.KtlintRunMode
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

    interface Parameters : WorkParameters {
        val errorsContainer: DirectoryProperty
        val ignoreFailures: Property<Boolean>
        val projectDirectory: RegularFileProperty
        val mode: Property<KtlintRunMode>
    }

    private val logger = Logging.getLogger(ConsoleReportWorker::class.java)

    override fun execute() {
        val mode = parameters.mode.get().let(::checkNotNull)
        val discoveredErrors = parameters.errorsContainer.readKtlintErrors()

        val errorsFound = discoveredErrors.any()
        if (errorsFound) {
            discoveredErrors.forEach { (file, errors) ->
                errors.forEach { (error, corrected) ->
                    when (mode) {
                        KtlintRunMode.Check -> logger.warn(error.generateMessage(file, message = "Lint error"))

                        KtlintRunMode.Format -> when (corrected) {
                            true -> logger.quiet(error.generateMessage(file, message = "Format fixed"))
                            false -> logger.warn(error.generateMessage(file, message = "Format could not fix"))
                        }
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

    private fun LintError.generateMessage(file: File, message: String) = "${file.path}:$line:$col: $message > [$ruleId] $detail"
}
