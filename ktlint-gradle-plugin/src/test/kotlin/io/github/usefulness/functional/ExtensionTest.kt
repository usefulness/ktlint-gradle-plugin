package io.github.usefulness.functional

import org.gradle.testkit.runner.TaskOutcome
import io.github.usefulness.functional.utils.kotlinClass
import io.github.usefulness.functional.utils.resolve
import io.github.usefulness.functional.utils.settingsFile
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

internal class ExtensionTest : WithGradleTest.Kotlin() {

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
    }

    @Test
    fun `extension configures ignoreFailures for 'lintKotlin'`() {
        projectRoot.resolve("src/main/kotlin/SomeClass.kt") {
            writeText(
                """
                data class SomeClass(val value : String)
                
                """.trimIndent(),
            )
        }
        projectRoot.resolve("src/main/kotlin/FileName.kt") {
            writeText(kotlinClass("DifferentClassName"))
        }

        projectRoot.resolve("build.gradle") {
            // language=groovy
            val script =
                """
                ktlint {
                    ignoreFailures = true
                }
                
                """.trimIndent()
            appendText(script)
        }

        build("lintKotlin").apply {
            assertThat(task(":lintKotlinMain")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        }

        projectRoot.resolve("build.gradle") {
            // language=groovy
            val script =
                """
                import io.github.usefulness.tasks.FormatTask 
                
                tasks.withType(FormatTask).configureEach {
                    ignoreFailures = false
                }
                
                """.trimIndent()
            appendText(script)
        }

        buildAndFail("formatKotlin").apply {
            assertThat(task(":formatKotlinMain")?.outcome).isEqualTo(TaskOutcome.FAILED)
            assertThat(output).contains("FileName.kt:1:1: Format could not fix > [standard:filename]")
            assertThat(output).contains("SomeClass.kt:1:32: Format fixed > [standard:colon-spacing]")
        }

        projectRoot.resolve("src/main/kotlin/FileName.kt") {
            writeText(
                """
                data class FileName(val value : String)
                
                """.trimIndent(),
            )
        }

        build("formatKotlin").apply {
            assertThat(task(":formatKotlinMain")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(output).contains("FileName.kt:1:31: Format fixed > [standard:colon-spacing]")
        }
    }

    @Test
    fun `extension configures reporters`() {
        projectRoot.resolve("build.gradle") {
            // language=groovy
            val script =
                """
                ktlint {
                    reporters = ['html'] 
                }
                """.trimIndent()
            appendText(script)
        }
        projectRoot.resolve("src/main/kotlin/KotlinClass.kt") {
            writeText(kotlinClass("KotlinClass"))
        }

        build("lintKotlin").apply {
            assertThat(task(":lintKotlinMain")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        }
        val report = projectRoot.resolve("build/reports/ktlint/main-lint.html")
        assertThat(report).isNotEmpty()
    }

    @Test
    fun `extension configures disabledRules`() {
        projectRoot.resolve("build.gradle") {
            // language=groovy
            val script =
                """
                ktlint {
                    experimentalRules = true
                    disabledRules = ["filename", "standard:unnecessary-parentheses-before-trailing-lambda"]
                }
                """.trimIndent()
            appendText(script)
        }
        projectRoot.resolve("src/main/kotlin/FileName.kt") {
            writeText(kotlinClass("DifferentClassName"))
        }
        projectRoot.resolve("src/main/kotlin/UnnecessaryParentheses.kt") {
            writeText(
                """
                val FAILING = "should not have '()'".count() { it == 'x' }

                """.trimIndent(),
            )
        }

        build("lintKotlin").apply {
            assertThat(task(":lintKotlinMain")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        }
    }

    @Test
    fun `extension properties are evaluated only during task execution`() {
        projectRoot.resolve("build.gradle") {
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
                
                tasks.whenTaskAdded {
                    // configure all tasks eagerly
                }
                
                ktlint {
                    disabledRules = ["filename"]
                }
                
                """.trimIndent()
            writeText(buildScript)
        }
        projectRoot.resolve("src/main/kotlin/FileName.kt") {
            writeText(kotlinClass("DifferentClassName"))
        }

        build("lintKotlin").apply {
            assertThat(task(":lintKotlinMain")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        }
    }

    @Test
    fun `can override ktlint version`() {
        projectRoot.resolve("build.gradle") {
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
                
                ktlint {
                    ktlintVersion = "0.32.0"
                }
                
                """.trimIndent()
            writeText(buildScript)
        }
        projectRoot.resolve("src/main/kotlin/FileName.kt") {
            writeText(kotlinClass("FileName"))
        }

        buildAndFail("lintKotlin").apply {
            assertThat(task(":lintKotlinMain")?.outcome).isEqualTo(TaskOutcome.FAILED)
            val expectedMessage = "ClassNotFoundException: com.pinterest.ktlint.cli.ruleset.core.api.RuleSetProviderV3"
            assertThat(output).contains(expectedMessage)
        }
        build("dependencies", "--configuration", "ktlint").apply {
            assertThat(output).contains("com.pinterest:ktlint:0.32.0")
        }
    }

    @Test
    fun `readme doc contains valid groovy code`() {
        projectRoot.resolve("build.gradle") {
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
                
                ktlint {
                    ignoreFailures = false
                    reporters = ["checkstyle", "html", "json", "plain", "sarif"]
                    experimentalRules = true
                    disabledRules = ["no-wildcard-imports", "experimental:annotation", "your-custom-rule:no-bugs"]
                    ktlintVersion = "0.49.0"
                    chunkSize = 50
                    baselineFile.set(file("config/ktlint_baseline.xml"))
                }
                
                """.trimIndent()
            writeText(buildScript)
        }
        projectRoot.resolve("src/main/kotlin/FileName.kt") {
            writeText(kotlinClass("FileName"))
        }

        build("lintKotlin", "--dry-run").apply {
            assertThat(output).contains(":lintKotlin SKIPPED")
        }
    }
}
