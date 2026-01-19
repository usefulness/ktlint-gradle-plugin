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
                            id 'io.github.usefulness.ktlint-gradle-plugin'
                        }
                        
                        android {
                            namespace 'io.github.usefulness'
                            compileSdk 36
                            defaultConfig {
                                minSdkVersion 32
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
                resolve("src/androidTest/kotlin/AndroidTestSourceSet.kt") {
                    writeText(kotlinClass("AndroidTestSourceSet"))
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
            listOf(
                "lintKotlinMain",
                "lintKotlinDebug",
                "lintKotlinTest",
                "lintKotlinFlavorOne",
                "lintKotlinAndroidTest",
            ).forEach { taskName ->
                assertThat(task(":androidproject:$taskName")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            }
            assertThat(task(":androidproject:lintKotlin")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        }

        build("formatKotlin").apply {
            listOf(
                "formatKotlinMain",
                "formatKotlinDebug",
                "formatKotlinTest",
                "formatKotlinFlavorOne",
                "formatKotlinAndroidTest",
            ).forEach { taskName ->
                assertThat(task(":androidproject:$taskName")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            }
            assertThat(task(":androidproject:formatKotlin")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        }
    }

    // language=groovy
    private val settingsFile =
        """
        rootProject.name = 'ktlint-gradle-test-project'
        include 'androidproject'
        """.trimIndent()
}
