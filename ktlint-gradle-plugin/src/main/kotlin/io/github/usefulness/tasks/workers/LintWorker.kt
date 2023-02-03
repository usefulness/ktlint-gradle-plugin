package io.github.usefulness.tasks.workers

import com.pinterest.ktlint.core.Code
import com.pinterest.ktlint.core.LintError
import io.github.usefulness.support.KtLintParams
import io.github.usefulness.support.KtlintErrorResult
import io.github.usefulness.support.createKtlintEngine
import io.github.usefulness.support.resetEditorconfigCacheIfNeeded
import io.github.usefulness.support.writeTo
import io.github.usefulness.tasks.LintTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Property
import org.gradle.internal.logging.slf4j.DefaultContextAwareTaskLogger
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters

internal abstract class LintWorker : WorkAction<LintWorker.Parameters> {
    private val logger = DefaultContextAwareTaskLogger(Logging.getLogger(LintTask::class.java))
    private val files = parameters.files
    private val projectDirectory = parameters.projectDirectory.asFile.get()
    private val name = parameters.name.get()
    private val ktLintParams = parameters.ktLintParams.get()

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
        val errors = mutableListOf<KtlintErrorResult>()

        files.forEach { file ->
            val relativePath = file.toRelativeString(projectDirectory)
            logger.debug("$name linting: $relativePath")

            if (file.extension !in supportedExtensions) {
                logger.debug("$name ignoring non Kotlin file: $relativePath")
                return@forEach
            }

            val fileErrors = mutableListOf<LintError>()
            ktLintEngine.lint(
                code = Code.CodeFile(file),
                callback = fileErrors::add,
            )
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
        val ktLintParams: Property<KtLintParams>
        val discoveredErrors: RegularFileProperty
    }
}

private val supportedExtensions = setOf("kt", "kts")
