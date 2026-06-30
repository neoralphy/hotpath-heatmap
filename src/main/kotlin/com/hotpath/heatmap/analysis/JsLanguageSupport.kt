package com.hotpath.heatmap.analysis

import com.hotpath.heatmap.model.MethodSummary
import com.hotpath.heatmap.model.OutgoingCall
import com.intellij.lang.javascript.psi.JSCallExpression
import com.intellij.lang.javascript.psi.JSDoWhileStatement
import com.intellij.lang.javascript.psi.JSFile
import com.intellij.lang.javascript.psi.JSForInStatement
import com.intellij.lang.javascript.psi.JSForStatement
import com.intellij.lang.javascript.psi.JSFunction
import com.intellij.lang.javascript.psi.JSReferenceExpression
import com.intellij.lang.javascript.psi.JSWhileStatement
import com.intellij.lang.javascript.psi.ecmal4.JSClass
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.util.PsiTreeUtil
import java.util.Locale

/**
 * [LanguageSupport] for JavaScript / TypeScript, backed by `com.intellij.lang.javascript` PSI.
 *
 * Beyond the usual `for` / `while` loops, array-iteration callbacks (`forEach`, `map`, `reduce`,
 * …) are treated as loop levels — they are the JS analog of `foreach` and a common source of
 * hidden per-element cost, so code inside their callbacks is counted as being "inside a loop".
 */
class JsLanguageSupport : LanguageSupport {

    /** Array-iteration methods whose callback bodies behave like a loop body. */
    private val ITERATION_METHODS = setOf(
        "foreach", "map", "filter", "reduce", "reduceright", "flatmap", "some", "every",
    )

    override fun handles(file: PsiFile): Boolean = file is JSFile

    override fun collectCallSites(file: PsiFile): List<PsiElement> =
        PsiTreeUtil.findChildrenOfType(file, JSCallExpression::class.java).toList()

    override fun callNameRange(callSite: PsiElement): TextRange {
        val ref = (callSite as? JSCallExpression)?.methodExpression as? JSReferenceExpression
        return ref?.referenceNameElement?.textRange ?: callSite.textRange
    }

    override fun callName(callSite: PsiElement): String? =
        ((callSite as? JSCallExpression)?.methodExpression as? JSReferenceExpression)?.referenceName

    override fun resolveTarget(callSite: PsiElement): PsiElement? {
        val ref = (callSite as? JSCallExpression)?.methodExpression as? JSReferenceExpression ?: return null
        return ref.resolve() as? JSFunction
    }

    override fun enclosingLoopDepth(callSite: PsiElement): Int {
        var depth = 0
        var current: PsiElement? = callSite.parent
        while (current != null) {
            if (current is JSFunction && !isIterationCallback(current)) break
            if (isLoopLike(current)) depth++
            current = current.parent
        }
        return depth
    }

    override fun containingFile(callable: PsiElement): VirtualFile? =
        callable.containingFile?.virtualFile

    override fun computeSummary(callable: PsiElement): MethodSummary {
        val function = callable as JSFunction
        val containingClass = PsiTreeUtil.getParentOfType(function, JSClass::class.java)

        val loopLike = PsiTreeUtil.findChildrenOfAnyType(
            function, false,
            JSForStatement::class.java, JSForInStatement::class.java,
            JSWhileStatement::class.java, JSDoWhileStatement::class.java, JSCallExpression::class.java,
        ).filter { isLoopLike(it) && belongsTo(it, function) }
        val maxLoopDepth = loopLike.maxOfOrNull { loopDepthOf(it, function) } ?: 0

        val pointerManager = SmartPointerManager.getInstance(function.project)
        val outgoing = PsiTreeUtil.findChildrenOfType(function, JSCallExpression::class.java)
            .filter { belongsTo(it, function) }
            .map { call ->
                OutgoingCall(
                    reference = pointerManager.createSmartPsiElementPointer(call as PsiElement),
                    loopDepthAtCall = loopDepthOf(call, function),
                )
            }

        return MethodSummary(
            className = containingClass?.name,
            methodName = function.name ?: "",
            localLoopCount = loopLike.size,
            maxLocalLoopDepth = maxLoopDepth,
            outgoingCalls = outgoing,
        )
    }

    /** Loop-nesting depth of [element] within [callable]; a loop-like node counts its own level. */
    private fun loopDepthOf(element: PsiElement, callable: JSFunction): Int {
        var depth = if (isLoopLike(element)) 1 else 0
        var current: PsiElement? = element.parent
        while (current != null && current != callable) {
            if (current is JSFunction && !isIterationCallback(current)) break
            if (isLoopLike(current)) depth++
            current = current.parent
        }
        return depth
    }

    /**
     * True if the nearest enclosing *non-callback* function of [element] is exactly [callable] —
     * i.e. [element] is in [callable]'s own body (or inside one of its iteration callbacks), not in
     * a separately-declared nested function.
     */
    private fun belongsTo(element: PsiElement, callable: JSFunction): Boolean {
        var current: PsiElement? = element.parent
        while (current != null) {
            if (current is JSFunction) {
                if (isIterationCallback(current)) {
                    current = current.parent
                    continue
                }
                return current == callable
            }
            current = current.parent
        }
        return false
    }

    private fun isLoopLike(element: PsiElement): Boolean = when (element) {
        is JSForStatement, is JSForInStatement, is JSWhileStatement, is JSDoWhileStatement -> true
        is JSCallExpression -> isIterationCall(element)
        else -> false
    }

    private fun isIterationCall(call: JSCallExpression): Boolean {
        val name = (call.methodExpression as? JSReferenceExpression)?.referenceName ?: return false
        return name.lowercase(Locale.ROOT) in ITERATION_METHODS
    }

    /** Whether [function] is an inline callback passed to an iteration method (e.g. `forEach(fn)`). */
    private fun isIterationCallback(function: JSFunction): Boolean {
        val call = PsiTreeUtil.getParentOfType(function, JSCallExpression::class.java) ?: return false
        // Make sure the function is an *argument* of that call, not its callee or something deeper.
        return PsiTreeUtil.isAncestor(call.argumentList ?: return false, function, true) &&
            isIterationCall(call)
    }
}
