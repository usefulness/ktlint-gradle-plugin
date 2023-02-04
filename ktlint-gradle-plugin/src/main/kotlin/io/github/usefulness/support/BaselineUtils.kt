package io.github.usefulness.support

import com.pinterest.ktlint.core.LintError
import com.pinterest.ktlint.core.api.Baseline
import com.pinterest.ktlint.core.api.loadBaseline
import java.io.File

internal fun File.readKtlintBaseline(): Map<String, List<LintError>>? {
    val baseline = loadBaseline(absolutePath)
    when (baseline.status) {
        Baseline.Status.VALID -> Unit
        Baseline.Status.NOT_FOUND,
        Baseline.Status.INVALID,
        -> return null
    }

    return baseline.lintErrorsPerFile
}

internal fun File.getBaselineKey(projectDir: File) =
    toRelativeString(projectDir).replace(File.separatorChar, '/')

/**
 * Same as ktlint, avoids unnecessary incompatibility issues
 */
internal fun List<LintError>.doesNotContain(lintError: LintError) =
    none {
        it.col == lintError.col &&
            it.line == lintError.line &&
            it.ruleId == lintError.ruleId
    }
