package io.github.usefulness.support.api

import io.github.ktlint.core.cli.reporter.baseline.Baseline
import io.github.ktlint.core.cli.reporter.baseline.BaselineErrorHandling
import io.github.ktlint.core.cli.reporter.baseline.loadBaseline
import io.github.ktlint.core.cli.reporter.core.api.KtlintCliError
import io.github.ktlint.core.cli.reporter.core.api.ReporterProviderV2
import io.github.ktlint.core.cli.reporter.core.api.ReporterV2
import io.github.ktlint.core.cli.ruleset.core.api.RuleSetV2Provider
import io.github.ktlint.core.rule.engine.api.Code
import io.github.ktlint.core.rule.engine.api.EditorConfigDefaults
import io.github.ktlint.core.rule.engine.api.EditorConfigOverride
import io.github.ktlint.core.rule.engine.api.KtLintRuleEngine
import io.github.ktlint.core.rule.engine.api.LintError
import io.github.ktlint.core.rule.engine.core.api.AutocorrectDecision
import io.github.ktlint.core.rule.engine.core.api.RuleSetId
import io.github.ktlint.core.rule.engine.core.api.RuleV2Provider
import io.github.ktlint.core.rule.engine.core.api.editorconfig.EditorConfigProperty
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
 * ktlint `2.x`, published as `io.github.ktlint.core`
 */
internal object Ktlint2Api : KtlintApi {

    override fun createEngine(disabledRules: List<String>, experimentalRules: Boolean): KtlintEngine = Engine(
        KtLintRuleEngine(
            ruleProviders = resolveRuleProviders(),
            editorConfigDefaults = EditorConfigDefaults(experimentalRulesEditorConfig(experimentalRules)),
            editorConfigOverride = editorConfigOverride(disabledRules),
        ),
    )

    override fun loadBaseline(baselineFile: File): Map<String, List<KtlintError>>? {
        val baseline = loadBaseline(baselineFile.absolutePath, errorHandling = BaselineErrorHandling.LOG)
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

        override fun format(file: File, callback: (KtlintError) -> Unit): String = engine.format(code = Code.fromFile(file)) { error ->
            val corrected = error.canBeAutoCorrected
            callback(error.toFormatError(corrected))

            if (corrected) AutocorrectDecision.ALLOW_AUTOCORRECT else AutocorrectDecision.NO_AUTOCORRECT
        }
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
private fun resolveRuleProviders(): Set<RuleV2Provider> = ServiceLoader.load(RuleSetV2Provider::class.java)
    .toList()
    .sortedBy { provider -> if (provider.id.value == RuleSetId.STANDARD.value) 0 else 1 }
    .flatMap(RuleSetV2Provider::getRuleProviders)
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
