package io.github.usefulness.support

import com.pinterest.ktlint.cli.reporter.core.api.KtlintCliError
import java.io.File
import java.io.Serializable

internal data class KtlintErrorResult(
    val file: File,
    val errors: List<KtlintCliError>,
) : Serializable {

    companion object {

        private const val serialVersionUID = 2L
    }
}
