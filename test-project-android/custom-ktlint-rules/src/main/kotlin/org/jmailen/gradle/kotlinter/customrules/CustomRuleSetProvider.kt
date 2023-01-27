package io.github.usefulness.customrules

import com.pinterest.ktlint.core.RuleProvider
import com.pinterest.ktlint.core.RuleSetProviderV2

class CustomRuleSetProvider : RuleSetProviderV2(
    "custom-ktlint-rules",
    about = NO_ABOUT,
) {

    override fun getRuleProviders() = setOf(
        RuleProvider { NoNewLineBeforeReturnTypeRule() },
    )
}
