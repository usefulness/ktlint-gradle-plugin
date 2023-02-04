package io.github.usefulness.support

import com.pinterest.ktlint.core.LintError
import java.io.File
import java.io.Serializable

internal data class KtlintErrorResult(
    val file: File,
    val errors: List<Pair<LintError, Boolean>>,
) : Serializable {

    companion object {

        const val serialVersionUID = 1L
    }
}
