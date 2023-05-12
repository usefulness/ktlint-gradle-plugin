package io.github.usefulness.tasks.workers

import com.pinterest.ktlint.cli.reporter.baseline.doesNotContain
import io.github.usefulness.support.ReporterType
import io.github.usefulness.support.getBaselineKey
import io.github.usefulness.support.readKtlintBaseline
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

    override fun execute() {
        val projectDir = parameters.projectDirectory.get().asFile

        val discoveredErrors = parameters.errorsContainer.readKtlintErrors()
        val baselineContent = parameters.baselineFile.orNull?.asFile?.readKtlintBaseline().orEmpty()

        val reporters = resolveReporters(enabled = getReports())
        logger.info("resolved ${reporters.size} Reporters")

        reporters.onEach { (_, reporter) -> reporter.beforeAll() }
        discoveredErrors.forEach { result ->
            val relativePath = result.file.toRelativeString(projectDir)
            val baselineErrors = baselineContent[result.file.getBaselineKey(projectDir)].orEmpty()

            reporters.onEach { (_, reporter) -> reporter.before(relativePath) }

            reporters.onEach { (type, reporter) ->
                result.errors.forEach { lintError ->
                    if (baselineErrors.doesNotContain(lintError)) {
                        // some reporters want relative paths, some want absolute
                        val filePath = reporterPathFor(
                            reporterType = type,
                            output = result.file,
                            relativeRoot = projectDir,
                        )
                        reporter.onLintError(filePath, lintError)
                    }
                }
            }

            reporters.onEach { (_, reporter) -> reporter.after(relativePath) }
        }
        reporters.onEach { (_, reporter) -> reporter.afterAll() }
    }

    private fun getReports() = parameters.reporters.get()
        .mapKeys { (id, _) -> ReporterType.getById(id = id) }

    internal interface Parameters : WorkParameters {
        val errorsContainer: DirectoryProperty
        val projectDirectory: RegularFileProperty
        val reporters: MapProperty<String, File>
        val baselineFile: RegularFileProperty
    }
}
