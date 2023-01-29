package io.github.usefulness.support

import java.io.Serializable

internal data class KtLintParams(
    val experimentalRules: Boolean,
    val disabledRules: List<String>,
) : Serializable {

    companion object {
        const val serialVersionUID = 1L
    }
}
