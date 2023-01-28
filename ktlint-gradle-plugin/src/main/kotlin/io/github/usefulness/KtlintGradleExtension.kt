package io.github.usefulness

import io.github.usefulness.support.ReporterType
import io.github.usefulness.support.versionProperties

open class KtlintGradleExtension {
    companion object {
        const val DEFAULT_IGNORE_FAILURES = false
        const val DEFAULT_EXPERIMENTAL_RULES = false
        val DEFAULT_REPORTER = ReporterType.Checkstyle.id
        val DEFAULT_DISABLED_RULES = emptyArray<String>()
    }

    var ignoreFailures = DEFAULT_IGNORE_FAILURES

    var reporters = emptyArray<String>()

    var experimentalRules = DEFAULT_EXPERIMENTAL_RULES

    var disabledRules = DEFAULT_DISABLED_RULES

    var ktlintVersion = versionProperties.ktlintVersion()
}
