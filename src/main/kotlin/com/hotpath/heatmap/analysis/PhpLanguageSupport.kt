package com.hotpath.heatmap.analysis

import com.hotpath.heatmap.model.MethodSummary
import com.hotpath.heatmap.model.OutgoingCall
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.PhpFile
import com.jetbrains.php.lang.psi.elements.DoWhile
import com.jetbrains.php.lang.psi.elements.For
import com.jetbrains.php.lang.psi.elements.ForeachStatement
import com.jetbrains.php.lang.psi.elements.Function
import com.jetbrains.php.lang.psi.elements.FunctionReference
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.While

/** [LanguageSupport] for PHP, backed by `com.jetbrains.php` PSI. */
object PhpLanguageSupport : LanguageSupport {

    override fun handles(file: PsiFile): Boolean = file is PhpFile

    override fun collectCallSites(file: PsiFile): List<PsiElement> =
        PsiTreeUtil.findChildrenOfType(file, FunctionReference::class.java).toList()

    override fun callNameRange(callSite: PsiElement): TextRange {
        val ref = callSite as FunctionReference
        return ref.nameNode?.textRange ?: ref.textRange
    }

    override fun callName(callSite: PsiElement): String? = (callSite as? FunctionReference)?.name

    override fun resolveTarget(callSite: PsiElement): PsiElement? =
        (callSite as? FunctionReference)?.resolve() as? Function

    override fun enclosingLoopDepth(callSite: PsiElement): Int {
        val enclosing = PsiTreeUtil.getParentOfType(callSite, Function::class.java) ?: return 0
        var depth = 0
        var current: PsiElement? = callSite.parent
        while (current != null && current != enclosing) {
            if (isLoop(current)) depth++
            current = current.parent
        }
        return depth
    }

    override fun containingFile(callable: PsiElement): VirtualFile? =
        callable.containingFile?.virtualFile

    override fun computeSummary(callable: PsiElement): MethodSummary {
        val function = callable as Function
        val containingClass = (function as? Method)?.containingClass

        val loops = PsiTreeUtil.findChildrenOfAnyType(
            function, false,
            ForeachStatement::class.java, For::class.java, While::class.java, DoWhile::class.java,
        )
        val maxLoopDepth = loops.maxOfOrNull { loopDepthOf(it, function) } ?: 0

        val pointerManager = SmartPointerManager.getInstance(function.project)
        val outgoing = PsiTreeUtil.findChildrenOfType(function, FunctionReference::class.java)
            .map { ref ->
                OutgoingCall(
                    reference = pointerManager.createSmartPsiElementPointer(ref as PsiElement),
                    loopDepthAtCall = loopDepthOf(ref, function),
                )
            }

        return MethodSummary(
            className = containingClass?.name,
            methodName = function.name,
            localLoopCount = loops.size,
            maxLocalLoopDepth = maxLoopDepth,
            outgoingCalls = outgoing,
        )
    }

    /** Loop-nesting depth of [element] within [function]; a loop counts its own level. */
    private fun loopDepthOf(element: PsiElement, function: Function): Int {
        var depth = if (isLoop(element)) 1 else 0
        var current: PsiElement? = element.parent
        while (current != null && current != function) {
            if (isLoop(current)) depth++
            current = current.parent
        }
        return depth
    }

    private fun isLoop(element: PsiElement): Boolean =
        element is ForeachStatement || element is For || element is While || element is DoWhile
}
