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
                            id 'io.github.usefulness.ktlint-gradle-plugin'
                        }
                        
                        android {
                            namespace 'io.github.usefulness'
                            compileSdk 36
                            defaultConfig {
                                minSdkVersion 32
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
                resolve("src/testFixtures/kotlin/TestFixturesSourceSet.kt") {
                    writeText(kotlinClass("TestFixturesSourceSet"))
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
            listOf(
                ":androidproject:lintKotlinMain",
                ":androidproject:lintKotlinDebug",
                ":androidproject:lintKotlinTest",
                ":androidproject:lintKotlinFlavorOne",
                ":androidproject:lintKotlinAndroidTest",
                ":androidproject:lintKotlinTestFixtures",
                ":kotlinproject:lintKotlinMain",
                ":kotlinproject:lintKotlinTestFixtures",
                ":kotlinproject:lintKotlinTest",
                ":kotlinproject:lintKotlinIndividuallyCustomized",
            )
                .forEach { task ->
                    assertThat(task(task)?.outcome).isEqualTo(TaskOutcome.SUCCESS)
                }
            assertThat(task(":androidproject:lintKotlin")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(task(":kotlinproject:lintKotlin")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        }

        build("formatKotlin").apply {
            listOf(
                ":androidproject:formatKotlinMain",
                ":androidproject:formatKotlinDebug",
                ":androidproject:formatKotlinTest",
                ":androidproject:formatKotlinFlavorOne",
                ":androidproject:formatKotlinAndroidTest",
                ":androidproject:formatKotlinTestFixtures",
                ":kotlinproject:formatKotlinMain",
                ":kotlinproject:formatKotlinTestFixtures",
                ":kotlinproject:formatKotlinTest",
                ":kotlinproject:formatKotlinIndividuallyCustomized",
            )
                .forEach { task ->
                    assertThat(task(task)?.outcome).isEqualTo(TaskOutcome.SUCCESS)
                }
            assertThat(task(":androidproject:formatKotlin")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(task(":kotlinproject:formatKotlin")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        }
    }

    @Test
    fun `plugin becomes up-to-date on second run`() {
        build("lintKotlin")

        build("lintKotlin").apply {
            listOf(
                ":androidproject:lintKotlinMain",
                ":androidproject:lintKotlinDebug",
                ":androidproject:lintKotlinTest",
                ":androidproject:lintKotlinFlavorOne",
                ":androidproject:lintKotlinAndroidTest",
                ":kotlinproject:lintKotlinMain",
                ":kotlinproject:lintKotlinTestFixtures",
                ":kotlinproject:lintKotlinTest",
                ":kotlinproject:lintKotlinIndividuallyCustomized",
            )
                .forEach { taskName ->
                    assertThat(task(taskName)?.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
                }
            assertThat(task(":androidproject:lintKotlin")?.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
            assertThat(task(":kotlinproject:lintKotlin")?.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
        }

        build("formatKotlin")

        build("formatKotlin").apply {
            listOf(
                ":androidproject:formatKotlinMain",
                ":androidproject:formatKotlinDebug",
                ":androidproject:formatKotlinTest",
                ":androidproject:formatKotlinFlavorOne",
                ":androidproject:formatKotlinAndroidTest",
                ":kotlinproject:formatKotlinMain",
                ":kotlinproject:formatKotlinTestFixtures",
                ":kotlinproject:formatKotlinTest",
                ":kotlinproject:formatKotlinIndividuallyCustomized",
            )
                .forEach { taskName ->
                    assertThat(task(taskName)?.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
                }
            assertThat(task(":androidproject:formatKotlin")?.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
            assertThat(task(":kotlinproject:formatKotlin")?.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
        }
    }

    // language=groovy
    private val settingsFile =
        """
        rootProject.name = 'ktlint-gradle-test-project'
        include 'androidproject', 'kotlinproject'
        """.trimIndent()
}
