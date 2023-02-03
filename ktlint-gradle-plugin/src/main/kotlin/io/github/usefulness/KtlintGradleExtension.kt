package io.github.usefulness

import io.github.usefulness.support.versionProperties

public open class KtlintGradleExtension {
    internal companion object {
        const val DEFAULT_IGNORE_FAILURES = false
        const val DEFAULT_EXPERIMENTAL_RULES = false
        const val DEFAULT_CHUNK_SIZE = 100
        val DEFAULT_DISABLED_RULES = emptyArray<String>()
    }

    public var ignoreFailures: Boolean = DEFAULT_IGNORE_FAILURES

    public var reporters: Array<String> = emptyArray()

    public var experimentalRules: Boolean = DEFAULT_EXPERIMENTAL_RULES

    public var disabledRules: Array<String> = DEFAULT_DISABLED_RULES

    public var ktlintVersion: String = versionProperties.ktlintVersion()

    public var chunkSize: Int = DEFAULT_CHUNK_SIZE
}
