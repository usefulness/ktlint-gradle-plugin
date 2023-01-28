package io.github.usefulness.functional.utils

// language=kotlin
internal fun kotlinClass(className: String) =
    """
    object $className
    
    """.trimIndent()

// language=groovy
internal val settingsFile =
    """
    rootProject.name = 'ktlint-gradle-test-project'
    
    """.trimIndent()

// language=xml
internal val androidManifest =
    """
     <manifest package="com.example" />

    """.trimIndent()

internal val editorConfig =
    """
    [*.kt]
    
    """.trimIndent()
