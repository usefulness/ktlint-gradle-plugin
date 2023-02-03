package io.github.usefulness.functional

import io.github.usefulness.functional.utils.editorConfig
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

internal class KotlinProjectTest : WithGradleTest.Kotlin() {

    private lateinit var settingsFile: File
    private lateinit var buildFile: File
    private lateinit var sourceDir: File
    private lateinit var editorconfigFile: File
    private val pathPattern = "(.*\\.kt):\\d+:\\d+".toRegex()

    @BeforeEach
    fun setup() {
        settingsFile = testProjectDir.resolve("settings.gradle")
        buildFile = testProjectDir.resolve("build.gradle")
        sourceDir = testProjectDir.resolve("src/main/kotlin/").also(File::mkdirs)
        editorconfigFile = testProjectDir.resolve(".editorconfig")
    }

    @Test
    fun `lintKotlinMain fails when lint errors detected`() {
        settingsFile()
        buildFile()

        val className = "KotlinClass"
        kotlinSourceFile(
            "$className.kt",
            """
            class $className {
                private fun hi(){
                    println ("hi")
                }
            }

            """.trimIndent(),
        )

        buildAndFail("lintKotlinMain").apply {
            assertThat(output).containsPattern(".*$className.kt.* Lint error > \\[.*] Missing spacing before \"\\{\"".toPattern())
            assertThat(output).containsPattern(".*$className.kt.* Lint error > \\[.*] Unexpected spacing before \"\\(\"".toPattern())
            output.lines().filter { it.contains("Lint error") }.forEach { line ->
                val filePath = pathPattern.find(line)?.groups?.get(1)?.value.orEmpty()
                assertThat(File(filePath)).exists()
            }
            assertThat(task(":lintKotlinMain")?.outcome).isEqualTo(TaskOutcome.FAILED)
        }
    }

    @Test
    fun `lintKotlinMain fails when lint errors for experimental rules are detected`() {
        settingsFile()
        buildFile()

        buildFile.appendText(
            """
            
            ktlint {
                experimentalRules = true
            }
            """.trimIndent(),
        )

        fileWithFailingExperimentalRule()

        buildAndFail("lintKotlinMain").apply {
            assertThat(output).containsPattern(".*Lint error > \\[experimental:unnecessary-parentheses".toPattern())
            output.lines().filter { it.contains("Lint error") }.forEach { line ->
                val filePath = pathPattern.find(line)?.groups?.get(1)?.value.orEmpty()
                assertThat(File(filePath)).exists()
            }
            assertThat(task(":lintKotlinMain")?.outcome).isEqualTo(TaskOutcome.FAILED)
        }
    }

    @Test
    fun `lintKotlinMain succeeds when no lint errors detected`() {
        settingsFile()
        buildFile()
        kotlinSourceFile(
            "KotlinClass.kt",
            """
            class KotlinClass {
                private fun hi() {
                    println("hi")
                }
            }

            """.trimIndent(),
        )

        build("lintKotlinMain").apply {
            assertThat(task(":lintKotlinMain")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        }
    }

    @Test
    fun `lintKotlinMain succeeds when experimental rules are not enabled and code contains experimental rules violations`() {
        settingsFile()
        buildFile()

        fileWithFailingExperimentalRule()

        build("lintKotlinMain").apply {
            assertThat(task(":lintKotlinMain")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        }
    }

    @Test
    fun `formatKotlin reports formatted and unformatted files`() {
        settingsFile()
        buildFile()
        // language=kotlin
        val kotlinClass =
            """
            import System.*
            
            class KotlinClass{
                private fun hi() {
                    out.println("Hello")
                }
            }
            """.trimIndent()
        kotlinSourceFile("KotlinClass.kt", kotlinClass)

        build("formatKotlin").apply {
            assertThat(task(":formatKotlinMain")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            output.lines().filter { it.contains("Format could not fix") }.forEach { line ->
                val filePath = pathPattern.find(line)?.groups?.get(1)?.value.orEmpty()
                assertThat(File(filePath)).exists()
            }
            assertThat(output).contains("Format failed to autocorrect")
        }
    }

    @Test
    fun `check task runs lintFormat`() {
        settingsFile()
        buildFile()
        kotlinSourceFile(
            "CustomObject.kt",
            """
            object CustomObject
            
            """.trimIndent(),
        )

        build("check").apply {
            assertThat(task(":lintKotlin")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        }
    }

    @Test
    fun `tasks up-to-date checks`() {
        settingsFile()
        buildFile()
        editorConfig()
        kotlinSourceFile(
            "CustomObject.kt",
            """
            object CustomObject
            
            """.trimIndent(),
        )

        build("lintKotlin").apply {
            assertThat(task(":lintKotlin")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        }
        build("lintKotlin").apply {
            assertThat(task(":lintKotlin")?.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
        }

        build("formatKotlin").apply {
            assertThat(task(":formatKotlin")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        }
        build("formatKotlin").apply {
            assertThat(task(":formatKotlin")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        }

        editorconfigFile.appendText("content=updated")
        build("lintKotlin").apply {
            assertThat(task(":lintKotlin")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        }
        build("lintKotlin").apply {
            assertThat(task(":lintKotlin")?.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
        }
    }

    @Test
    fun `lint task is incremental`() {
        settingsFile()
        buildFile()
        editorConfig()
        kotlinSourceFile(
            "CustomObject.kt",
            """
            object CustomObject
            
            """.trimIndent(),
        )
        kotlinSourceFile(
            "CustomClass.kt",
            """
            data class CustomClass(val value: Int)
            
            """.trimIndent(),
        )

        build("lintKotlin", "--info").apply {
            assertThat(task(":lintKotlin")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(task(":lintKotlinMainWorker")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(task(":lintKotlinMain")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(task(":lintKotlinTestWorker")?.outcome).isEqualTo(TaskOutcome.NO_SOURCE)
            assertThat(task(":lintKotlinTest")?.outcome).isEqualTo(TaskOutcome.NO_SOURCE)
            assertThat(output).contains("lintKotlinMainWorker - executing against 2 file(s)")
        }

        kotlinSourceFile(
            "CustomClass.kt",
            """
            data class CustomClass(val modified: Int)
            
            """.trimIndent(),
        )
        build("lintKotlin", "--info").apply {
            assertThat(task(":lintKotlin")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(task(":lintKotlinMainWorker")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(task(":lintKotlinMain")?.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
            assertThat(task(":lintKotlinTestWorker")?.outcome).isEqualTo(TaskOutcome.NO_SOURCE)
            assertThat(task(":lintKotlinTest")?.outcome).isEqualTo(TaskOutcome.NO_SOURCE)
            assertThat(output).contains("lintKotlinMainWorker - executing against 1 file(s)")
        }

        editorconfigFile.appendText("content=updated")
        build("lintKotlin", "--info").apply {
            assertThat(task(":lintKotlinMainWorker")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(task(":lintKotlinMain")?.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
            assertThat(output).contains("lintKotlinMainWorker - executing against 2 file(s)")
        }

        kotlinSourceFile(
            "CustomClass.kt",
            """
            data class CustomClass(val modifiedEditorconfig: Int)
            
            """.trimIndent(),
        )
        build("lintKotlin", "--info").apply {
            assertThat(task(":lintKotlinMainWorker")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(task(":lintKotlinMain")?.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
            assertThat(output).contains("lintKotlinMainWorker - executing against 1 file(s)")
        }

        kotlinSourceFile(
            "WrongFilename.kt",
            """
            data class AnotherCustomClass(val modifiedEditorconfig: Int)
            
            """.trimIndent(),
        )
        buildAndFail("lintKotlin", "--info").apply {
            assertThat(task(":lintKotlinMainWorker")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(task(":lintKotlinMain")?.outcome).isEqualTo(TaskOutcome.FAILED)
            assertThat(output).contains("lintKotlinMainWorker - executing against 1 file(s)")
        }
    }

    @Test
    fun `plugin is compatible with configuration cache`() {
        settingsFile()
        buildFile()
        kotlinSourceFile(
            "CustomObject.kt",
            """
            object CustomObject
            
            """.trimIndent(),
        )

        build("lintKotlin", "--configuration-cache").apply {
            assertThat(task(":lintKotlin")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(output).contains("Configuration cache entry stored")
        }
        build("lintKotlin", "--configuration-cache").apply {
            assertThat(task(":lintKotlin")?.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
            assertThat(output).contains("Configuration cache entry reused.")
        }

        build("formatKotlin", "--configuration-cache").apply {
            assertThat(task(":formatKotlin")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(output).contains("Configuration cache entry stored")
        }
        build("formatKotlin", "--configuration-cache").apply {
            assertThat(task(":formatKotlin")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(output).contains("Configuration cache entry reused.")
        }
    }

    @Test
    fun `plugin resolves dynamically loaded RuleSetProviders`() {
        settingsFile()
        buildFile()
        editorConfig()
        kotlinSourceFile(
            "CustomObject.kt",
            """
            object CustomObject
            
            """.trimIndent(),
        )

        fun String.findResolvedRuleProvidersCount(taskName: String): Int {
            val matcher = "$taskName - resolved (\\d+) RuleProviders".toRegex()

            return matcher.find(this)?.groups?.get(1)?.value?.toIntOrNull() ?: 0
        }

        build("lintKotlin", "--info").apply {
            assertThat(task(":lintKotlinMain")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(task(":lintKotlinMainWorker")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            val resolvedRulesCount = output.findResolvedRuleProvidersCount("lintKotlinMainWorker")
            assertThat(resolvedRulesCount).isGreaterThan(0)
        }

        build("formatKotlin", "--info").apply {
            assertThat(task(":formatKotlin")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            val resolvedRulesCount = output.findResolvedRuleProvidersCount("formatKotlinMain")
            assertThat(resolvedRulesCount).isGreaterThan(0)
        }
    }

    private fun settingsFile() = settingsFile.apply {
        writeText("rootProject.name = 'ktlint-gradle-test-project'")
    }

    private fun editorConfig() = editorconfigFile.apply {
        writeText(editorConfig)
    }

    private fun buildFile() = buildFile.apply {
        // language=groovy
        val buildscript =
            """
            plugins {
                id 'org.jetbrains.kotlin.jvm'
                id 'io.github.usefulness.ktlint-gradle-plugin'
            }

            repositories {
                mavenCentral()
            }
            """.trimIndent()
        writeText(buildscript)
    }

    private fun kotlinSourceFile(name: String, content: String) = File(sourceDir, name).apply {
        writeText(content)
    }

    private fun fileWithFailingExperimentalRule() {
        kotlinSourceFile(
            "ExperimentalRuleViolations.kt",
            """
            val variable = "should not contain '()'".count() { it == 'x' }
    
            """.trimIndent(),
        )
    }
}
