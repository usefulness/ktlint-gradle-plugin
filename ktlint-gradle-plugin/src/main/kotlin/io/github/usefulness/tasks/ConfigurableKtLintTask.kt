package io.github.usefulness.tasks

import groovy.lang.Closure
import io.github.usefulness.KtlintGradleExtension.Companion.DEFAULT_DISABLED_RULES
import io.github.usefulness.KtlintGradleExtension.Companion.DEFAULT_EXPERIMENTAL_RULES
import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTreeElement
import org.gradle.api.file.ProjectLayout
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.util.PatternFilterable
import org.gradle.api.tasks.util.PatternSet
import org.gradle.work.FileChange
import org.gradle.work.Incremental
import org.gradle.work.InputChanges
import io.github.usefulness.support.KtLintParams
import io.github.usefulness.support.findApplicableEditorConfigFiles
import org.gradle.api.file.ConfigurableFileCollection
import java.util.concurrent.Callable

public abstract class ConfigurableKtLintTask(
    projectLayout: ProjectLayout,
    objectFactory: ObjectFactory,
) : DefaultTask(), PatternFilterable {

    @Input
    public val experimentalRules: Property<Boolean> = objectFactory.property(default = DEFAULT_EXPERIMENTAL_RULES)

    @Input
    public val disabledRules: ListProperty<String> = objectFactory.listProperty(default = DEFAULT_DISABLED_RULES.toList())

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Incremental
    internal val editorconfigFiles = objectFactory.fileCollection().apply {
        from(projectLayout.findApplicableEditorConfigFiles().toList())
    }

    @Input
    public val workerMaxHeapSize: Property<String> = objectFactory.property(default = "256m")

    @Classpath
    public val ktlintClasspath: ConfigurableFileCollection = objectFactory.fileCollection()

    @Classpath
    public val ruleSetsClasspath: ConfigurableFileCollection = objectFactory.fileCollection()

    private val allSourceFiles = project.objects.fileCollection()

    @get:Internal
    internal val patternFilterable: PatternFilterable = PatternSet()

    @SkipWhenEmpty // Marks the input incremental: https://github.com/gradle/gradle/issues/17593
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    @IgnoreEmptyDirectories
    public val source: FileCollection = objectFactory.fileCollection()
        .from(Callable { allSourceFiles.asFileTree.matching(patternFilterable) })

    public fun source(vararg sources: Any?): ConfigurableKtLintTask = also { allSourceFiles.setFrom(*sources) }

    public fun setSource(source: Any) {
        allSourceFiles.setFrom(source)
    }

    @Internal
    internal fun getKtLintParams(): KtLintParams = KtLintParams(
        experimentalRules = experimentalRules.get(),
        disabledRules = disabledRules.get(),
    )

    @Internal
    override fun getIncludes(): MutableSet<String> = patternFilterable.includes

    @Internal
    override fun getExcludes(): MutableSet<String> = patternFilterable.excludes

    override fun setIncludes(includes: MutableIterable<String>): ConfigurableKtLintTask = also { patternFilterable.setIncludes(includes) }
    override fun setExcludes(excludes: MutableIterable<String>): ConfigurableKtLintTask = also { patternFilterable.setExcludes(excludes) }
    override fun include(vararg includes: String?): ConfigurableKtLintTask = also { patternFilterable.include(*includes) }
    override fun include(includes: MutableIterable<String>): ConfigurableKtLintTask = also { patternFilterable.include(includes) }
    override fun include(includeSpec: Spec<FileTreeElement>): ConfigurableKtLintTask = also { patternFilterable.include(includeSpec) }
    override fun include(includeSpec: Closure<*>): ConfigurableKtLintTask = also { patternFilterable.include(includeSpec) }
    override fun exclude(vararg excludes: String?): ConfigurableKtLintTask = also { patternFilterable.exclude(*excludes) }
    override fun exclude(excludes: MutableIterable<String>): ConfigurableKtLintTask = also { patternFilterable.exclude(excludes) }
    override fun exclude(excludeSpec: Spec<FileTreeElement>): ConfigurableKtLintTask = also { patternFilterable.exclude(excludeSpec) }
    override fun exclude(excludeSpec: Closure<*>): ConfigurableKtLintTask = also { patternFilterable.exclude(excludeSpec) }
}

internal inline fun <reified T> ObjectFactory.property(default: T? = null): Property<T> =
    property(T::class.java).apply {
        set(default)
    }

internal inline fun <reified T> ObjectFactory.listProperty(default: Iterable<T> = emptyList()): ListProperty<T> =
    listProperty(T::class.java).apply {
        set(default)
    }

internal inline fun <reified K, reified V> ObjectFactory.mapProperty(default: Map<K, V> = emptyMap()): MapProperty<K, V> =
    mapProperty(K::class.java, V::class.java).apply {
        set(default)
    }

internal fun ConfigurableKtLintTask.getChangedEditorconfigFiles(inputChanges: InputChanges) =
    inputChanges.getFileChanges(editorconfigFiles).map(FileChange::getFile)

internal fun ConfigurableKtLintTask.getChangedSources(inputChanges: InputChanges) =
    if (inputChanges.isIncremental && inputChanges.getFileChanges(editorconfigFiles).none()) {
        inputChanges.getFileChanges(source).map(FileChange::getFile)
    } else {
        source
    }
