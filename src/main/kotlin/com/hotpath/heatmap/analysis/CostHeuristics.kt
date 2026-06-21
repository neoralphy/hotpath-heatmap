package com.hotpath.heatmap.analysis

import com.hotpath.heatmap.model.CostBreakdown
import com.hotpath.heatmap.model.Severity

/** Turns a [CostBreakdown] into the spec's additive score and a [Severity] band. */
object CostHeuristics {

    fun score(b: CostBreakdown): Int =
        b.directLoopDepth +
            b.downstreamMaxCallDepth +
            b.downstreamLoopCount +
            b.downstreamNestedLoopDepth +
            b.fanOut +
            b.expensiveOperationRisk

    fun severity(score: Int): Severity = Severity.fromScore(score)
}
