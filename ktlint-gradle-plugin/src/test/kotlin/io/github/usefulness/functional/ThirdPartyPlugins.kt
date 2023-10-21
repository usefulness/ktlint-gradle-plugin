package io.github.usefulness.functional

import io.github.usefulness.functional.utils.editorConfig
import io.github.usefulness.functional.utils.kotlinClass
import io.github.usefulness.functional.utils.resolve
import io.github.usefulness.functional.utils.settingsFile
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ThirdPartyPlugins : WithGradleTest.Android() {

    @Test
    fun kspKotlin() {
        testProjectDir.apply {
            resolve("settings.gradle") { writeText(settingsFile) }
            resolve(".editorconfig") { writeText(editorConfig) }
            resolve("build.gradle") {
                // language=groovy
                writeText(
                    """
                    import org.gradle.api.JavaVersion
                    import org.jetbrains.kotlin.gradle.dsl.KotlinCompile
                    
                    plugins {
                        id 'org.jetbrains.kotlin.jvm'
                        id 'com.google.devtools.ksp'
                        id 'io.github.usefulness.ktlint-gradle-plugin'
                    }
                    
                    repositories.mavenCentral()
                    
                    dependencies {
                        ksp "com.google.dagger:dagger-compiler:2.48.1"
                    }
                    
                    kotlin {
                        jvmToolchain(21)
                    }
                    
                    def targetJavaVersion = JavaVersion.VERSION_17
                    tasks.withType(JavaCompile).configureEach {
                        options.release.set(targetJavaVersion.majorVersion.toInteger())
                    }
                    tasks.withType(KotlinCompile).configureEach {
                        kotlinOptions.jvmTarget = targetJavaVersion
                    }
                    
                    """.trimIndent(),
                )
            }
            resolve("src/main/kotlin/KotlinClass.kt") {
                writeText(kotlinClass("KotlinClass"))
            }
        }

        val result = build("lintKotlin")

        assertThat(result.tasks.map { it.path }).containsExactlyInAnyOrder(
            ":lintKotlinTest",
            ":lintKotlinMain",
            ":lintKotlin",
        )
        testProjectDir.resolve("build.gradle") {
            appendText(
                // language=groovy
                """
                
                ktlint {
                    ignoreKspGeneratedSources = false 
                }
                
                """.trimIndent(),
            )
        }

        val onlyMain = build("lintKotlin")

        assertThat(onlyMain.tasks.map { it.path }).containsAll(
            listOf(
                ":compileKotlin",
                ":compileJava",
                ":lintKotlinGeneratedByKspTestKotlin",
                ":lintKotlinTest",
                ":lintKotlinMain",
                ":lintKotlin",
            ),
        )
    }

    @Test
    fun kspAndroid() {
        testProjectDir.apply {
            resolve("settings.gradle") { writeText(settingsFile) }
            resolve(".editorconfig") { writeText(editorConfig) }
            resolve("build.gradle") {
                // language=groovy
                writeText(
                    """
                    plugins {
                        id 'com.android.library'
                        id 'org.jetbrains.kotlin.android'
                        id 'com.google.devtools.ksp'
                        id 'io.github.usefulness.ktlint-gradle-plugin'
                    }
                    
                    android {
                        namespace 'io.github.usefulness'
                        compileSdk 33
                        defaultConfig {
                            minSdkVersion 23
                        }
                    }
                            
                    repositories.mavenCentral()
                    
                    dependencies {
                        ksp "com.google.dagger:dagger-compiler:2.48"
                    }
                    
                    """.trimIndent(),
                )
            }
            resolve("src/main/kotlin/KotlinClass.kt") {
                writeText(kotlinClass("KotlinClass"))
            }
        }

        val result = build("lintKotlin")

        assertThat(result.tasks.map { it.path }).containsExactlyInAnyOrder(
            ":lintKotlinTestFixturesRelease",
            ":lintKotlinTestDebug",
            ":lintKotlinAndroidTest",
            ":lintKotlinTestFixtures",
            ":lintKotlinTestFixturesDebug",
            ":lintKotlinRelease",
            ":lintKotlinTest",
            ":lintKotlinTestRelease",
            ":lintKotlinMain",
            ":lintKotlinDebug",
            ":lintKotlinAndroidTestRelease",
            ":lintKotlinAndroidTestDebug",
            ":lintKotlin",
        )

        testProjectDir.resolve("build.gradle") {
            appendText(
                // language=groovy
                """
                
                ktlint {
                    ignoreKspGeneratedSources = false 
                }
                
                """.trimIndent(),
            )
        }

        val onlyMain = build("lintKotlin")

        assertThat(onlyMain.tasks.map { it.path }).containsExactlyInAnyOrder(
            ":lintKotlinTestFixturesRelease",
            ":lintKotlinTestDebug",
            ":lintKotlinAndroidTest",
            ":lintKotlinTestFixtures",
            ":lintKotlinTestFixturesDebug",
            ":lintKotlinRelease",
            ":lintKotlinTest",
            ":lintKotlinTestRelease",
            ":lintKotlinMain",
            ":lintKotlinDebug",
            ":lintKotlinAndroidTestRelease",
            ":lintKotlinAndroidTestDebug",
            ":lintKotlin",
        )
    }
}
