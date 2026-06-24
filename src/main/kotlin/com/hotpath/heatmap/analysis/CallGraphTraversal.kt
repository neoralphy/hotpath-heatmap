package com.hotpath.heatmap.analysis

import com.hotpath.heatmap.model.CallSiteResult
import com.hotpath.heatmap.model.CostBreakdown
import com.hotpath.heatmap.settings.HotPathState
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.PsiElement

/**
 * Composes cached [com.hotpath.heatmap.model.MethodSummary]s downstream of a call site to estimate
 * its cost, talking to a [LanguageSupport] for everything language-specific. All caps from the spec
 * are enforced here: [HotPathState.maxCallDepth], [HotPathState.maxMethodsVisitedPerCallSite] and a
 * wall-clock deadline. Must run inside a read action (the annotator wraps it in one off the EDT).
 */
class CallGraphTraversal(
    private val project: Project,
    private val settings: HotPathState,
    private val summaryService: MethodSummaryService,
    private val support: LanguageSupport,
) {

    private val fileIndex = ProjectFileIndex.getInstance(project)

    private companion object {
        /** Risk weight for a known external I/O call (e.g. an HTTP `fetch`). */
        const val IO_RISK = 3
    }

    /** Mutable accumulator threaded through the recursive walk. */
    private class Acc {
        var maxDepth = 0
        var loopCount = 0
        var maxNestedLoopDepth = 0
        var risk = 0
        var methodsVisited = 0
        /** Outgoing calls of the immediate target that resolve to non-trivial project code. */
        var immediateFanOut = 0
        var truncated = false
        // Specific findings grouped by category so no signal type is ever dropped from the tooltip.
        val expensiveHits = LinkedHashSet<String>()
        val repositoryHits = LinkedHashSet<String>()
        val clientHits = LinkedHashSet<String>()
        val ioHits = LinkedHashSet<String>()
        val visited = HashSet<PsiElement>()
    }

    /**
     * Analyze a single call site. Returns `null` when the call resolves to nothing in the project
     * (or to excluded vendor/test/library code) — i.e. nothing worth highlighting.
     *
     * @param deadlineNanos shared wall-clock budget; when exceeded the walk stops early.
     */
    fun analyzeCallSite(callSite: PsiElement, deadlineNanos: Long): CallSiteResult? {
        val target = support.resolveTarget(callSite)
        // A call straight into a built-in/library (e.g. `fetch(...)`) isn't traversed, but a known
        // I/O global still deserves a marker on its own.
        if (target == null || isExcluded(target)) return externalCallResult(callSite)

        val directLoopDepth = support.enclosingLoopDepth(callSite)
        val acc = Acc().apply { maxNestedLoopDepth = directLoopDepth }

        traverse(target, depth = 1, pathLoopDepth = directLoopDepth, deadlineNanos, acc)

        val breakdown = CostBreakdown(
            directLoopDepth = directLoopDepth,
            downstreamMaxCallDepth = acc.maxDepth,
            downstreamLoopCount = acc.loopCount,
            downstreamNestedLoopDepth = if (settings.enableLoopMultiplier) acc.maxNestedLoopDepth else 0,
            fanOut = acc.immediateFanOut,
            expensiveOperationRisk = acc.risk,
            reasons = buildReasons(directLoopDepth, acc),
            truncated = acc.truncated,
        )
        val score = CostHeuristics.score(breakdown)
        return CallSiteResult(score, CostHeuristics.severity(score), breakdown)
    }

    private fun traverse(callable: PsiElement, depth: Int, pathLoopDepth: Int, deadlineNanos: Long, acc: Acc) {
        if (depth > settings.maxCallDepth) {
            acc.truncated = true
            return
        }
        if (acc.methodsVisited >= settings.maxMethodsVisitedPerCallSite || System.nanoTime() > deadlineNanos) {
            acc.truncated = true
            return
        }

        if (!acc.visited.add(callable)) return // cycle / already counted
        if (isExcluded(callable)) return
        acc.methodsVisited++

        val summary = summaryService.summaryFor(callable, support)
        acc.maxDepth = maxOf(acc.maxDepth, depth)
        acc.loopCount += summary.localLoopCount

        scoreNameAndClass(summary.methodName, summary.className, acc)

        for (call in summary.outgoingCalls) {
            val callSite = call.reference.element ?: continue
            val effectiveLoopDepth = pathLoopDepth + call.loopDepthAtCall
            acc.maxNestedLoopDepth = maxOf(acc.maxNestedLoopDepth, effectiveLoopDepth)

            val next = support.resolveTarget(callSite)
            if (next == null || isExcluded(next)) {
                // Built-ins / library code are leaves, but a known I/O global (fetch, …) still counts.
                scoreExternalCall(support.callName(callSite), acc)
                continue
            }
            // Fan-out counts only the immediate target's calls into non-trivial project code.
            if (depth == 1) acc.immediateFanOut++
            traverse(next, depth + 1, effectiveLoopDepth, deadlineNanos, acc)
            if (acc.truncated && System.nanoTime() > deadlineNanos) return
        }
    }

    private fun scoreNameAndClass(methodName: String, className: String?, acc: Acc) {
        val label = if (className != null) "$className::$methodName()" else "$methodName()"
        if (settings.enableNameHeuristics && HeuristicSignals.isExpensiveMethodName(methodName)) {
            if (acc.expensiveHits.add(label)) acc.risk += 2
        }
        if (settings.enableRepositoryClientHeuristics) {
            if (HeuristicSignals.isRepositoryLikeClass(className) && acc.repositoryHits.add(label)) acc.risk += 3
            if (HeuristicSignals.isClientLikeClass(className) && acc.clientHits.add(label)) acc.risk += 3
        }
    }

    /** Score a call whose target we don't traverse (built-in/library) when it's a known I/O call. */
    private fun scoreExternalCall(callName: String?, acc: Acc) {
        if (!settings.enableNameHeuristics) return
        if (callName != null && HeuristicSignals.isKnownIoCall(callName) && acc.ioHits.add("$callName()")) {
            acc.risk += IO_RISK
        }
    }

    /** Result for a call straight into a known I/O global (`fetch`/…); null otherwise. */
    private fun externalCallResult(callSite: PsiElement): CallSiteResult? {
        val name = support.callName(callSite)
        if (!settings.enableNameHeuristics || !HeuristicSignals.isKnownIoCall(name)) return null

        val directLoopDepth = support.enclosingLoopDepth(callSite)
        val reasons = buildList {
            if (directLoopDepth > 0) add("Call is inside loop depth $directLoopDepth")
            add("Known I/O calls (1): $name()")
        }
        val breakdown = CostBreakdown(
            directLoopDepth = directLoopDepth,
            fanOut = 0,
            expensiveOperationRisk = IO_RISK,
            reasons = reasons,
        )
        val score = CostHeuristics.score(breakdown)
        return CallSiteResult(score, CostHeuristics.severity(score), breakdown)
    }

    private fun buildReasons(directLoopDepth: Int, acc: Acc): List<String> {
        val reasons = mutableListOf<String>()
        if (directLoopDepth > 0) reasons += "Call is inside loop depth $directLoopDepth"
        if (acc.maxDepth > 0) reasons += "Downstream call depth: ${acc.maxDepth}"
        if (acc.loopCount > 0) reasons += "Downstream loops found: ${acc.loopCount}"
        if (settings.enableLoopMultiplier && acc.maxNestedLoopDepth > 1) {
            reasons += "Effective nested loop depth: ${acc.maxNestedLoopDepth}"
        }
        categoryReason("Repository-like", acc.repositoryHits)?.let { reasons += it }
        categoryReason("Client/gateway-like", acc.clientHits)?.let { reasons += it }
        categoryReason("Expensive-looking", acc.expensiveHits)?.let { reasons += it }
        categoryReason("Known I/O", acc.ioHits)?.let { reasons += it }
        if (acc.immediateFanOut > 0) reasons += "Fan-out: ${acc.immediateFanOut} method calls"
        if (acc.truncated) reasons += "Analysis truncated after configured limit (approximate)."
        return reasons
    }

    /** One tooltip line per finding category, e.g. "Repository-like calls (2): A::find(), B::get()". */
    private fun categoryReason(label: String, hits: Set<String>): String? {
        if (hits.isEmpty()) return null
        val shown = hits.take(3).joinToString(", ")
        val more = if (hits.size > 3) " (+${hits.size - 3} more)" else ""
        return "$label calls (${hits.size}): $shown$more"
    }

    /**
     * True when [callable] should be treated as a leaf rather than traversed. Built-in /
     * standard-library / SDK stubs and any non-project library file are never real downstream cost
     * and are *always* skipped. On top of that, folders the user marked via the IDE's
     * "Mark Directory as…" menu are skipped when the matching setting is on:
     *  - [HotPathState.excludeTestSources] → folders marked as Test Sources Root;
     *  - [HotPathState.excludeMarkedExcluded] → folders marked Excluded.
     * Skipping these keeps trivial calls from inflating fan-out and call depth.
     */
    private fun isExcluded(callable: PsiElement): Boolean {
        val vFile = support.containingFile(callable) ?: return true

        if (settings.excludeTestSources && fileIndex.isInTestSourceContent(vFile)) return true
        if (settings.excludeMarkedExcluded && fileIndex.isExcluded(vFile)) return true

        // Always skip genuine non-project code (libraries, SDKs, built-in stubs). An Excluded
        // folder also reports !isInContent, so we let those through here — if the user turned the
        // exclude-marked setting off above, we still want to traverse them.
        if (!fileIndex.isInContent(vFile) && !fileIndex.isExcluded(vFile)) return true
        return false
    }
}
