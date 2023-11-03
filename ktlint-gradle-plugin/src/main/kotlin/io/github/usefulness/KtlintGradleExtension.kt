package io.github.usefulness

import io.github.usefulness.support.versionProperties
import io.github.usefulness.tasks.listProperty
import io.github.usefulness.tasks.property
import org.gradle.api.Incubating
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

    public val ignoreFailures: Property<Boolean> = objectFactory.property(default = DEFAULT_IGNORE_FAILURES)

    public val reporters: ListProperty<String> = objectFactory.listProperty(default = emptyList())

    public val experimentalRules: Property<Boolean> = objectFactory.property(default = DEFAULT_EXPERIMENTAL_RULES)

    public val disabledRules: ListProperty<String> = objectFactory.listProperty(default = DEFAULT_DISABLED_RULES)

    public val ktlintVersion: Property<String> = objectFactory.property(providerFactory.provider { versionProperties.ktlintVersion() })

    public val chunkSize: Property<Int> = objectFactory.property(default = DEFAULT_CHUNK_SIZE)

    public val baselineFile: RegularFileProperty = objectFactory.fileProperty()

    @Incubating
    public val ignoreFilesUnderBuildDir: Property<Boolean> = objectFactory.property(default = true)

    @Deprecated(message = "Will be removed in the next version", replaceWith = ReplaceWith(expression = "ignoreFilesUnderBuildDir"))
    @Incubating
    public val ignoreKspGeneratedSources: Property<Boolean> = ignoreFilesUnderBuildDir
}
