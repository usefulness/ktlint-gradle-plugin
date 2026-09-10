package io.github.usefulness.support

import org.ec4j.core.model.EditorConfig
import org.ec4j.core.model.Glob
import org.ec4j.core.model.Property
import org.ec4j.core.model.Section
import java.io.File

/**
 * `.editorconfig` property name/value pairs disabling given rules. They are meant to be applied as `EditorConfigOverride`
 */
internal fun disabledRulesProperties(disabledRules: List<String>): List<Pair<String, String>> = disabledRules
    .map { ruleName -> getKtlintRulePropertyName(ruleName) to "disabled" }

/**
 * `.editorconfig` model toggling experimental rules. It is meant to be applied as `EditorConfigDefaults`
 */
internal fun experimentalRulesEditorConfig(includeExperimentalRules: Boolean): EditorConfig = EditorConfig
    .builder()
    .section(
        Section
            .builder()
            .glob(Glob("*.{kt,kts}"))
            .properties(
                Property
                    .builder()
                    .name("ktlint_experimental")
                    .value(if (includeExperimentalRules) "enabled" else "disabled"),
            ),
    )
    .build()

private fun getKtlintRulePropertyName(ruleName: String) = if (ruleName.contains(':')) { // Rule from a non-standard rule set
    "ktlint_${ruleName.replace(':', '_')}"
} else {
    "ktlint_standard_$ruleName"
}

internal val File.isRootEditorConfig: Boolean
    get() {
        if (!isFile || !canRead()) return false

        return useLines { lines ->
            lines.any { line -> line.matches(editorConfigRootRegex) }
        }
    }

/**
 * According to https://editorconfig.org/ root-most EditorConfig file contains line with `root=true`
 */
private val editorConfigRootRegex = "^root\\s?=\\s?true".toRegex()
