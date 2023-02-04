package io.github.usefulness.functional

import io.github.usefulness.functional.utils.kotlinClass
import io.github.usefulness.functional.utils.resolve
import io.github.usefulness.functional.utils.settingsFile
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

class KotlinJsProjectTest : WithGradleTest.Kotlin() {

    lateinit var projectRoot: File

    @BeforeEach
    fun setup() {
        projectRoot = testProjectDir.apply {
            resolve("settings.gradle") { writeText(settingsFile) }
            resolve("build.gradle") {
                // language=groovy
                writeText(
                    """
                    plugins {
                        id 'org.jetbrains.kotlin.js'
                        id 'io.github.usefulness.ktlint-gradle-plugin'
                    }
                    
                    repositories.mavenCentral()
                    
                    kotlin {
                        js(IR) {
                            browser()
                            binaries.executable()
                        }
                    }
                    """.trimIndent(),
                )
            }
        }
    }

    @Test
    fun `lintKotlin passes when on valid kotlin files`() {
        projectRoot.resolve("src/main/kotlin/FixtureFileName.kt") {
            writeText(kotlinClass("FixtureFileName"))
        }
        projectRoot.resolve("src/test/kotlin/TestFileName.kt") {
            writeText(kotlinClass("TestFileName"))
        }

        build("lintKotlin").apply {
            assertThat(task(":lintKotlinMainReporter")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(task(":lintKotlinTestReporter")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        }
    }

    @Test
    fun `lintKotlin fails when lint errors detected`() {
        projectRoot.resolve("src/main/kotlin/FixtureFileName.kt") {
            writeText(kotlinClass("DifferentClassName"))
        }
        projectRoot.resolve("src/test/kotlin/FixtureTestFileName.kt") {
            writeText(kotlinClass("DifferentTestClassName"))
        }

        buildAndFail("lintKotlin", "--continue").apply {
            assertThat(task(":lintKotlinMainReporter")?.outcome).isEqualTo(TaskOutcome.FAILED)
            assertThat(output).contains("Lint error > [filename] File 'FixtureFileName.kt' contains a single top level declaration")
            assertThat(task(":lintKotlinTestReporter")?.outcome).isEqualTo(TaskOutcome.FAILED)
            assertThat(output).contains("Lint error > [filename] File 'FixtureTestFileName.kt' contains a single top level declaration")
        }
    }

    @Test
    fun `formatKotlin reports formatted and unformatted files`() {
        projectRoot.resolve("src/main/kotlin/FixtureClass.kt") {
            // language=kotlin
            val kotlinClass =
                """
                import System.*
                
                class FixtureClass{
                    private fun hi() {
                        out.println("Hello")
                    }
                }
                """.trimIndent()
            writeText(kotlinClass)
        }
        projectRoot.resolve("src/test/kotlin/FixtureTestClass.kt") {
            // language=kotlin
            val kotlinClass =
                """
                import System.*
                
                class FixtureTestClass{
                    private fun hi() {
                        out.println("Hello")
                    }
                }
                """.trimIndent()
            writeText(kotlinClass)
        }
        build("formatKotlin").apply {
            assertThat(task(":formatKotlinMainReporter")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(output).contains("FixtureClass.kt:3:19: Format fixed > [curly-spacing] Missing spacing before \"{\"")
            assertThat(output).contains("FixtureClass.kt:1:1: Format could not fix > [no-wildcard-imports] Wildcard import")
            assertThat(task(":formatKotlinTestReporter")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(output).contains("FixtureTestClass.kt:3:23: Format fixed > [curly-spacing] Missing spacing before \"{\"")
            assertThat(output).contains("FixtureTestClass.kt:1:1: Format could not fix > [no-wildcard-imports] Wildcard import")
        }
    }
}
