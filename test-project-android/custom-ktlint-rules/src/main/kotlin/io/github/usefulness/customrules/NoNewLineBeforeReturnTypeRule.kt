package io.github.usefulness.customrules

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import com.pinterest.ktlint.rule.engine.core.api.ifAutocorrectAllowed
import com.pinterest.ktlint.rule.engine.core.api.isPartOfComment
import com.pinterest.ktlint.rule.engine.core.api.isPartOfString
import com.pinterest.ktlint.rule.engine.core.api.nextLeaf
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.com.intellij.psi.impl.source.tree.LeafPsiElement
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.KtSecondaryConstructor

class NoNewLineBeforeReturnTypeRule : Rule(
    ruleId = RuleId("custom-ktlint-rules:no-newline-before-return-type"),
    about = About()
), RuleAutocorrectApproveHandler {

    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision,
    ) {
        if (
            node !is LeafPsiElement
            || !node.textMatches(":")
            || node.isPartOfComment()
            || node.isPartOfString()
        ) return

        if (
            node.parent !is KtFunction
            || node.parent is KtSecondaryConstructor
            || node.parent is KtPrimaryConstructor
        ) return

        val nextLeaf = node.nextLeaf()
        if (nextLeaf?.textContains('\n') != true) return

        emit(
            nextLeaf.startOffset,
            "Unexpected new-line before return type definition.",
            true,
        ).ifAutocorrectAllowed {
            (nextLeaf as LeafPsiElement).rawReplaceWithText(" ")
        }
    }
}
