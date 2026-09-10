package io.github.usefulness.support

import java.io.File
import java.io.Serializable

internal data class KtlintErrorResult(
    val file: File,
    val errors: List<KtlintError>,
) : Serializable {

    companion object {

        private const val serialVersionUID = 3L
    }
}
