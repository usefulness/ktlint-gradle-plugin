// ktlint 1.8 deprecates the callback-based `format`, but it is the only overload available across the whole 1.x line
@file:Suppress("DEPRECATION")

package io.github.usefulness.support.api

import com.pinterest.ktlint.cli.reporter.baseline.Baseline
import com.pinterest.ktlint.cli.reporter.baseline.loadBaseline
import com.pinterest.ktlint.cli.reporter.core.api.KtlintCliError
import com.pinterest.ktlint.cli.reporter.core.api.ReporterProviderV2
import com.pinterest.ktlint.cli.reporter.core.api.ReporterV2
import com.pinterest.ktlint.cli.ruleset.core.api.RuleSetProviderV3
import com.pinterest.ktlint.rule.engine.api.Code
import com.pinterest.ktlint.rule.engine.api.EditorConfigDefaults
import com.pinterest.ktlint.rule.engine.api.EditorConfigOverride
import com.pinterest.ktlint.rule.engine.api.KtLintRuleEngine
import com.pinterest.ktlint.rule.engine.api.LintError
import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import com.pinterest.ktlint.rule.engine.core.api.RuleSetId
import com.pinterest.ktlint.rule.engine.core.api.editorconfig.EditorConfigProperty
import io.github.usefulness.support.KtlintError
import io.github.usefulness.support.ReporterType
import io.github.usefulness.support.disabledRulesProperties
import io.github.usefulness.support.experimentalRulesEditorConfig
import io.github.usefulness.support.reporterOptions
import org.ec4j.core.model.PropertyType
import org.ec4j.core.model.PropertyType.PropertyValueParser.IDENTITY_VALUE_PARSER
import java.io.File
import java.io.PrintStream
import java.nio.file.Path
import java.util.ServiceLoader

/**
 * ktlint `1.x`, published as `com.pinterest.ktlint`
 */
internal object Ktlint1Api : KtlintApi {

    override fun createEngine(disabledRules: List<String>, experimentalRules: Boolean): KtlintEngine = Engine(
        KtLintRuleEngine(
            ruleProviders = resolveRuleProviders(),
            editorConfigDefaults = EditorConfigDefaults(experimentalRulesEditorConfig(experimentalRules)),
            editorConfigOverride = editorConfigOverride(disabledRules),
        ),
    )

    override fun loadBaseline(baselineFile: File): Map<String, List<KtlintError>>? {
        val baseline = loadBaseline(baselineFile.absolutePath)
        when (baseline.status) {
            Baseline.Status.VALID -> Unit

            Baseline.Status.NOT_FOUND,
            Baseline.Status.INVALID,
            Baseline.Status.DISABLED,
            -> return null
        }

        return baseline.lintErrorsPerFile.mapValues { (_, errors) -> errors.map(KtlintCliError::toKtlintError) }
    }

    override fun createReporters(enabled: Map<ReporterType, File>): Map<ReporterType, KtlintReporter> {
        val providers = ServiceLoader.load(ReporterProviderV2::class.java).associateBy { it.id }

        return enabled
            .filter { (type, _) -> providers.containsKey(type.id) }
            .mapValues { (type, output) ->
                Reporter(providers.getValue(type.id).get(out = PrintStream(output), opt = type.reporterOptions()))
            }
    }

    private class Engine(private val engine: KtLintRuleEngine) : KtlintEngine {

        override val ruleProvidersCount get() = engine.ruleProviders.size

        override fun ruleIds() = engine.ruleProviders.map { it.createNewRuleInstance().ruleId.value }

        override fun reloadEditorConfigFile(path: Path) = engine.reloadEditorConfigFile(path)

        override fun lint(file: File, callback: (KtlintError) -> Unit) = engine.lint(code = Code.fromFile(file)) { error ->
            callback(error.toLintError())
        }

        override fun format(file: File, callback: (KtlintError) -> Unit): String =
            engine.format(code = Code.fromFile(file)) { error, corrected -> callback(error.toFormatError(corrected)) }
    }

    private class Reporter(private val reporter: ReporterV2) : KtlintReporter {

        override fun beforeAll() = reporter.beforeAll()

        override fun before(file: String) = reporter.before(file)

        override fun onLintError(file: String, error: KtlintError) = reporter.onLintError(file, error.toKtlintCliError())

        override fun after(file: String) = reporter.after(file)

        override fun afterAll() = reporter.afterAll()
    }
}

// statically resolve providers from plugin classpath. ServiceLoader#load alone resolves classes lazily which fails when run in parallel
private fun resolveRuleProviders(): Set<RuleProvider> = ServiceLoader.load(RuleSetProviderV3::class.java)
    .toList()
    .sortedBy { provider -> if (provider.id.value == RuleSetId.STANDARD.value) 0 else 1 }
    .flatMap(RuleSetProviderV3::getRuleProviders)
    .toSet()

private fun editorConfigOverride(disabledRules: List<String>): EditorConfigOverride {
    val properties = disabledRulesProperties(disabledRules)
    if (properties.isEmpty()) return EditorConfigOverride.EMPTY_EDITOR_CONFIG_OVERRIDE

    return EditorConfigOverride.from(
        *properties
            .map { (name, value) ->
                EditorConfigProperty(type = PropertyType(name, "Rule to be disabled", IDENTITY_VALUE_PARSER), defaultValue = value) to value
            }
            .toTypedArray(),
    )
}

private fun LintError.toLintError() = KtlintError.forLint(
    line = line,
    col = col,
    ruleId = ruleId.value,
    detail = detail,
    canBeAutoCorrected = canBeAutoCorrected,
)

private fun LintError.toFormatError(corrected: Boolean) = KtlintError.forFormat(
    line = line,
    col = col,
    ruleId = ruleId.value,
    detail = detail,
    corrected = corrected,
)

private fun KtlintCliError.toKtlintError() = KtlintError(
    line = line,
    col = col,
    ruleId = ruleId,
    detail = detail,
    status = KtlintError.Status.valueOf(status.name),
)

private fun KtlintError.toKtlintCliError() = KtlintCliError(
    line = line,
    col = col,
    ruleId = ruleId,
    detail = detail,
    status = KtlintCliError.Status.valueOf(status.name),
)
