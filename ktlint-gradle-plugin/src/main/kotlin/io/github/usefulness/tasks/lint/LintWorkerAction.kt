package io.github.usefulness.tasks.lint

import com.pinterest.ktlint.core.Code
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.internal.logging.slf4j.DefaultContextAwareTaskLogger
import org.gradle.workers.WorkAction
import io.github.usefulness.support.PluginError
import io.github.usefulness.support.KtLintParams
import io.github.usefulness.support.createKtlintEngine
import io.github.usefulness.support.reporterPathFor
import io.github.usefulness.support.resetEditorconfigCacheIfNeeded
import io.github.usefulness.support.resolveReporters
import io.github.usefulness.tasks.LintTask
import java.io.File

abstract class LintWorkerAction : WorkAction<LintWorkerParameters> {
    private val logger: Logger = DefaultContextAwareTaskLogger(Logging.getLogger(LintTask::class.java))
    private val files: Iterable<File> = parameters.files
    private val projectDirectory: File = parameters.projectDirectory.asFile.get()
    private val name: String = parameters.name.get()
    private val ktLintParams: KtLintParams = parameters.ktLintParams.get()

    override fun execute() {
        val ktLintEngine = createKtlintEngine(ktLintParams = ktLintParams)
        ktLintEngine.resetEditorconfigCacheIfNeeded(
            changedEditorconfigFiles = parameters.changedEditorConfigFiles,
            logger = logger,
        )
        logger.info("$name - resolved ${ktLintEngine.ruleProviders.size} RuleProviders")
        logger.info("$name - executing against ${files.count()} file(s)")
        if (logger.isDebugEnabled) {
            logger.debug("Resolved RuleSetProviders = ${ktLintEngine.ruleProviders.joinToString { it.createNewRuleInstance().id }}")
        }

        val reporters = resolveReporters(enabled = parameters.reporters.get())

        var hasError = false

        try {
            reporters.onEach { (_, reporter) -> reporter.beforeAll() }
            files.sorted().forEach { file ->
                val relativePath = file.toRelativeString(projectDirectory)
                reporters.onEach { (_, reporter) -> reporter.before(relativePath) }
                logger.debug("$name linting: $relativePath")

                if (file.extension !in supportedExtensions) {
                    logger.debug("$name ignoring non Kotlin file: $relativePath")
                    return@forEach
                }

                ktLintEngine.lint(Code.CodeFile(file)) { error ->
                    hasError = true
                    reporters.onEach { (type, reporter) ->
                        // some reporters want relative paths, some want absolute
                        val filePath = reporterPathFor(
                            reporterType = type,
                            output = file,
                            relativeRoot = projectDirectory,
                        )
                        reporter.onLintError(filePath, error, false)
                    }
                    logger.quiet("${file.path}:${error.line}:${error.col}: Lint error > [${error.ruleId}] ${error.detail}")
                }
                reporters.onEach { (_, reporter) -> reporter.after(relativePath) }
            }
            reporters.onEach { (_, reporter) -> reporter.afterAll() }
        } catch (t: Throwable) {
            throw PluginError.WorkerError("lint worker execution error", t)
        }

        if (hasError) {
            throw PluginError.LintingError("$name source failed lint check")
        }
    }
}

private val supportedExtensions = setOf("kt", "kts")