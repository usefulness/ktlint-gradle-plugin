package io.github.usefulness.functional

import org.gradle.testkit.runner.TaskOutcome
import io.github.usefulness.functional.utils.editorConfig
import io.github.usefulness.functional.utils.kotlinClass
import io.github.usefulness.functional.utils.resolve
import io.github.usefulness.functional.utils.settingsFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
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
                """ |
                    |object FourSpacesByDefault {
                    |    val text: String
                    |}
                    |
                """.trimMargin(),
            )
        }

        build("lintKotlin").apply {
            assertEquals(TaskOutcome.SUCCESS, task(":lintKotlinMain")?.outcome)
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
            assertEquals(TaskOutcome.SUCCESS, task(":lintKotlinMain")?.outcome)
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
            assertEquals(TaskOutcome.FAILED, task(":lintKotlinMain")?.outcome)
            assertTrue(output.contains("[indent] Unexpected indentation (2) (should be 6)"))
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
            assertEquals(TaskOutcome.SUCCESS, task(":lintKotlinMain")?.outcome)
            assertFalse(output.contains("resetting KtLint caches"))
        }

        projectRoot.resolve(".editorconfig") {
            writeText(editorConfig)
        }
        buildAndFail("lintKotlin", "--info").apply {
            assertEquals(TaskOutcome.FAILED, task(":lintKotlinMain")?.outcome)
            assertTrue(output.contains("[filename] File 'FileName.kt' contains a single top level declaration"))
            assertTrue(output.contains("resetting KtLint caches"))
        }

        projectRoot.resolve("src/main/kotlin/FileName.kt") {
            writeText(kotlinClass("FileName"))
        }
        build("lintKotlin").apply {
            assertEquals(TaskOutcome.SUCCESS, task(":lintKotlinMain")?.outcome)
            assertFalse(output.contains("resetting KtLint caches"))
        }
        build("lintKotlin").apply {
            assertEquals(TaskOutcome.UP_TO_DATE, task(":lintKotlinMain")?.outcome)
            assertFalse(output.contains("resetting KtLint caches"))
        }
    }

    @Test
    fun `editorconfig changes do not clear ktlint caches for format task re-runs`() {
        projectRoot.resolve(".editorconfig") {
            writeText(editorConfig)
        }

        projectRoot.resolve("src/main/kotlin/FileName.kt") {
            writeText(kotlinClass("DifferentClassName"))
        }
        build("formatKotlin").apply {
            assertEquals(TaskOutcome.SUCCESS, task(":formatKotlinMain")?.outcome)
            assertTrue(output.contains("Format could not fix > [filename] File 'FileName.kt' contains a single top level declaration"))
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
            assertEquals(TaskOutcome.SUCCESS, task(":formatKotlinMain")?.outcome)
            assertFalse(output.contains("Format could not fix"))
            assertFalse(output.contains("resetting KtLint caches"))
        }
    }
}