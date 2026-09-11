package io.github.usefulness.support

import io.github.ktlint.core.cli.ruleset.core.api.RuleSetV2Provider
import io.github.ktlint.core.rule.engine.core.api.RuleV2Provider
import io.github.ktlint.core.rule.engine.core.api.RuleSetId
import java.util.ServiceLoader

internal fun resolveRuleProviders(providers: Iterable<RuleSetV2Provider>): Set<RuleV2Provider> = providers
    .asSequence()
    .sortedWith(
        compareBy {
            when (it.id.value) {
                RuleSetId.STANDARD.value -> 0
                else -> 1
            }
        },
    )
    .map(RuleSetV2Provider::getRuleProviders)
    .flatten()
    .toSet()

// statically resolve providers from plugin classpath. ServiceLoader#load alone resolves classes lazily which fails when run in parallel
internal val defaultRuleSetProviders: List<RuleSetV2Provider> =
    ServiceLoader.load(RuleSetV2Provider::class.java).toList()
