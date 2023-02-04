package io.github.usefulness.tasks.workers

import io.github.usefulness.support.ReporterType
import io.github.usefulness.support.readKtlintErrors
import io.github.usefulness.support.reporterPathFor
import io.github.usefulness.support.resolveReporters
import io.github.usefulness.tasks.LintTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logging
import org.gradle.api.provider.MapProperty
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import java.io.File

internal abstract class GenerateReportsWorker : WorkAction<GenerateReportsWorker.Parameters> {

    private val logger = Logging.getLogger(LintTask::class.java)

    internal interface Parameters : WorkParameters {
        val errorsContainer: DirectoryProperty
        val projectDirectory: RegularFileProperty
        val reporters: MapProperty<String, File>
    }

    override fun execute() {
        val projectDir = parameters.projectDirectory.get().asFile
        val reporters = resolveReporters(enabled = getReports())
        val discoveredErrors = parameters.errorsContainer.readKtlintErrors()
        logger.info("resolved ${reporters.size} Reporters")

        reporters.onEach { (_, reporter) -> reporter.beforeAll() }
        discoveredErrors.forEach { result ->
            val relativePath = result.file.toRelativeString(projectDir)
            reporters.onEach { (_, reporter) -> reporter.before(relativePath) }

            reporters.onEach { (type, reporter) ->
                result.errors.forEach { (lintError, corrected) ->
                    // some reporters want relative paths, some want absolute
                    val filePath = reporterPathFor(
                        reporterType = type,
                        output = result.file,
                        relativeRoot = projectDir,
                    )
                    reporter.onLintError(filePath, lintError, corrected)
                }
            }

            reporters.onEach { (_, reporter) -> reporter.after(relativePath) }
        }
        reporters.onEach { (_, reporter) -> reporter.afterAll() }
    }

    private fun getReports() = parameters.reporters.get()
        .mapKeys { (id, _) -> ReporterType.getById(id = id) }
}
