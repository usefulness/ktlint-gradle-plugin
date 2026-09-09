package io.github.usefulness.customrules

import io.github.ktlint.core.cli.ruleset.core.api.RuleSetV2Provider
import io.github.ktlint.core.rule.engine.core.api.RuleV2Provider
import io.github.ktlint.core.rule.engine.core.api.RuleSetId

class CustomRuleSetProvider : RuleSetV2Provider(RuleSetId("custom-ktlint-rules")) {

    override fun getRuleProviders() = setOf(
        RuleV2Provider { NoNewLineBeforeReturnTypeRule() },
    )
}
