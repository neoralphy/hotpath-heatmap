package com.hotpath.heatmap.model

/**
 * The accumulated downstream cost signals for one call site. These map directly onto the
 * scoring formula in the spec; [CostHeuristics][com.hotpath.heatmap.analysis.CostHeuristics]
 * turns it into a number and a [Severity].
 */
data class CostBreakdown(
    /** Loop nesting depth of the call site itself within its enclosing method. */
    val directLoopDepth: Int = 0,
    /** Deepest call-graph depth reached downstream of the call site. */
    val downstreamMaxCallDepth: Int = 0,
    /** Total loop statements found across all visited downstream methods. */
    val downstreamLoopCount: Int = 0,
    /** Deepest effective loop nesting along any path (loops compound across method calls). */
    val downstreamNestedLoopDepth: Int = 0,
    /** Fan-out (outgoing calls) of the immediate target. */
    val fanOut: Int = 0,
    /** Weighted risk from expensive method names + repository/client-like classes downstream. */
    val expensiveOperationRisk: Int = 0,
    /** Human-readable reasons, shown in the tooltip. */
    val reasons: List<String> = emptyList(),
    /** True when a traversal cap was hit and the result is only approximate. */
    val truncated: Boolean = false,
)

/** Final analysis for a single call site: its score, severity and the breakdown behind them. */
data class CallSiteResult(
    val score: Int,
    val severity: Severity,
    val breakdown: CostBreakdown,
)
