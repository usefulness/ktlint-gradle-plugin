# ktlint-gradle-plugin

[![Build Status](https://github.com/usefulness/ktlint-gradle-plugin/workflows/Build%20Project/badge.svg)](https://github.com/usefulness/ktlint-gradle-plugin/actions)
[![Latest Version](https://img.shields.io/maven-metadata/v/https/plugins.gradle.org/m2/io/github/usefulness/ktlin-gradle-plugin/maven-metadata.xml?label=gradle)](https://plugins.gradle.org/plugin/io.github.usefulness.ktlint-gradle-plugin)

Simple and

### Installation

Available on the Gradle Plugin Portal: https://plugins.gradle.org/plugin/io.github.usefulness.ktlint-gradle-plugin

#### Single module

<details open>
<summary>Kotlin</summary>

```kotlin
plugins {
    id("io.github.usefulness.ktlint-gradle-plugin") version "3.13.0"
}
```


### Compatibility

| plugin version | min gradle version | min ktlint version |
|----------------|--------------------|--------------------|
| 3.14.0+        | 7.6                | 0.48.0             |

### Features

- Supports Kotlin Gradle plugins:
  - [JVM](https://plugins.gradle.org/plugin/org.jetbrains.kotlin.jvm)
  - [Multiplatform](https://plugins.gradle.org/plugin/org.jetbrains.kotlin.multiplatform)
  - [Android](https://plugins.gradle.org/plugin/org.jetbrains.kotlin.android)
  - [JS](https://plugins.gradle.org/plugin/org.jetbrains.kotlin.js)
- Supports `.kt` and `.kts` files
- Standalone `LintTask` and `FormatTask` types for defining custom tasks
- Incremental build support and fast parallelization with Gradle Worker API
- Configures from `.editorconfig` when available
- Configurable reporters

### Tasks

When your project uses one of the supported Kotlin Gradle plugins, the plugin adds these tasks:

`formatKotlin`: format Kotlin source code according to `ktlint` rules or warn when auto-format not possible.

`lintKotlin`: report Kotlin lint errors and by default fail the build.

Also `check` becomes dependent on `lintKotlin`.

Granular tasks are added for each source set in the project: `formatKotlin`*`SourceSet`* and `lintKotlin`*`SourceSet`*.

### Configuration
Options are configured in the `ktlin` extension. Defaults shown (you may omit the configuration block entirely if you want these defaults).

<details open>
<summary>Kotlin</summary>

```kotlin
ktlint {
    ignoreFailures = false
    reporters = arrayOf("checkstyle", "plain")
    experimentalRules = false
    disabledRules = emptyArray()
    ktlintVersion = "x.y.z"
}
```

</details>

<details>
<summary>Groovy</summary>

```groovy
ktlint {
    ignoreFailures = false
    reporters = ['checkstyle', 'plain']
    experimentalRules = false
    disabledRules = []
    ktlintVersion = 'x.y.z'
}
```

</details>

Options for `reporters`: `checkstyle`, `html`, `json`, `plain`, `sarif`

Reporters behave as described at: https://github.com/pinterest/ktlint

The `experimentalRules` property enables rules which are part of ktlint's experimental rule set.

The `disabledRules` property can includes an array of rule ids you wish to disable. For example to allow wildcard imports:
```groovy
disabledRules = ["no-wildcard-imports"]
```
You must prefix rule ids not part of the standard rule set with `<rule-set-id>:<rule-id>`. For example `experimental:annotation`.

There is a basic support for overriding `ktlintVersion`, but the plugin doesn't guarantee backwards compatibility with all `ktlint` versions.
Errors like `java.lang.NoSuchMethodError:` or `com/pinterest/ktlint/core/KtLint$Params` can be thrown if provided `ktlint` version isn't compatible with the latest ktlint apis.

### Customizing Tasks

The `formatKotlin`*`SourceSet`* and `lintKotlin`*`SourceSet`* tasks inherit from [SourceTask](https://docs.gradle.org/current/dsl/org.gradle.api.tasks.SourceTask.html)
so you can customize includes, excludes, and source.

<details open>
<summary>Kotlin</summary>

```kotlin
tasks.lintKotlinMain {
  exclude("com/example/**/generated/*.kt")
}
```

</details>

<details>
<summary>Groovy</summary>

```groovy
tasks.named('lintKotlinMain') {
    exclude 'com/example/**/generated/*.kt'
}
```

</details>

Note that exclude paths are relative to the package root.

#### Advanced
By default, Gradle workers will use 256MB of heap size. To adjust this setting use:
<details>
<summary>Kotlin</summary>

```kotlin
import io.github.usefulness.tasks.ConfigurableKtLintTask

tasks.withType<ConfigurableKtLintTask> {
  workerMaxHeapSize.set("512m")
}
```

</details>

<details>
<summary>Groovy</summary>

```groovy
import io.github.usefulness.tasks.ConfigurableKtLintTask

tasks.withType(ConfigurableKtLintTask::class).configureEach {
  workerMaxHeapSize.set("512m")
}
```

</details>

### Custom Tasks

If you aren't using autoconfiguration from a supported plugin or otherwise need to handle additional source code, you can create custom tasks:

<details open>
<summary>Kotlin</summary>

```kotlin
import io.github.usefulness.tasks.LintTask
import io.github.usefulness.tasks.FormatTask

tasks.register<LintTask>("ktLint") {
    group = "verification"
    source(files("src"))
    reports.set(
        mapOf(
            "plain" to file("build/lint-report.txt"),
            "json" to file("build/lint-report.json")
        )
    )
}

tasks.register<FormatTask>("ktFormat") {
    group = "formatting"
    source(files("src"))
    report.set(file("build/format-report.txt"))
}
```

</details>

<details>
<summary>Groovy</summary>

```groovy
import io.github.usefulness.tasks.LintTask
import io.github.usefulness.tasks.FormatTask

tasks.register('ktLint', LintTask) {
    group 'verification'
    source files('src')
    reports = [
            'plain': file('build/lint-report.txt'),
            'json' : file('build/lint-report.json')
    ]
    disabledRules = ['import-ordering']
}


tasks.register('ktFormat', FormatTask) {
  group 'formatting'
  source files('src/test')
  report = file('build/format-report.txt')
  disabledRules = ['import-ordering']
}
```

</details>

### Custom Rules

You can add custom `ktlint` RuleSets using the `ktlintRuleSet` dependency:

<details open>
<summary>Kotlin</summary>

```kotlin
dependencies {
    ktlintRuleSet(files("libs/my-custom-ktlint-rules.jar"))
    ktlintRuleSet(project(":ktlint-custom-rules"))
    ktlintRuleSet("org.other.ktlint:custom-rules:1.0")
}
```

</details>

<details>
<summary>Groovy</summary>

```groovy
dependencies {
    ktlintRuleSet files('libs/my-custom-ktlint-rules.jar')
    ktlintRuleSet project(':ktlint-custom-rules')
    ktlintRuleSet 'org.other.ktlint:custom-rules:1.0'
}
```

</details>
