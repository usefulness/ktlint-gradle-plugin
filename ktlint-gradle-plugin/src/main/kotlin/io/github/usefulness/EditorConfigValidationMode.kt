package io.github.usefulness

import org.gradle.api.Incubating

@Incubating
public enum class EditorConfigValidationMode {
    None,
    PrintWarningLogs,
    BuildFailure,
}
