package io.github.usefulness.functional

import io.github.usefulness.functional.utils.kotlinClass
import io.github.usefulness.functional.utils.resolve
import io.github.usefulness.functional.utils.settingsFile
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

class BaselineTest : WithGradleTest.Kotlin() {
    lateinit var projectRoot: File

    @BeforeEach
    fun setup() {
        projectRoot = testProjectDir.apply {
            resolve("settings.gradle") { writeText(settingsFile) }
            resolve("build.gradle") {
                // language=groovy
                writeText(
                    """
                    plugins {
                        id 'org.jetbrains.kotlin.jvm'
                        id 'io.github.usefulness.ktlint-gradle-plugin'
                    }
                    
                    ktlint {
                        baselineFile.set(file('config/baseline.xml'))
                    }
                    
                    repositories.mavenCentral()
                    
                    """.trimIndent(),
                )
            }
            resolve(".editorconfig") {
                writeText(
                    """
                    [*.kt]
                    ktlint_standard_function-start-of-body-spacing = disabled
                    
                    """.trimIndent(),
                )
            }
            resolve("src/main/kotlin/ClassOne.kt") {
                writeText(kotlinClass("ClassOne"))
            }
        }
    }

    @Test
    fun `lintKotlin respects baseline`() {
        projectRoot.resolve("src/main/kotlin/WithOffence.kt") {
            writeText(kotlinClass("InvalidName"))
        }
        projectRoot.resolve("src/main/kotlin/CustomClass.kt") {
            // language=kotlin
            val validClass =
                """
                class CustomClass {
                    private fun go(){
                        println("go")
                    }
                }

                """.trimIndent()
            writeText(validClass)
        }
        projectRoot.resolve("config/baseline.xml") {
            // language=xml
            writeText(
                """
                <?xml version="1.0" encoding="utf-8"?>
                <baseline version="1.0">
                	<file name="src/main/kotlin/WithOffence.kt">
                		<error line="1" column="1" source="standard:filename" />
                	</file>
                	<file name="src/main/kotlin/CustomClass.kt">
                		<error line="2" column="21" source="standard:curly-spacing" />
                	</file>
                </baseline>

                """.trimIndent(),
            )
        }

        build("lintKotlin").apply {
            assertThat(output).doesNotContain("Lint error >")
        }

        projectRoot.resolve("src/main/kotlin/CustomClass.kt") {
            // language=kotlin
            val validClass =
                """
                class CustomClass {
                    private fun go()  {
                        println("go")
                    }
                }

                """.trimIndent()
            writeText(validClass)
        }
        buildAndFail("lintKotlin").apply {
            assertThat(output).contains("CustomClass.kt:2:22: Lint error > [standard:no-multi-spaces]")
        }

        projectRoot.resolve("config/baseline.xml") {
            // language=xml
            writeText(
                """
                <?xml version="1.0" encoding="utf-8"?>
                <baseline version="1.0">
                	<file name="src/main/kotlin/WithOffence.kt">
                		<error line="1" column="1" source="standard:filename" />
                	</file>
                	<file name="src/main/kotlin/CustomClass.kt">
                		<error line="2" column="22" source="standard:no-multi-spaces" />
                	</file>
                </baseline>

                """.trimIndent(),
            )
        }
        build("lintKotlin").apply {
            assertThat(output).doesNotContain("Lint error")
        }
    }

    @Test
    fun `formatKotlin doesn't respect baseline`() {
        projectRoot.resolve("src/main/kotlin/WithOffence.kt") {
            writeText(kotlinClass("InvalidName"))
        }
        projectRoot.resolve("src/main/kotlin/CustomClass.kt") {
            // language=kotlin
            val validClass =
                """
                class CustomClass {
                    private fun go(){
                        println("go")
                    }
                }

                """.trimIndent()
            writeText(validClass)
        }
        projectRoot.resolve("config/baseline.xml") {
            // language=xml
            writeText(
                """
                <?xml version="1.0" encoding="utf-8"?>
                <baseline version="1.0">
                	<file name="src/main/kotlin/WithOffence.kt">
                		<error line="1" column="1" source="standard:filename" />
                	</file>
                	<file name="src/main/kotlin/CustomClass.kt">
                		<error line="2" column="21" source="standard:curly-spacing" />
                	</file>
                </baseline>

                """.trimIndent(),
            )
        }
        build("formatKotlin").apply {
            assertThat(output).contains("CustomClass.kt:2:21: Format fixed > [standard:curly-spacing]")
        }

        projectRoot.resolve("src/main/kotlin/CustomClass.kt") {
            // language=kotlin
            val validClass =
                """
                class CustomClass {
                    private fun go()  {
                        println("go")
                    }
                }

                """.trimIndent()
            writeText(validClass)
        }

        projectRoot.resolve("config/baseline.xml") {
            // language=xml
            writeText(
                """
                <?xml version="1.0" encoding="utf-8"?>
                <baseline version="1.0">
                	<file name="src/main/kotlin/WithOffence.kt">
                		<error line="1" column="1" source="standard:filename" />
                	</file>
                	<file name="src/main/kotlin/CustomClass.kt">
                		<error line="2" column="22" source="standard:no-multi-spaces" />
                	</file>
                </baseline>

                """.trimIndent(),
            )
        }
        build("formatKotlin").apply {
            assertThat(output).contains("CustomClass.kt:2:22: Format fixed > [standard:no-multi-spaces]")
        }
    }
}
