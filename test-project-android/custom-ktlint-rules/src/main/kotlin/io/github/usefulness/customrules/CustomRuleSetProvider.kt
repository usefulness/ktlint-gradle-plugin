package io.github.usefulness.customrules

import com.pinterest.ktlint.cli.ruleset.core.api.RuleSetProviderV3
import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import com.pinterest.ktlint.rule.engine.core.api.RuleSetId

class CustomRuleSetProvider : RuleSetProviderV3(RuleSetId("custom-ktlint-rules")) {

    override fun getRuleProviders() = setOf(
        RuleProvider { NoNewLineBeforeReturnTypeRule() },
    )
}
