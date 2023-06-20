package io.github.usefulness

import io.github.usefulness.support.versionProperties
import io.github.usefulness.tasks.listProperty
import io.github.usefulness.tasks.property
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.ProviderFactory

public open class KtlintGradleExtension internal constructor(
    objectFactory: ObjectFactory,
    providerFactory: ProviderFactory,
) {

    internal companion object {
        const val DEFAULT_IGNORE_FAILURES = false
        const val DEFAULT_EXPERIMENTAL_RULES = false
        const val DEFAULT_CHUNK_SIZE = 50
        val DEFAULT_DISABLED_RULES = emptyList<String>()
    }

    public var ignoreFailures: Property<Boolean> = objectFactory.property(default = DEFAULT_IGNORE_FAILURES)

    public var reporters: ListProperty<String> = objectFactory.listProperty(default = emptyList())

    public var experimentalRules: Property<Boolean> = objectFactory.property(default = DEFAULT_EXPERIMENTAL_RULES)

    public var disabledRules: ListProperty<String> = objectFactory.listProperty(default = DEFAULT_DISABLED_RULES)

    public var ktlintVersion: Property<String> = objectFactory.property(providerFactory.provider { versionProperties.ktlintVersion() })

    public var chunkSize: Property<Int> = objectFactory.property(default = DEFAULT_CHUNK_SIZE)

    public var baselineFile: RegularFileProperty = objectFactory.fileProperty()
}
