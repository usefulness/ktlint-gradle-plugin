package io.github.usefulness.functional

import io.github.usefulness.functional.utils.editorConfig
import org.assertj.core.api.Assertions.assertThat
import org.gradle.internal.impldep.org.bouncycastle.asn1.x500.style.RFC4519Style.name
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
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

        buildAndFail("lintKotlin").apply {
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

        buildAndFail("lintKotlin").apply {
            assertThat(output).containsPattern(".*Lint error > \\[standard:unnecessary-parentheses".toPattern())
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

        build("lintKotlin").apply {
            assertThat(task(":lintKotlinMain")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        }
    }

    @Test
    @Disabled("I'm not aware of any experimental rule")
    fun `lintKotlin succeeds when experimental rules are not enabled and code contains experimental rules violations`() {
        settingsFile()
        buildFile()

        fileWithFailingExperimentalRule()

        build("lintKotlin").apply {
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
            assertThat(output).contains("Format could not fix > [standard:no-wildcard-imports] Wildcard import (cannot be auto-corrected)")
            assertThat(output).contains("KotlinClass.kt:1:1: Format fixed > [standard:final-newline] File must end with a newline")
            assertThat(output).contains("KotlinClass.kt:3:18: Format fixed > [standard:curly-spacing] Missing spacing before \"{\"")

            // language=kotlin
            val expected =
                """
                import System.*
                
                class KotlinClass {
                    private fun hi() {
                        out.println("Hello")
                    }
                }

                """.trimIndent()
            assertThat(File(sourceDir, "KotlinClass.kt")).content().isEqualTo(expected)
        }

        build("formatKotlin").apply {
            assertThat(task(":formatKotlinMain")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(output).contains("Format could not fix > [standard:no-wildcard-imports] Wildcard import (cannot be auto-corrected)")
            assertThat(output).doesNotContain("Format failed to autocorrect")
            assertThat(output).doesNotContain("Format fixed")
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
            assertThat(task(":lintKotlinMain")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(task(":lintKotlin")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        }
        build("lintKotlin").apply {
            assertThat(task(":lintKotlinMain")?.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
            assertThat(task(":lintKotlin")?.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
        }

        build("formatKotlin").apply {
            assertThat(task(":formatKotlinMain")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(task(":formatKotlin")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        }
        build("formatKotlin").apply {
            assertThat(task(":formatKotlinMain")?.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
            assertThat(task(":formatKotlin")?.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
        }

        editorconfigFile.appendText("content=updated")
        build("lintKotlin").apply {
            assertThat(task(":lintKotlinMain")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(task(":lintKotlin")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        }
        build("lintKotlin").apply {
            assertThat(task(":lintKotlinMain")?.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
            assertThat(task(":lintKotlin")?.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
        }
        build("formatKotlin").apply {
            assertThat(task(":formatKotlinMain")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(task(":formatKotlin")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        }
        build("formatKotlin").apply {
            assertThat(task(":formatKotlinMain")?.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
            assertThat(task(":formatKotlin")?.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
        }

        kotlinSourceFile(
            "FileWithCorrectableOffence.kt",
            """
            fun hello()= "world"
            
            """.trimIndent(),
        )
        build("formatKotlin").apply {
            assertThat(task(":formatKotlinMain")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(task(":formatKotlin")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        }
        build("formatKotlin").apply {
            assertThat(task(":formatKotlinMain")?.outcome).isEqualTo(TaskOutcome.SUCCESS) // current flaw of format task
            assertThat(task(":formatKotlin")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        }
        build("formatKotlin").apply {
            assertThat(task(":formatKotlinMain")?.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
            assertThat(task(":formatKotlin")?.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
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
            data class CustomClass(
                val value: Int,
            )
            
            """.trimIndent(),
        )

        build("lintKotlin", "--info").apply {
            assertThat(task(":lintKotlin")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(task(":lintKotlinMain")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(task(":lintKotlinTest")?.outcome).isEqualTo(TaskOutcome.NO_SOURCE)
            assertThat(output).contains("lintKotlinMain - executing against 2 file(s)")
        }

        kotlinSourceFile(
            "CustomClass.kt",
            """
            data class CustomClass(
                val modified: Int,
            )
            
            """.trimIndent(),
        )
        build("lintKotlin", "--info").apply {
            assertThat(task(":lintKotlin")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(task(":lintKotlinMain")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(task(":lintKotlinTest")?.outcome).isEqualTo(TaskOutcome.NO_SOURCE)
            assertThat(output).contains("lintKotlinMain - executing against 1 file(s)")
        }

        editorconfigFile.appendText("content=updated")
        build("lintKotlin", "--info").apply {
            assertThat(task(":lintKotlinMain")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(output).contains("lintKotlinMain - executing against 2 file(s)")
        }

        kotlinSourceFile(
            "CustomClass.kt",
            """
            data class CustomClass(
                val modifiedEditorconfig: Int,
            )
            
            """.trimIndent(),
        )
        build("lintKotlin", "--info").apply {
            assertThat(task(":lintKotlinMain")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(output).contains("lintKotlinMain - executing against 1 file(s)")
        }

        kotlinSourceFile(
            "WrongFilename.kt",
            """
            data class AnotherCustomClass(
                val modifiedEditorconfig: Int,
            )
            
            """.trimIndent(),
        )
        buildAndFail("lintKotlin", "--info").apply {
            assertThat(task(":lintKotlinMain")?.outcome).isEqualTo(TaskOutcome.FAILED)
            assertThat(output).contains("lintKotlinMain - executing against 1 file(s)")
        }
    }

    @Test
    fun `format task is incremental`() {
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
            data class CustomClass(
                val value: Int,
            )
            
            """.trimIndent(),
        )

        build("formatKotlin", "--info").apply {
            assertThat(task(":formatKotlin")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(task(":formatKotlinMain")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(task(":formatKotlinTest")?.outcome).isEqualTo(TaskOutcome.NO_SOURCE)
            assertThat(output).contains("formatKotlinMain - executing against 2 file(s)")
        }

        kotlinSourceFile(
            "CustomClass.kt",
            """
            data class CustomClass(
                val modified: Int,
            )
            
            """.trimIndent(),
        )
        build("formatKotlin", "--info").apply {
            assertThat(task(":formatKotlin")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(task(":formatKotlinMain")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(task(":formatKotlinTest")?.outcome).isEqualTo(TaskOutcome.NO_SOURCE)
            assertThat(output).contains("formatKotlinMain - executing against 1 file(s)")
        }

        editorconfigFile.appendText("content=updated")
        build("formatKotlin", "--info").apply {
            assertThat(task(":formatKotlinMain")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(output).contains("formatKotlinMain - executing against 2 file(s)")
        }

        kotlinSourceFile(
            "CustomClass.kt",
            """
            data class CustomClass(
                val modifiedEditorconfig: Int,
            )
            
            """.trimIndent(),
        )
        build("formatKotlin", "--info").apply {
            assertThat(task(":formatKotlinMain")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(output).contains("formatKotlinMain - executing against 1 file(s)")
        }

        kotlinSourceFile(
            "WrongFilename.kt",
            """
            data class AnotherCustomClass(
                val modifiedEditorconfig: Int,
            )
            
            """.trimIndent(),
        )
        build("formatKotlin", "--info").apply {
            assertThat(task(":formatKotlinMain")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(output).contains("formatKotlinMain - executing against 1 file(s)")
        }
        build("formatKotlin", "--info").apply {
            assertThat(task(":formatKotlinMain")?.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
        }

        kotlinSourceFile(
            "WrongFilename.kt",
            """
                

            data class AnotherCustomClass(val modifiedEditorconfig: Int)
            
            """.trimIndent(),
        )
        build("formatKotlin", "--info").apply {
            assertThat(task(":formatKotlinMain")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(output).contains("formatKotlinMain - executing against 1 file(s)")
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
            assertThat(task(":formatKotlin")?.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
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
            val resolvedRulesCount = output.findResolvedRuleProvidersCount("lintKotlinMain")
            assertThat(resolvedRulesCount).isGreaterThan(0)
        }

        build("formatKotlin", "--info").apply {
            assertThat(task(":formatKotlin")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            val resolvedRulesCount = output.findResolvedRuleProvidersCount("formatKotlinMain")
            assertThat(resolvedRulesCount).isGreaterThan(0)
        }
    }

    @Test
    fun `behavior on non-compiling code`() {
        settingsFile()
        buildFile()

        val className = "KotlinClass"
        kotlinSourceFile(
            "$className.kt",
            """
            class $className {
                private fun hi() = // this does not compile
            }

            """.trimIndent(),
        )

        val expectedFilPath = "/src/main/kotlin/KotlinClass.kt".replace("/", File.separator)
        buildAndFail("lintKotlin").apply {
            assertThat(output).contains("ktlint failed when parsing file").contains(expectedFilPath)
            assertThat(task(":lintKotlinMain")?.outcome).isEqualTo(TaskOutcome.FAILED)
        }

        buildAndFail("formatKotlin").apply {
            assertThat(output).contains("ktlint failed when parsing file").contains(expectedFilPath)
            assertThat(task(":formatKotlinMain")?.outcome).isEqualTo(TaskOutcome.FAILED)
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
            import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
            
            plugins {
                id 'org.jetbrains.kotlin.jvm'
                id 'io.github.usefulness.ktlint-gradle-plugin'
            }

            repositories {
                mavenCentral()
            }
            
            def targetJavaVersion = JavaVersion.VERSION_11
            tasks.withType(JavaCompile).configureEach {
                options.release.set(targetJavaVersion.majorVersion.toInteger())
            }
            tasks.withType(KotlinCompilationTask).configureEach {
                kotlinOptions.jvmTarget = targetJavaVersion
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
