package io.github.usefulness.customrules

import com.pinterest.ktlint.core.RuleProvider
import com.pinterest.ktlint.cli.ruleset.core.api.RuleSetProviderV3

class CustomRuleSetProvider : RuleSetProviderV3(
    "custom-ktlint-rules",
    about = NO_ABOUT,
) {

    override fun getRuleProviders() = setOf(
        RuleProvider { NoNewLineBeforeReturnTypeRule() },
    )
}
