package com.hotpath.heatmap.analysis

import com.hotpath.heatmap.model.MethodSummary
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

/**
 * Language-specific knowledge the otherwise language-neutral cost engine ([CallGraphTraversal],
 * [MethodSummaryService], the annotator) talks to instead of touching PHP or JS PSI directly.
 *
 * Implementations are stateless singletons selected per file by [LanguageSupports]. All methods
 * are called inside a read action.
 */
interface LanguageSupport {

    /** True if this support understands [file] (e.g. a PHP file, or a JS/TS file). */
    fun handles(file: PsiFile): Boolean

    /** Every call site (call expression) in [file], as language-neutral [PsiElement]s. */
    fun collectCallSites(file: PsiFile): List<PsiElement>

    /** The range to highlight for [callSite] — just the called name, ideally. */
    fun callNameRange(callSite: PsiElement): TextRange

    /** The textual name of the callee at [callSite] (e.g. `findById`, `fetch`), or null. */
    fun callName(callSite: PsiElement): String?

    /** Resolve [callSite] to the callable it invokes (a function/method declaration), or null. */
    fun resolveTarget(callSite: PsiElement): PsiElement?

    /** Loop-nesting depth of [callSite] within its enclosing callable (0 = not in a loop). */
    fun enclosingLoopDepth(callSite: PsiElement): Int

    /** The file backing [callable], used for vendor/test/library exclusion. */
    fun containingFile(callable: PsiElement): VirtualFile?

    /** Compute the cached *local* [MethodSummary] for [callable] (no recursion). */
    fun computeSummary(callable: PsiElement): MethodSummary
}

/**
 * Picks the [LanguageSupport] for a file from the registered extensions. Each language adapter
 * (PHP, JS) is contributed from its own *optional* dependency, so only the adapters whose language
 * plugin is actually installed are present here — that is what lets the plugin load in IDEs that
 * bundle only some (or none) of the supported languages. Order doesn't matter; each [handles] is
 * exclusive.
 */
object LanguageSupports {
    private val EP_NAME = ExtensionPointName.create<LanguageSupport>("com.hotpath.heatmap.languageSupport")

    fun forFile(file: PsiFile): LanguageSupport? = EP_NAME.extensionList.firstOrNull { it.handles(file) }
}
