package io.github.usefulness.support

import org.gradle.api.GradleException

internal sealed class PluginError(
    message: String,
    cause: Throwable? = null,
) : GradleException(message, cause) {

    class LintingError(message: String) : PluginError(message)
}
