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

internal class EditorConfigTest : WithGradleTest.Kotlin() {

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
    fun `lintTask uses default indentation if editorconfig absent`() {
        projectRoot.resolve("src/main/kotlin/FourSpacesByDefault.kt") {
            writeText(
                """ |object FourSpacesByDefault {
                    |    val text: String
                    |}
                    |
                """.trimMargin(),
            )
        }

        build("lintKotlin").apply {
            assertThat(task(":lintKotlinMain")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        }
    }

    @Test
    fun `plugin respects disabled_rules set in editorconfig`() {
        projectRoot.resolve(".editorconfig") {
            appendText(
                // language=editorconfig
                """
                    [*.{kt,kts}]
                    ktlint_standard_filename = disabled
                """.trimIndent(),
            )
        }
        projectRoot.resolve("src/main/kotlin/FileName.kt") {
            writeText(kotlinClass("DifferentClassName"))
        }

        build("lintKotlin").apply {
            assertThat(task(":lintKotlinMain")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        }
    }

    @Test
    fun `plugin respects 'indent_size' set in editorconfig`() {
        projectRoot.resolve(".editorconfig") {
            appendText(
                // language=editorconfig
                """
                    [*.{kt,kts}]
                    indent_size = 6
                """.trimIndent(),
            )
        }
        projectRoot.resolve("src/main/kotlin/FileName.kt") {
            // language=kotlin
            val content =
                """
                class WrongFileName {

                  fun unnecessarySpace () = 2
                }

                """.trimIndent()

            writeText(content)
        }
        buildAndFail("lintKotlin").apply {
            assertThat(task(":lintKotlinMain")?.outcome).isEqualTo(TaskOutcome.FAILED)
            assertThat(output).contains("[standard:indent] Unexpected indentation (2) (should be 6)")
        }

        projectRoot.resolve(".editorconfig") {
            appendText(
                // language=editorconfig
                """
                    [*.{kt,kts}]
                    indent_size = 2
                """.trimIndent(),
            )
        }
        buildAndFail("lintKotlin").apply {
            assertThat(task(":lintKotlinMain")?.outcome).isEqualTo(TaskOutcome.FAILED)
            assertThat(output).doesNotContain("[standard:indent] Unexpected indentation (2) (should be 6)")
        }
    }

    @Test
    @DisabledOnOs(OS.WINDOWS, disabledReason = "https://github.com/gradle/gradle/issues/21964")
    fun `editorconfig changes are taken into account on lint task re-runs`() {
        projectRoot.resolve(".editorconfig") {
            writeText(
                // language=editorconfig
                """
                    [*.{kt,kts}]
                    ktlint_standard_filename = disabled
                """.trimIndent(),
            )
        }
        projectRoot.resolve("src/main/kotlin/FileName.kt") {
            writeText(kotlinClass("DifferentClassName"))
        }
        build("lintKotlin").apply {
            assertThat(task(":lintKotlinMain")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(output).doesNotContain("resetting KtLint caches")
        }

        projectRoot.resolve(".editorconfig") {
            writeText(editorConfig)
        }
        buildAndFail("lintKotlin", "--info").apply {
            assertThat(task(":lintKotlinMain")?.outcome).isEqualTo(TaskOutcome.FAILED)
            assertThat(output).contains("[standard:filename] File 'FileName.kt' contains a single top level declaration")
            assertThat(output).contains("resetting KtLint caches")
        }

        projectRoot.resolve("src/main/kotlin/FileName.kt") {
            writeText(kotlinClass("FileName"))
        }
        build("lintKotlin").apply {
            assertThat(task(":lintKotlinMain")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(output).doesNotContain("resetting KtLint caches")
        }
        build("lintKotlin").apply {
            assertThat(task(":lintKotlinMain")?.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
            assertThat(output).doesNotContain("resetting KtLint caches")
        }
    }

    @Test
    fun `editorconfig changes clear ktlint caches on format task re-runs`() {
        projectRoot.resolve(".editorconfig") {
            writeText(editorConfig)
        }

        projectRoot.resolve("src/main/kotlin/FileName.kt") {
            writeText(kotlinClass("DifferentClassName"))
        }
        build("formatKotlin").apply {
            assertThat(task(":formatKotlinMain")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(output).contains(
                "Format could not fix > [standard:filename] File 'FileName.kt' contains a single top level declaration",
            )
        }

        projectRoot.resolve(".editorconfig") {
            writeText(
                // language=editorconfig
                """
                    [*.{kt,kts}]
                    ktlint_standard_filename = disabled
                """.trimIndent(),
            )
        }
        build("formatKotlin", "--info").apply {
            assertThat(task(":formatKotlinMain")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(output).doesNotContain("Format could not fix")
            assertThat(output).contains("resetting KtLint caches")
        }

        projectRoot.resolve(".editorconfig") {
            writeText(
                // language=editorconfig
                """
                    [*.{kt,kts}]
                    indent_size = 2
                """.trimIndent(),
            )
        }
        build("formatKotlin", "--info").apply {
            assertThat(task(":formatKotlinMain")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(output).contains("Format could not fix")
            assertThat(output).contains("resetting KtLint caches")
        }
    }
}
