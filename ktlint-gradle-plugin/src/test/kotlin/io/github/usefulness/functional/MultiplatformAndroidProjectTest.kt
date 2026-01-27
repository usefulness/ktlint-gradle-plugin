package io.github.usefulness.functional

import io.github.usefulness.functional.utils.editorConfig
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

internal class MultiplatformAndroidProjectTest : WithGradleTest.Android() {

    private lateinit var settingsFile: File
    private lateinit var buildFile: File
    private lateinit var sourceDir: File
    private lateinit var editorconfigFile: File
    private val pathPattern = "(.*\\.kt):\\d+:\\d+".toRegex()

    @BeforeEach
    fun setup() {
        settingsFile = testProjectDir.resolve("settings.gradle")
        buildFile = testProjectDir.resolve("build.gradle")
        sourceDir = testProjectDir.resolve("src/commonMain/kotlin/").also(File::mkdirs)
        editorconfigFile = testProjectDir.resolve(".editorconfig")
    }

    @Test
    fun `lintKotlinMain fails when lint errors detected`() {
        settingsFile()
        buildFile()
        editorConfig()

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
            assertThat(task(":lintKotlinCommonMain")?.outcome).isEqualTo(TaskOutcome.FAILED)
            assertThat(task(":lintKotlinAndroidHostTest")?.outcome).isEqualTo(TaskOutcome.NO_SOURCE)
            assertThat(task(":lintKotlinAndroidDeviceTest")?.outcome).isEqualTo(TaskOutcome.NO_SOURCE)
            assertThat(task(":lintKotlinCommonTest")?.outcome).isEqualTo(TaskOutcome.NO_SOURCE)
        }
    }

    private fun settingsFile() = settingsFile.apply {
        writeText("""rootProject.name = 'ktlint-gradle-test-project'""".trimIndent())
    }

    private fun buildFile() = buildFile.apply {
        // language=groovy
        val buildscript =
            """
            import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
            
            plugins {
                id 'org.jetbrains.kotlin.multiplatform'
                id 'io.github.usefulness.ktlint-gradle-plugin'
                id 'com.android.kotlin.multiplatform.library'
            }

            repositories {
                mavenCentral()
                google()
            }
            
            def targetJavaVersion = JavaVersion.VERSION_21
            tasks.withType(JavaCompile).configureEach {
                options.release.set(targetJavaVersion.majorVersion.toInteger())
            }
            tasks.withType(KotlinCompilationTask).configureEach {
                kotlinOptions.jvmTarget = targetJavaVersion
            }
            
            kotlin {
               androidLibrary {
                   namespace = "com.example.kmpfirstlib"
                   compileSdk = 33
                   minSdk = 24

                   withJava() // enable java compilation support
                   withHostTestBuilder {}.configure {}
                   withDeviceTestBuilder {
                       sourceSetTreeName = "test"
                   }
               }

               sourceSets {
                   androidMain {
                       dependencies {
                           // Add Android-specific dependencies here
                       }
                   }
                   getByName("androidHostTest") {
                       dependencies {
                       }
                   }

                   getByName("androidDeviceTest") {
                       dependencies {
                       }
                   }
               }
               // ... other targets (JVM, iOS, etc.) ...
            }
            """.trimIndent()
        writeText(buildscript)
    }

    private fun editorConfig() = editorconfigFile.apply {
        writeText(editorConfig)
    }

    private fun kotlinSourceFile(name: String, content: String) = File(sourceDir, name).apply {
        writeText(content)
    }
}
