package io.github.usefulness.functional

import io.github.usefulness.functional.utils.androidManifest
import io.github.usefulness.functional.utils.kotlinClass
import org.gradle.testkit.runner.TaskOutcome
import io.github.usefulness.functional.utils.resolve
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

internal class AndroidProjectTest : WithGradleTest.Android() {

    private lateinit var androidModuleRoot: File

    @BeforeEach
    fun setUp() {
        testProjectDir.apply {
            resolve("settings.gradle") { writeText(settingsFile) }
            resolve("build.gradle") {
                // language=groovy
                val buildScript =
                    """
                subprojects {
                    repositories {
                        google()
                        mavenCentral()
                    }
                }
                
                    """.trimIndent()
                writeText(buildScript)
            }
            androidModuleRoot = resolve("androidproject") {
                resolve("build.gradle") {
                    // language=groovy
                    val androidBuildScript =
                        """
                        plugins {
                            id 'com.android.library'
                            id 'kotlin-android'
                            id 'io.github.usefulness.ktlint-gradle-plugin'
                        }
                        
                        android {
                            compileSdkVersion 31
                            defaultConfig {
                                minSdkVersion 23
                            }
                            
                            flavorDimensions 'customFlavor'
                            productFlavors {
                                flavorOne {
                                    dimension 'customFlavor'
                                }
                                flavorTwo {
                                    dimension 'customFlavor'
                                }
                            }
                        }
                        
                        """.trimIndent()
                    writeText(androidBuildScript)
                }
                resolve("src/main/AndroidManifest.xml") {
                    writeText(androidManifest)
                }
                resolve("src/main/kotlin/MainSourceSet.kt") {
                    writeText(kotlinClass("MainSourceSet"))
                }
                resolve("src/debug/kotlin/DebugSourceSet.kt") {
                    writeText(kotlinClass("DebugSourceSet"))
                }
                resolve("src/test/kotlin/TestSourceSet.kt") {
                    writeText(kotlinClass("TestSourceSet"))
                }
                resolve("src/flavorOne/kotlin/FlavorSourceSet.kt") {
                    writeText(kotlinClass("FlavorSourceSet"))
                }
            }
        }
    }

    @Test
    fun runsOnAndroidProject() {
        build("lintKotlin").apply {
            assertThat(TaskOutcome.SUCCESS).isEqualTo(task(":androidproject:lintKotlinMain")?.outcome)
            assertThat(TaskOutcome.SUCCESS).isEqualTo(task(":androidproject:lintKotlinDebug")?.outcome)
            assertThat(TaskOutcome.SUCCESS).isEqualTo(task(":androidproject:lintKotlinTest")?.outcome)
            assertThat(TaskOutcome.SUCCESS).isEqualTo(task(":androidproject:lintKotlinFlavorOne")?.outcome)
            assertThat(TaskOutcome.SUCCESS).isEqualTo(task(":androidproject:lintKotlin")?.outcome)
        }
    }

    // language=groovy
    private val settingsFile =
        """
        rootProject.name = 'ktlint-gradle-test-project'
        include 'androidproject'
        """.trimIndent()
}
