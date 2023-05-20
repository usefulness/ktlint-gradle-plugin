package io.github.usefulness.support

import com.pinterest.ktlint.cli.reporter.baseline.Baseline
import com.pinterest.ktlint.cli.reporter.baseline.loadBaseline
import com.pinterest.ktlint.cli.reporter.core.api.KtlintCliError
import java.io.File

internal fun File.readKtlintBaseline(): Map<String, List<KtlintCliError>>? {
    val baseline = loadBaseline(absolutePath)
    when (baseline.status) {
        Baseline.Status.VALID -> Unit
        Baseline.Status.NOT_FOUND,
        Baseline.Status.INVALID,
        Baseline.Status.DISABLED,
        -> return null
    }.let { }

    return baseline.lintErrorsPerFile
}

internal fun File.getBaselineKey(projectDir: File) = toRelativeString(projectDir).replace(File.separatorChar, '/')
