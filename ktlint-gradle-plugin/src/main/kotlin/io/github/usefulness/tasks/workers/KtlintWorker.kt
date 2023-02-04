package io.github.usefulness.tasks.workers

import com.pinterest.ktlint.core.Code
import com.pinterest.ktlint.core.LintError
import io.github.usefulness.support.KtLintParams
import io.github.usefulness.support.KtlintErrorResult
import io.github.usefulness.support.KtlintRunMode
import io.github.usefulness.support.createKtlintEngine
import io.github.usefulness.support.resetEditorconfigCacheIfNeeded
import io.github.usefulness.support.writeTo
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Property
import org.gradle.internal.logging.slf4j.DefaultContextAwareTaskLogger
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters

internal abstract class KtlintWorker : WorkAction<KtlintWorker.Parameters> {
    private val logger = DefaultContextAwareTaskLogger(Logging.getLogger(KtlintWorker::class.java))

    private val name = parameters.name.get()

    override fun execute() {
        val projectDir = parameters.projectDirectory.asFile.get()
        val files = parameters.files

        val ktLintEngine = createKtlintEngine(ktLintParams = parameters.ktLintParams.get())
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
            val relativePath = file.toRelativeString(projectDir)
            logger.debug("$name linting: $relativePath")

            if (file.extension !in supportedExtensions) {
                logger.warn("$name ignoring non Kotlin file: $relativePath")
                return@forEach
            }

            val fileErrors = mutableListOf<Pair<LintError, Boolean>>()
            when (parameters.mode.get()) {
                KtlintRunMode.Check,
                null,
                -> ktLintEngine.lint(
                    code = Code.CodeFile(file),
                    callback = { fileErrors.add(it to false) },
                )

                KtlintRunMode.Format -> {
                    var fileFixed = false
                    val fixedContent = ktLintEngine.format(
                        code = Code.CodeFile(file),
                        callback = { error, corrected ->
                            if (corrected) {
                                fileFixed = true
                            }
                            fileErrors.add(error to corrected)
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
        val ktLintParams: Property<KtLintParams>
        val discoveredErrors: RegularFileProperty
        val mode: Property<KtlintRunMode>
    }
}

private val supportedExtensions = setOf("kt", "kts")
