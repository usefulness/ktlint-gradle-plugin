package io.github.usefulness.tasks.workers

import io.github.usefulness.support.readKtlintErrors
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Property
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters

internal abstract class ConsoleReportWorker : WorkAction<ConsoleReportWorker.Parameters> {

    interface Parameters : WorkParameters {
        val errorsContainer: DirectoryProperty
        val ignoreFailures: Property<Boolean>
        val projectDirectory: RegularFileProperty
    }

    private val logger = Logging.getLogger(ConsoleReportWorker::class.java)

    override fun execute() {
        val discoveredErrors = parameters.errorsContainer.readKtlintErrors()

        val errorsFound = discoveredErrors.any()
        if (errorsFound) {
            discoveredErrors.forEach { (file, errors) ->
                errors.forEach { error ->
                    logger.quiet("${file.path}:${error.line}:${error.col}: Lint error > [${error.ruleId}] ${error.detail}")
                }
            }
        }

        if (!parameters.ignoreFailures.get() && errorsFound) {
            throw GradleException("ktlint check failed")
        }
    }
}
