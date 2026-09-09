package io.github.usefulness.support.api

import io.github.usefulness.support.KtlintError
import io.github.usefulness.support.ReporterType
import java.io.File
import java.nio.file.Path

/**
 * The subset of ktlint the plugin relies on. Each supported ktlint major has its own implementation, since ktlint 2.x
 * moved to a different maven group and package (`io.github.ktlint.core`) than ktlint 1.x (`com.pinterest.ktlint`).
 */
internal interface KtlintApi {

    fun createEngine(disabledRules: List<String>, experimentalRules: Boolean): KtlintEngine

    /**
     * @return baseline errors keyed by the file path relative to the project, or `null` when there is no usable baseline
     */
    fun loadBaseline(baselineFile: File): Map<String, List<KtlintError>>?

    fun createReporters(enabled: Map<ReporterType, File>): Map<ReporterType, KtlintReporter>
}

internal interface KtlintEngine {

    val ruleProvidersCount: Int

    fun ruleIds(): List<String>

    fun reloadEditorConfigFile(path: Path)

    fun lint(file: File, callback: (KtlintError) -> Unit)

    /**
     * @return formatted content of the [file]. It is not written back to the file
     */
    fun format(file: File, callback: (KtlintError) -> Unit): String
}

internal interface KtlintReporter {

    fun beforeAll()

    fun before(file: String)

    fun onLintError(file: String, error: KtlintError)

    fun after(file: String)

    fun afterAll()
}

/**
 * Picks the implementation matching the ktlint present on the worker classpath.
 * Only the selected implementation gets loaded, so classes of the other ktlint generation are never touched.
 */
internal fun resolveKtlintApi(): KtlintApi = when {
    isOnClasspath("io.github.ktlint.core.rule.engine.api.KtLintRuleEngine") -> Ktlint2Api

    isOnClasspath("com.pinterest.ktlint.rule.engine.api.KtLintRuleEngine") -> Ktlint1Api

    else -> error(
        "Unsupported ktlint version. " +
            "The plugin supports ktlint 1.x (com.pinterest.ktlint:ktlint-cli) and 2.x (io.github.ktlint.core:ktlint-cli)",
    )
}

private fun isOnClasspath(className: String) = runCatching {
    Class.forName(className, false, KtlintApi::class.java.classLoader)
}.isSuccess
