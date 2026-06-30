package com.hotpath.heatmap.annotator

import com.hotpath.heatmap.analysis.CallGraphTraversal
import com.hotpath.heatmap.analysis.LanguageSupport
import com.hotpath.heatmap.analysis.LanguageSupports
import com.hotpath.heatmap.analysis.MethodSummaryService
import com.hotpath.heatmap.model.CallSiteResult
import com.hotpath.heatmap.settings.HotPathSettings
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer

/**
 * Drives the heatmap through the IntelliJ daemon's [ExternalAnnotator] contract, which is what
 * keeps the IDE responsive:
 *  - [collectInformation] runs in a read action on the EDT and only does cheap PSI collection;
 *  - [doAnnotate] runs on a background thread (no read lock) where we do the call-graph walk,
 *    re-acquiring the read lock ourselves and respecting a per-file time budget;
 *  - [apply] runs on the EDT and only paints the highlights.
 * The daemon debounces re-runs and only schedules them for open files.
 */
class HotPathAnnotator :
    ExternalAnnotator<HotPathAnnotator.CollectedInfo, List<HotPathAnnotator.Highlight>>() {

    /** Snapshot gathered cheaply in the read-action phase. */
    data class CollectedInfo(
        val file: PsiFile,
        val support: LanguageSupport,
        val callSites: List<SmartPsiElementPointer<PsiElement>>,
    )

    /** A computed highlight ready to be painted in [apply]. */
    data class Highlight(val range: TextRange, val result: CallSiteResult)

    override fun collectInformation(file: PsiFile, editor: Editor, hasErrors: Boolean): CollectedInfo? {
        if (!HotPathSettings.getInstance().state.enabled) return null
        val support = LanguageSupports.forFile(file) ?: return null

        val pointerManager = SmartPointerManager.getInstance(file.project)
        val sites = support.collectCallSites(file)
            .map { pointerManager.createSmartPsiElementPointer(it) }
        if (sites.isEmpty()) return null
        return CollectedInfo(file, support, sites)
    }

    override fun doAnnotate(info: CollectedInfo): List<Highlight> {
        val settings = HotPathSettings.getInstance().state
        if (!settings.enabled) return emptyList()

        val project = info.file.project
        val summaryService = project.getService(MethodSummaryService::class.java)
        val traversal = CallGraphTraversal(project, settings, summaryService, info.support)
        val deadlineNanos = System.nanoTime() + settings.maxAnalysisTimePerFileMs * 1_000_000L
        val minOrdinal = settings.minHighlightSeverityOrdinal

        // Heavy work happens here, off the EDT, inside a read action with a wall-clock budget.
        return ReadAction.compute<List<Highlight>, RuntimeException> {
            val highlights = ArrayList<Highlight>()
            for (pointer in info.callSites) {
                if (System.nanoTime() > deadlineNanos) break
                val callSite = pointer.element ?: continue
                if (!callSite.isValid) continue

                val result = traversal.analyzeCallSite(callSite, deadlineNanos) ?: continue
                if (!result.severity.isHighlighted || result.severity.ordinal < minOrdinal) continue

                highlights += Highlight(info.support.callNameRange(callSite), result)
            }
            highlights
        }
    }

    override fun apply(file: PsiFile, annotationResult: List<Highlight>, holder: AnnotationHolder) {
        for (highlight in annotationResult) {
            val result = highlight.result
            val tooltip = buildTooltip(result)
            val attrs = result.severity.textAttributes() ?: continue
            // INFORMATION keeps this on the lowest highlighting layer, so the straight-underline
            // marker always yields to any warning/error/typo underline on the same token (shared
            // "underline slot") instead of doubling up or painting over a diagnostic.
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(highlight.range)
                .enforcedTextAttributes(attrs)
                .tooltip(tooltip)
                .gutterIconRenderer(ScoreGutterIconRenderer(result.score, result.severity, tooltip))
                .create()
        }
    }

    private fun buildTooltip(result: CallSiteResult): String {
        val sb = StringBuilder()
        sb.append("<html><b>Estimated execution cost: ")
            .append(result.severity.displayName.uppercase())
            .append("</b> (score ").append(result.score).append(")")
        sb.append("<br><br>Reason:")
        for (reason in result.breakdown.reasons) {
            sb.append("<br>&bull; ").append(escape(reason))
        }
        sb.append("</html>")
        return sb.toString()
    }

    private fun escape(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
