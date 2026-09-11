package io.github.usefulness.support

import io.github.ktlint.core.cli.reporter.baseline.Baseline
import io.github.ktlint.core.cli.reporter.baseline.BaselineErrorHandling
import io.github.ktlint.core.cli.reporter.baseline.loadBaseline
import io.github.ktlint.core.cli.reporter.core.api.KtlintCliError
import java.io.File

internal fun File.readKtlintBaseline(): Map<String, List<KtlintCliError>>? {
    val baseline = loadBaseline(absolutePath, errorHandling = BaselineErrorHandling.LOG)
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
