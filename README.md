# ktlint-gradle-plugin

[![Build Status](https://github.com/usefulness/ktlint-gradle-plugin/actions/workflows/default.yml/badge.svg?branch=master)](https://github.com/usefulness/ktlint-gradle-plugin/actions/workflows/default.yml)
[![Latest Version](https://img.shields.io/maven-metadata/v/https/plugins.gradle.org/m2/io/github/usefulness/ktlint-gradle-plugin/maven-metadata.xml?label=gradle)](https://plugins.gradle.org/plugin/io.github.usefulness.ktlint-gradle-plugin)
![Maven Central](https://img.shields.io/maven-central/v/io.github.usefulness/ktlint-gradle-plugin)

Gradle wrapper for [pinterest/ktlint](https://github.com/pinterest/ktlint)

### Installation

Available on:

- [Gradle Plugin Portal](https://plugins.gradle.org/plugin/io.github.usefulness.ktlint-gradle-plugin)
- [Maven Central](https://mvnrepository.com/artifact/io.github.usefulness/ktlint-gradle-plugin)

#### Apply the plugin

```groovy
plugins {
    id("io.github.usefulness.ktlint-gradle-plugin") version "{{version}}"
}
```

##### (Optional) Managing `ktlint` version with Renovate/Dependaot
Given the plugin is just a wrapper and follows its own release cycle, it may a good practice to manually control `ktlint` tool version. One can achieve it by defining full ktlint version dependency and passing resolved ktlint version to the `ktlint` extension. Version Catalog example:
```toml
[versions]
ktlint = "x.y.z"

[libraries]
ktlint-cli = { module = "com.pinterest.ktlint:ktlint-cli", version.ref = "ktlint" } 
```
\+ then 
```groovy
ktlint {
    ktlintVersion = libs.versions.ktlint.get()
}
```


### Compatibility

| plugin version | min gradle version | min ktlint version |
|----------------|--------------------|--------------------|
| 0.7.0+         | 8.0                | 1.0.0              |
| 0.5.0+         | 7.6                | 1.0.0              |
| 0.4.0+         | 7.6                | 0.50.0             |
| 0.3.0+         | 7.6                | 0.49.0             |
| 0.1.0+         | 7.6                | 0.48.0             |

### Features

- Supports Kotlin Gradle plugins:
    - [JVM](https://plugins.gradle.org/plugin/org.jetbrains.kotlin.jvm)
    - [Multiplatform](https://plugins.gradle.org/plugin/org.jetbrains.kotlin.multiplatform)
    - [Android](https://plugins.gradle.org/plugin/org.jetbrains.kotlin.android)
    - [JS](https://plugins.gradle.org/plugin/org.jetbrains.kotlin.js)
- Supports `.kt` and `.kts` files
- Leverages latest Gradle APIs (cacheable, incremental tasks using Gradle Worker API)   
- Configurable reporters

### Tasks

When your project uses one of the supported Kotlin Gradle plugins, the plugin adds these tasks:

- `./gradlew formatKotlin`: format Kotlin source code according to `ktlint` rules or warn when auto-format not possible.

- `./gradlew lintKotlin`: report Kotlin lint errors and by default fail the build.

Also `check` becomes dependent on `lintKotlin`.

Granular tasks are added for each source set in the project: `formatKotlin`*`SourceSet`* and `lintKotlin`*`SourceSet`*.

### Configuration

Options can be configured in the `ktlint` extension:

```groovy
ktlint {
    ignoreFailures = false
    reporters = ["checkstyle", "html", "json", "plain", "sarif"]
    experimentalRules = true
    disabledRules = ["no-wildcard-imports", "experimental:annotation", "your-custom-rule:no-bugs"]
    ktlintVersion = "2.0.0-SNAPSHOT"
    chunkSize = 50
    baselineFile.set(file("config/ktlint_baseline.xml"))
    ignoreFilesUnderBuildDir = true
}
```

- `ignoreFailures` - makes the `LintTask` tasks always pass
- `reporters` - defines enable [reporters](https://pinterest.github.io/ktlint/install/cli/#violation-reporting) for all
  tasks.
- `experimentalRules` - enables rules from ktlint [Experimental](https://pinterest.github.io/ktlint/rules/experimental/)
  ruleset.
- `disabledRules` - can include an array of rule ids you wish to disable
- `ktlintVersion` There is a basic support for overriding ktlint version, but the plugin doesn't guarantee backwards
  compatibility with all `ktlint` versions.
  Errors like `java.lang.NoSuchMethodError:` or `com/pinterest/ktlint/core/KtLint$Params` can be thrown if
  provided `ktlint` version isn't compatible with the latest ktlint apis.

- `chunkSize` - defines how many files will be processed by a single gradle worker in parallel
- `baselineFile` - points at location of baseline file containing _known_ offenses that will be ignored during `lintKotlin` task execution
- `ignoreFilesUnderBuildDir` - This allows to ignore generated sources. This is a workaround for https://youtrack.jetbrains.com/issue/KT-45161. Setting the value to `false` restores default behavior and will run ktlint against all sources returned by KGP  
- `editorConfigValidation` - One of `None`, `PrintWarningLogs`, `BuildFailure`.
  - Currently, this only validates if any of the _typical_ editorconfig location contain `root=true` entry. This is highly recommended to ensure the builds are deterministic across machines  

### Customizing Tasks

The `formatKotlin`*`SourceSet`* and `lintKotlin`*`SourceSet`* tasks inherit
from [SourceTask](https://docs.gradle.org/current/dsl/org.gradle.api.tasks.SourceTask.html)
so you can customize includes, excludes, and source.

```groovy
tasks.named("lintKotlinMain") {
    exclude("com/example/**/generated/*.kt")
}
```

Note that exclude paths are relative to the package root.

#### Advanced

By default, Gradle workers will use 256MB of heap size. To adjust this setting use:

```groovy
import io.github.usefulness.tasks.KtlintWorkTask

tasks.withType(KtlintWorkTask).configureEach {
    workerMaxHeapSize.set("512m")
}
```

### Custom Rules

You can add custom `ktlint` RuleSets using the `ktlintRuleSet` configuration dependency:

```groovy
dependencies {
    ktlintRuleSet(files("libs/my-custom-ktlint-rules.jar"))
    ktlintRuleSet(project(":ktlint-custom-rules"))
    ktlintRuleSet("org.other.ktlint:custom-rules:1.0")
    ktlintRuleSet("io.nlopez.compose.rules:ktlint:0.3.3")
}
```

Credits
---

<img src="https://www.yourkit.com/images/yklogo.png" alt="YourKit logo" />

YourKit supports open source projects with innovative and intelligent tools
for monitoring and profiling Java and .NET applications.
YourKit is the creator of <a href="https://www.yourkit.com/java/profiler/">YourKit Java Profiler</a>,
<a href="https://www.yourkit.com/.net/profiler/">YourKit .NET Profiler</a>,
and <a href="https://www.yourkit.com/youmonitor/">YourKit YouMonitor</a>.
