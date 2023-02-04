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
    fun `extension configures ignoreFailures`() {
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
        projectRoot.resolve("src/main/kotlin/FileName.kt") {
            writeText(kotlinClass("DifferentClassName"))
        }

        build("lintKotlin").apply {
            assertThat(task(":lintKotlinMainReporter")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
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
            assertThat(task(":lintKotlinMainReporter")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
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
                    disabledRules = ["filename", "experimental:unnecessary-parentheses-before-trailing-lambda"]
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
            assertThat(task(":lintKotlinMainReporter")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
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
            assertThat(task(":lintKotlinMainReporter")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
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
                    ktlintVersion = "0.46.0"
                }
                
                """.trimIndent()
            writeText(buildScript)
        }
        projectRoot.resolve("src/main/kotlin/FileName.kt") {
            writeText(kotlinClass("FileName"))
        }

        buildAndFail("lintKotlin").apply {
            assertThat(task(":lintKotlinMainWorker")?.outcome).isEqualTo(TaskOutcome.FAILED)
            val expectedMessage = "EditorConfigOverride com.pinterest.ktlint.core.api.EditorConfigOverride" +
                "${"$"}Companion.getEMPTY_EDITOR_CONFIG_OVERRIDE()'"
            assertThat(output).contains(expectedMessage)
        }
        build("dependencies", "--configuration", "ktlint").apply {
            assertThat(output).contains("com.pinterest:ktlint:0.46.0")
        }
    }
}
