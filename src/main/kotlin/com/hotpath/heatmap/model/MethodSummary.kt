package com.hotpath.heatmap.model

import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer

/**
 * A single outgoing call observed inside a callable body, together with the loop nesting depth
 * at which it occurs (0 = not inside any loop). The call-site element is kept language-neutral
 * (a [PsiElement]) and stored as a [SmartPsiElementPointer] so it survives between the annotator
 * phases; resolution to a target callable happens lazily during traversal via the language SPI.
 */
data class OutgoingCall(
    val reference: SmartPsiElementPointer<PsiElement>,
    val loopDepthAtCall: Int,
)

/**
 * Purely *local* facts about one callable (PHP function/method or JS/TS function/method).
 * Summaries never recurse — the recursive cost is composed by
 * [com.hotpath.heatmap.analysis.CallGraphTraversal] from cached summaries. This keeps each cache
 * entry cheap and lets [com.intellij.psi.util.PsiModificationTracker] invalidate it precisely.
 */
data class MethodSummary(
    val className: String?,
    val methodName: String,
    /** Total number of loop (or loop-like) statements anywhere in the body. */
    val localLoopCount: Int,
    /** Deepest loop nesting within the body (0 = no loops). */
    val maxLocalLoopDepth: Int,
    /** Outgoing calls, with their loop depth at the call site. */
    val outgoingCalls: List<OutgoingCall>,
) {
    /** Number of distinct outgoing call sites in this callable. */
    val fanOut: Int get() = outgoingCalls.size
}
