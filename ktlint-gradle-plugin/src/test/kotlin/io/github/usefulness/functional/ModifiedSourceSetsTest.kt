package io.github.usefulness.functional

import org.gradle.testkit.runner.TaskOutcome
import io.github.usefulness.functional.utils.androidManifest
import io.github.usefulness.functional.utils.kotlinClass
import io.github.usefulness.functional.utils.resolve
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

internal class ModifiedSourceSetsTest : WithGradleTest.Android() {

    private lateinit var androidModuleRoot: File
    private lateinit var kotlinModuleRoot: File

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
                            sourceSets {
                                main.java.srcDirs += "src/main/kotlin"
                                test.java.srcDirs += "src/test/kotlin"
                                debug.java.srcDirs += "src/debug/kotlin"
                                flavorOne.java.srcDirs = ['src/customFolder/kotlin']
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
                resolve("src/androidTest/kotlin/AndroidTestSourceSet.kt") {
                    writeText(kotlinClass("AndroidTestSourceSet"))
                }
                resolve("src/customFolder/kotlin/CustomSourceSet.kt") {
                    writeText(kotlinClass("CustomSourceSet"))
                }
            }
            kotlinModuleRoot = resolve("kotlinproject") {
                resolve("build.gradle") {
                    // language=groovy
                    val kotlinBuildScript =
                        """
                        plugins {
                            id 'kotlin'
                            id 'java-test-fixtures'
                            id 'io.github.usefulness.ktlint-gradle-plugin'
                        }
                        
                        sourceSets {
                            main.kotlin.srcDirs += "random/path"
                            individuallyCustomized {
                                java.srcDirs = ["src/debug/kotlin"]
                            }
                        }
                        """.trimIndent()
                    writeText(kotlinBuildScript)
                }
                resolve("random/path/MainSourceSet.kt") {
                    writeText(kotlinClass("MainSourceSet"))
                }
                resolve("src/test/kotlin/TestSourceSet.kt") {
                    writeText(kotlinClass("TestSourceSet"))
                }
                resolve("src/individuallyCustomized/kotlin/CustomSourceSet.kt") {
                    writeText(kotlinClass("CustomSourceSet"))
                }
                resolve("src/testFixtures/kotlin/TestFixturesSourceSet.kt") {
                    writeText(kotlinClass("TestFixturesSourceSet"))
                }
            }
        }
    }

    @Test
    fun `plugin detects sources in all sourcesets`() {
        build("lintKotlin").apply {
            assertThat(task(":androidproject:lintKotlinMain")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(task(":androidproject:lintKotlinDebug")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(task(":androidproject:lintKotlinTest")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(task(":androidproject:lintKotlinFlavorOne")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(task(":androidproject:lintKotlinAndroidTest")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(task(":androidproject:lintKotlin")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(task(":kotlinproject:lintKotlinMain")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(task(":kotlinproject:lintKotlinTestFixtures")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(task(":kotlinproject:lintKotlinTest")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(task(":kotlinproject:lintKotlinIndividuallyCustomized")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        }
    }

    @Test
    fun `plugin becomes up-to-date on second run`() {
        build("lintKotlin")

        build("lintKotlin").apply {
            assertThat(task(":androidproject:lintKotlinMain")?.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
            assertThat(task(":androidproject:lintKotlinDebug")?.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
            assertThat(task(":androidproject:lintKotlinTest")?.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
            assertThat(task(":androidproject:lintKotlinFlavorOne")?.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
            assertThat(task(":androidproject:lintKotlinAndroidTest")?.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
            assertThat(task(":androidproject:lintKotlin")?.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
            assertThat(task(":kotlinproject:lintKotlinMain")?.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
            assertThat(task(":kotlinproject:lintKotlinTestFixtures")?.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
            assertThat(task(":kotlinproject:lintKotlinTest")?.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
            assertThat(task(":kotlinproject:lintKotlinIndividuallyCustomized")?.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
        }
    }

    // language=groovy
    private val settingsFile =
        """
        rootProject.name = 'ktlint-gradle-test-project'
        include 'androidproject', 'kotlinproject'
        """.trimIndent()
}
