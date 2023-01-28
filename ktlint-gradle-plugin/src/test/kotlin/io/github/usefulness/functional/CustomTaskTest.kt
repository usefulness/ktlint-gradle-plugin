package io.github.usefulness.functional

import org.gradle.testkit.runner.TaskOutcome
import io.github.usefulness.functional.utils.editorConfig
import io.github.usefulness.functional.utils.kotlinClass
import io.github.usefulness.functional.utils.resolve
import io.github.usefulness.functional.utils.settingsFile
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
import java.io.File

class CustomTaskTest : WithGradleTest.Kotlin() {

    lateinit var projectRoot: File

    @BeforeEach
    fun setUp() {
        projectRoot = testProjectDir.apply {
            resolve("settings.gradle") { writeText(settingsFile) }
            resolve("build.gradle") {
                // language=groovy
                val buildScript =
                    """
                    plugins {
                        id 'kotlin'
                        id 'io.github.usefulness.ktlint-gradle-plugin'
                    }
                    
                    repositories {
                        mavenCentral()
                    }
                
                    """.trimIndent()
                writeText(buildScript)
            }
        }
        projectRoot.resolve("src/main/kotlin/CustomClass.kt") {
            writeText(kotlinClass("CustomClass"))
        }
    }

    @Test
    fun `ktLint custom task succeeds when no lint errors detected`() {
        projectRoot.resolve(".editorconfig") {
            writeText(editorConfig)
        }
        projectRoot.resolve("src/main/kotlin/CustomClass.kt") {
            // language=kotlin
            val validClass =
                """
                class CustomClass {
                    private fun go() {
                        println("go")
                    }
                }
                """.trimIndent()
            writeText(validClass)
        }
        projectRoot.resolve("build.gradle") {
            // language=groovy
            val buildScript =
                """
                import io.github.usefulness.tasks.LintTask
    
                task ktLint(type: LintTask) {
                    source files('src')
                    reports = ['plain': file('build/lint-report.txt')]
                    experimentalRules = true
                    disabledRules = ["final-newline"]
                }
                
                """
            appendText(buildScript)
        }

        build("ktLint").apply {
            assertThat(task(":ktLint")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        }
    }

    @Test
    fun `ktLint custom task succeeds with default configuration`() {
        projectRoot.resolve("build.gradle") {
            // language=groovy
            val buildScript =
                """
                import io.github.usefulness.tasks.LintTask
    
                task minimalCustomTask(type: LintTask) {
                    source files('src')
                }
                
                """.trimIndent()
            appendText(buildScript)
        }

        build("minimalCustomTask").apply {
            assertThat(task(":minimalCustomTask")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        }
    }

    @Test
    fun `ktLint custom task succeeds with fully provided configuration`() {
        projectRoot.resolve(".editorconfig") {
            writeText(editorConfig)
        }
        projectRoot.resolve("build.gradle") {
            // language=groovy
            val buildScript =
                """
                import io.github.usefulness.tasks.LintTask
    
                task customizedLintTask(type: LintTask) {
                    source files('src')
                    reports = ['plain': file('build/lint-report.txt')]
                    experimentalRules = true
                    disabledRules = ["final-newline"]
                    ignoreFailures = true
                }
                
                """.trimIndent()
            appendText(buildScript)
        }

        build("customizedLintTask").apply {
            assertThat(task(":customizedLintTask")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        }
    }

    @Test
    fun `ktLintFormat custom task succeeds with fully provided configuration`() {
        projectRoot.resolve(".editorconfig") {
            writeText(editorConfig)
        }
        projectRoot.resolve("build.gradle") {
            // language=groovy
            val buildScript =
                """
                import io.github.usefulness.tasks.FormatTask
    
                task customizedFormatTask(type: FormatTask) {
                    source files('src')
                    experimentalRules = true
                    disabledRules = ["final-newline"]
                }
                
                """.trimIndent()
            appendText(buildScript)
        }

        build("customizedFormatTask").apply {
            assertThat(task(":customizedFormatTask")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        }
    }

    @Test
    fun `ktLint custom task skips reports generation if reports not configured`() {
        projectRoot.resolve("src/main/kotlin/MissingNewLine.kt") {
            // language=kotlin
            val validClass =
                """
                class MissingNewLine
                """.trimIndent()
            writeText(validClass)
        }
        projectRoot.resolve("build.gradle") {
            // language=groovy
            val buildScript =
                """
                import io.github.usefulness.tasks.LintTask
    
                task reportsNotConfigured(type: LintTask) {
                    source files('src')
                }
                
                task reportsEmpty(type: LintTask) {
                    source files('src')
                    reports = [:]
                }
                
                """
            appendText(buildScript)
        }

        buildAndFail("reportsEmpty").apply {
            assertThat(task(":reportsEmpty")?.outcome).isEqualTo(TaskOutcome.FAILED)
            assertThat(output).contains("[final-newline] File must end with a newline (\\n)")
            assertThat(projectRoot.resolve("build/reports/ktlint")).doesNotExist()
        }
        buildAndFail("reportsNotConfigured").apply {
            assertThat(task(":reportsNotConfigured")?.outcome).isEqualTo(TaskOutcome.FAILED)
            assertThat(output).contains("[final-newline] File must end with a newline (\\n)")
            assertThat(projectRoot.resolve("build/reports/ktlint")).doesNotExist()
        }
    }

    @Test
    fun `ktLint custom task became up-to-date on second run if reports not configured`() {
        projectRoot.resolve("build.gradle") {
            // language=groovy
            val buildScript =
                """
                import io.github.usefulness.tasks.LintTask
    
                task reportsNotConfigured(type: LintTask) {
                    source files('src')
                }
                
                task reportsEmpty(type: LintTask) {
                    source files('src')
                    reports = [:]
                }
                
                """
            appendText(buildScript)
        }

        build("reportsEmpty").apply {
            assertThat(task(":reportsEmpty")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        }
        build("reportsEmpty").apply {
            assertThat(TaskOutcome.UP_TO_DATE).isEqualTo(task(":reportsEmpty")?.outcome)
        }
        build("reportsNotConfigured").apply {
            assertThat(task(":reportsNotConfigured")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        }
        build("reportsNotConfigured").apply {
            assertThat(TaskOutcome.UP_TO_DATE).isEqualTo(task(":reportsNotConfigured")?.outcome)
        }
    }

    @Test
    @DisabledOnOs(OS.WINDOWS, disabledReason = "https://github.com/gradle/gradle/issues/21964")
    fun `ktLint custom task treats reports as input parameter`() {
        projectRoot.resolve("build.gradle") {
            // language=groovy
            val buildScript =
                """
                import io.github.usefulness.tasks.LintTask
    
                task ktLintWithReports(type: LintTask) {
                    source files('src')
                    reports = ['plain': file('build/lint-report.txt')]
                }
                
                """
            appendText(buildScript)
        }

        build("ktLintWithReports").apply {
            assertThat(task(":ktLintWithReports")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(projectRoot.resolve("build/lint-report.txt")).isEmpty()
        }
        build("ktLintWithReports").apply {
            assertThat(TaskOutcome.UP_TO_DATE).isEqualTo(task(":ktLintWithReports")?.outcome)
        }

        projectRoot.resolve("build/lint-report.txt").appendText("CHANGED REPORT FILE")

        build("ktLintWithReports").apply {
            assertThat(task(":ktLintWithReports")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(projectRoot.resolve("build/lint-report.txt")).isEmpty()
        }
    }
}
