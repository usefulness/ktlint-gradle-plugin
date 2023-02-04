package io.github.usefulness.functional

import io.github.usefulness.functional.utils.kotlinClass
import io.github.usefulness.functional.utils.resolve
import io.github.usefulness.functional.utils.settingsFile
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
import java.io.File

class ReportersTest : WithGradleTest.Kotlin() {

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
        projectRoot.resolve("src/main/kotlin/CustomClass.kt") {
            writeText(kotlinClass("CustomClass"))
        }
    }

    @Test
    @DisabledOnOs(OS.WINDOWS, disabledReason = "Report file content differs on Windows due to different path separator")
    fun `supports all types of reporters`() {
        projectRoot.resolve("build.gradle") {
            // language=groovy
            val buildScript =
                """

                ktlint {
                    reporters = [
                        'checkstyle',
                        'html',
                        'json',
                        'plain',
                        'sarif',
                    ] 
                }

                """
            appendText(buildScript)
        }

        build("lintKotlin").apply {
            assertThat(task(":lintKotlin")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(reportFile("/main-lint.txt")).content().isEqualTo(expectedEmptyPlain())
            assertThat(reportFile("main-lint.xml")).content().isEqualTo(expectedEmptyCheckstyle())
            assertThat(reportFile("/main-lint.html")).content().isEqualTo(expectedEmptyHtml())
            assertThat(reportFile("/main-lint.json")).content().isEqualTo(expectedEmptyJson())
            assertThat(reportFile("/main-lint.sarif.json")).content().isNotBlank()
        }
        build("lintKotlin").apply {
            assertThat(task(":lintKotlin")?.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
        }

        projectRoot.resolve("src/main/kotlin/FirstClass.kt") {
            writeText(kotlinClass("WrongClassName"))
        }
        projectRoot.resolve("src/main/kotlin/SecondClass.kt") {
            writeText(kotlinClass("MultipleOffencesInSingleSourceSet"))
        }

        projectRoot.resolve("src/test/kotlin/CustomTestClass.kt") {
            writeText(kotlinClass("DifferentSourceSet"))
        }

        buildAndFail("lintKotlin").apply {
            assertThat(task(":lintKotlinMainReporter")?.outcome).isEqualTo(TaskOutcome.FAILED)
            assertThat(reportFile("/main-lint.txt")).content().isEqualTo(expectedFailedPlain())
            assertThat(reportFile("/main-lint.xml")).content().isEqualTo(expectedFailedCheckstyle())
            assertThat(reportFile("/main-lint.html")).content().isEqualTo(expectedFailedHtml())
            assertThat(reportFile("/main-lint.json")).content().isEqualTo(expectedFailedJson())
            assertThat(reportFile("/main-lint.sarif.json")).content().isNotBlank()
        }
    }

    @Test
    fun `uses reporters from overridden ktlint version`() {
        projectRoot.resolve("build.gradle") {
            // language=groovy
            val buildScript =
                """

                ktlint {
                    reporters = ['sarif']
                    ktlintVersion = "0.48.1"
                }

                """
            appendText(buildScript)
        }

        build("lintKotlin").apply {
            val reportContent = projectRoot.resolve("build/reports/ktlint/main-lint.sarif.json")
            assertThat(reportContent).content().contains(""""version": "0.48.1"""")
            assertThat(reportContent).content().contains(""""semanticVersion": "0.48.1"""")
        }
    }

    private fun reportFile(reportName: String) = projectRoot.resolve("build/reports/ktlint/$reportName")
}

private fun expectedEmptyPlain() = "".trimIndent()

// language=xml
private fun expectedEmptyCheckstyle() = """
<?xml version="1.0" encoding="utf-8"?>
<checkstyle version="8.0">
</checkstyle>

""".trimIndent()

// language=html
private fun expectedEmptyHtml() = """
<html>
<head>
<link href="https://fonts.googleapis.com/css?family=Source+Code+Pro" rel="stylesheet" />
<meta http-equiv="Content-Type" Content="text/html; Charset=UTF-8">
<style>
body {
    font-family: 'Source Code Pro', monospace;
}
h3 {
    font-size: 12pt;
}</style>
</head>
<body>
<p>Congratulations, no issues found!</p>
</body>
</html>

""".trimIndent()

// language=json
private fun expectedEmptyJson() = """
[
]

""".trimIndent()

private fun expectedFailedPlain() = """
src/main/kotlin/FirstClass.kt:1:1: File 'FirstClass.kt' contains a single top level declaration and should be named 'WrongClassName.kt' (filename)
src/main/kotlin/SecondClass.kt:1:1: File 'SecondClass.kt' contains a single top level declaration and should be named 'MultipleOffencesInSingleSourceSet.kt' (filename)

Summary error count (descending) by rule:
  filename: 2

""".trimIndent()

// language=xml
private fun expectedFailedCheckstyle() = """
<?xml version="1.0" encoding="utf-8"?>
<checkstyle version="8.0">
    <file name="src/main/kotlin/FirstClass.kt">
        <error line="1" column="1" severity="error" message="File &apos;FirstClass.kt&apos; contains a single top level declaration and should be named &apos;WrongClassName.kt&apos;" source="filename" />
    </file>
    <file name="src/main/kotlin/SecondClass.kt">
        <error line="1" column="1" severity="error" message="File &apos;SecondClass.kt&apos; contains a single top level declaration and should be named &apos;MultipleOffencesInSingleSourceSet.kt&apos;" source="filename" />
    </file>
</checkstyle>

""".trimIndent()

// language=html
private fun expectedFailedHtml() = """
<html>
<head>
<link href="https://fonts.googleapis.com/css?family=Source+Code+Pro" rel="stylesheet" />
<meta http-equiv="Content-Type" Content="text/html; Charset=UTF-8">
<style>
body {
    font-family: 'Source Code Pro', monospace;
}
h3 {
    font-size: 12pt;
}</style>
</head>
<body>
<h1>Overview</h1>
<p>Issues found: 2</p>
<p>Issues corrected: 0</p>
<h3>src/main/kotlin/FirstClass.kt</h3>
<ul>
<li>(1, 1): File &apos;FirstClass.kt&apos; contains a single top level declaration and should be named &apos;WrongClassName.kt&apos;  (filename)</li>
</ul>
<h3>src/main/kotlin/SecondClass.kt</h3>
<ul>
<li>(1, 1): File &apos;SecondClass.kt&apos; contains a single top level declaration and should be named &apos;MultipleOffencesInSingleSourceSet.kt&apos;  (filename)</li>
</ul>
</body>
</html>

""".trimIndent()

// language=json
fun expectedFailedJson() = """
[
    {
        "file": "src/main/kotlin/FirstClass.kt",
        "errors": [
            {
                "line": 1,
                "column": 1,
                "message": "File 'FirstClass.kt' contains a single top level declaration and should be named 'WrongClassName.kt'",
                "rule": "filename"
            }
        ]
    },
    {
        "file": "src/main/kotlin/SecondClass.kt",
        "errors": [
            {
                "line": 1,
                "column": 1,
                "message": "File 'SecondClass.kt' contains a single top level declaration and should be named 'MultipleOffencesInSingleSourceSet.kt'",
                "rule": "filename"
            }
        ]
    }
]

""".trimIndent()
