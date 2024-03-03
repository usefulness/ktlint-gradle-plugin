package io.github.usefulness.functional

import org.gradle.testkit.runner.TaskOutcome
import io.github.usefulness.functional.utils.editorConfig
import io.github.usefulness.functional.utils.resolve
import io.github.usefulness.functional.utils.settingsFile
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

internal class EditorConfigValidationTest : WithGradleTest.Kotlin() {

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
            resolve("src/main/kotlin/Foo.kt") {
                // language=kotlin
                val content =
                    """
                object Foo {
                    fun bar() = 2
                }

                    """.trimIndent()

                writeText(content)
            }
        }
    }

    @Test
    fun `lint - valid pass and cacheability`() {
        projectRoot.resolve(".editorconfig") {
            writeText(editorConfig)
        }

        build("lintKotlin").apply {
            assertThat(task(":validateEditorConfigForKtlint")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(task(":lintKotlinMain")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(output).doesNotContain(failureMessage)
        }

        build("lintKotlin").apply {
            assertThat(task(":validateEditorConfigForKtlint")?.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
            assertThat(task(":lintKotlinMain")?.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
            assertThat(output).doesNotContain(failureMessage)
        }

        projectRoot.resolve(".editorconfig") {
            writeText(
                """
                root = true
                """.trimIndent(),
            )
        }
        build("lintKotlin").apply {
            assertThat(task(":validateEditorConfigForKtlint")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(task(":lintKotlinMain")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(output).doesNotContain(failureMessage)
            assertThat(output).contains("Reusing configuration cache.")
        }
    }

    @Test
    fun `format - valid pass and cacheability`() {
        projectRoot.resolve(".editorconfig") {
            writeText(editorConfig)
        }

        build("formatKotlin").apply {
            assertThat(task(":validateEditorConfigForKtlint")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(task(":formatKotlinMain")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(output).doesNotContain(failureMessage)
        }

        build("formatKotlin").apply {
            assertThat(task(":validateEditorConfigForKtlint")?.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
            assertThat(task(":formatKotlinMain")?.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
            assertThat(output).doesNotContain(failureMessage)
        }
    }

    @Test
    fun `default validation`() {
        projectRoot.resolve(".editorconfig") {
            writeText(invalidEditorConfig)
        }
        build("lintKotlin").apply {
            assertThat(task(":lintKotlinMain")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(task(":validateEditorConfigForKtlint")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(output).contains(failureMessage)
        }
    }

    @Test
    fun `disabled validation`() {
        projectRoot.resolve("build.gradle") {
            // language=groovy
            appendText(
                """
                
                ktlint {
                    editorConfigValidation "None"
                }
                """.trimIndent(),
            )
        }

        build("lintKotlin").apply {
            assertThat(task(":lintKotlinMain")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(task(":validateEditorConfigForKtlint")).isNull()
            assertThat(output).doesNotContain(failureMessage)
        }

        projectRoot.resolve("build.gradle") {
            // language=groovy
            appendText(
                """
                
                tasks.named("validateEditorConfigForKtlint") {
                    mode = io.github.usefulness.EditorConfigValidationMode.BuildFailure
                }
                """.trimIndent(),
            )
        }

        buildAndFail("validateEditorConfigForKtlint").apply {
            assertThat(task(":validateEditorConfigForKtlint")?.outcome).isEqualTo(TaskOutcome.FAILED)
            assertThat(output).contains(failureMessage)
        }
        build("lintKotlin").apply {
            assertThat(task(":lintKotlinMain")?.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
            assertThat(task(":validateEditorConfigForKtlint")).isNull()
            assertThat(output).doesNotContain(failureMessage)
        }
    }

    @Test
    fun `validation with warnings`() {
        projectRoot.resolve("build.gradle") {
            // language=groovy
            appendText(
                """
                
                ktlint {
                    editorConfigValidation "PrintWarningLogs"
                }
                """.trimIndent(),
            )
        }
        build("lintKotlin").apply {
            assertThat(task(":lintKotlinMain")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(task(":validateEditorConfigForKtlint")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(output).contains(failureMessage)
        }

        projectRoot.resolve(".editorconfig") {
            writeText(editorConfig)
        }
        build("lintKotlin").apply {
            assertThat(task(":lintKotlinMain")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(task(":validateEditorConfigForKtlint")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(output).doesNotContain(failureMessage)
        }
    }

    @Test
    fun `validation with errors`() {
        projectRoot.resolve("build.gradle") {
            // language=groovy
            appendText(
                """
                
                ktlint {
                    editorConfigValidation "BuildFailure"
                }
                """.trimIndent(),
            )
        }
        buildAndFail("lintKotlin").apply {
            assertThat(task(":lintKotlinMain")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(task(":validateEditorConfigForKtlint")?.outcome).isEqualTo(TaskOutcome.FAILED)
            assertThat(output).contains(failureMessage)
        }

        projectRoot.resolve(".editorconfig") {
            writeText(editorConfig)
        }
        build("lintKotlin").apply {
            assertThat(task(":lintKotlinMain")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(task(":validateEditorConfigForKtlint")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(output).doesNotContain(failureMessage)
        }
    }

    private val failureMessage = "None of the recognised `.editorconfig` files contain `root=true` entry"

    private val invalidEditorConfig =
        """
    [*.kt]
    
        """.trimIndent()
}
