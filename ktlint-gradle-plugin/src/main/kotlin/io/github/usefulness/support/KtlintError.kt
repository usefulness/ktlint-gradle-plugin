package io.github.usefulness.support

import java.io.Serializable

/**
 * ktlint version agnostic counterpart of ktlint's `KtlintCliError`.
 * Workers exchange these through java serialization, so the type must not reference any ktlint class.
 */
internal data class KtlintError(
    val line: Int,
    val col: Int,
    val ruleId: String,
    val detail: String,
    val status: Status,
) : Serializable {

    enum class Status {
        BASELINE_IGNORED,
        LINT_CAN_NOT_BE_AUTOCORRECTED,
        LINT_CAN_BE_AUTOCORRECTED,
        FORMAT_IS_AUTOCORRECTED,
        KOTLIN_PARSE_EXCEPTION,
        KTLINT_RULE_ENGINE_EXCEPTION,
    }

    companion object {

        private const val serialVersionUID = 1L

        fun forLint(line: Int, col: Int, ruleId: String, detail: String, canBeAutoCorrected: Boolean) = KtlintError(
            line = line,
            col = col,
            ruleId = ruleId,
            detail = detail,
            status = if (canBeAutoCorrected) Status.LINT_CAN_BE_AUTOCORRECTED else Status.LINT_CAN_NOT_BE_AUTOCORRECTED,
        )

        fun forFormat(line: Int, col: Int, ruleId: String, detail: String, corrected: Boolean) = KtlintError(
            line = line,
            col = col,
            ruleId = ruleId,
            detail = if (corrected) detail else "$detail (cannot be auto-corrected)",
            status = if (corrected) Status.FORMAT_IS_AUTOCORRECTED else Status.LINT_CAN_NOT_BE_AUTOCORRECTED,
        )
    }
}

/**
 * Mirrors ktlint's `List<KtlintCliError>.doesNotContain` - baseline entries don't carry `detail`, so only position and rule id are compared
 */
internal fun List<KtlintError>.doesNotContain(error: KtlintError) = none {
    it.line == error.line && it.col == error.col && it.ruleId == error.ruleId
}
