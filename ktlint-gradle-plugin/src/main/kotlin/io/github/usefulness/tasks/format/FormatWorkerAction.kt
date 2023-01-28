package io.github.usefulness.tasks.format

import com.pinterest.ktlint.core.Code
import io.github.usefulness.support.KtLintParams
import io.github.usefulness.support.createKtlintEngine
import io.github.usefulness.support.resetEditorconfigCacheIfNeeded
import io.github.usefulness.tasks.FormatTask
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.internal.logging.slf4j.DefaultContextAwareTaskLogger
import org.gradle.workers.WorkAction
import java.io.File

abstract class FormatWorkerAction : WorkAction<FormatWorkerParameters> {
    private val logger: Logger = DefaultContextAwareTaskLogger(Logging.getLogger(FormatTask::class.java))
    private val files: Iterable<File> = parameters.files
    private val projectDirectory: File = parameters.projectDirectory.asFile.get()
    private val name: String = parameters.name.get()
    private val ktLintParams: KtLintParams = parameters.ktLintParams.get()
    private val output: File? = parameters.output.asFile.orNull

    override fun execute() {
        val ktLintEngine = createKtlintEngine(ktLintParams = ktLintParams)
        ktLintEngine.resetEditorconfigCacheIfNeeded(
            changedEditorconfigFiles = parameters.changedEditorConfigFiles,
            logger = logger,
        )
        logger.info("$name - resolved ${ktLintEngine.ruleProviders.size} RuleProviders")
        logger.info("$name - executing against ${files.count()} file(s)")

        var notFixedFiles = 0
        val fixes = mutableListOf<String>()
        files.sorted().forEach { file ->
            val relativePath = file.toRelativeString(projectDirectory)

            logger.debug("$name checking format: $relativePath")

            if (file.extension !in supportedExtensions) {
                logger.log(LogLevel.DEBUG, "$name ignoring non Kotlin file: $relativePath")
                return@forEach
            }

            var fileFixed = false
            val fixedContent = ktLintEngine.format(code = Code.CodeFile(file = file)) { error, corrected ->
                val msg = when (corrected) {
                    true -> "${file.path}:${error.line}:${error.col}: Format fixed > [${error.ruleId}] ${error.detail}"
                    false -> "${file.path}:${error.line}:${error.col}: Format could not fix > [${error.ruleId}] ${error.detail}"
                }
                logger.log(LogLevel.QUIET, msg)
                fixes.add(msg)
                if (corrected) {
                    fileFixed = true
                } else {
                    notFixedFiles++
                }
            }

            if (fileFixed) {
                file.writeText(fixedContent)
            }
        }

        output?.writeText(
            when (fixes.isEmpty()) {
                true -> "ok"
                false -> fixes.joinToString("\n")
            },
        )
        if (notFixedFiles > 0) {
            logger.warn("Format failed to autocorrect $notFixedFiles file(s)")
        }
    }
}

private val supportedExtensions = setOf("kt", "kts")
