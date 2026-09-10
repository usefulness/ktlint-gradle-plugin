package io.github.usefulness.customrules

import io.github.ktlint.core.rule.engine.core.api.AutocorrectDecision
import io.github.ktlint.core.rule.engine.core.api.RuleId
import io.github.ktlint.core.rule.engine.core.api.RuleV2
import io.github.ktlint.core.rule.engine.core.api.ifAutocorrectAllowed
import io.github.ktlint.core.rule.engine.core.api.isPartOfComment
import io.github.ktlint.core.rule.engine.core.api.isPartOfString
import io.github.ktlint.core.rule.engine.core.api.nextLeaf
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.com.intellij.psi.impl.source.tree.LeafPsiElement
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.KtSecondaryConstructor

class NoNewLineBeforeReturnTypeRule : RuleV2(
    ruleId = RuleId("custom-ktlint-rules:no-newline-before-return-type"),
    about = About()
) {

    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision,
    ) {
        if (
            node !is LeafPsiElement
            || !node.textMatches(":")
            || node.isPartOfComment
            || node.isPartOfString
        ) return

        if (
            node.parent !is KtFunction
            || node.parent is KtSecondaryConstructor
            || node.parent is KtPrimaryConstructor
        ) return

        val nextLeaf = node.nextLeaf { true }
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
