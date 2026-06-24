package com.hotpath.heatmap.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/** Mutable, persisted plugin configuration. */
class HotPathState : BaseState() {
    var enabled by property(true)
    var maxCallDepth by property(5)
    var maxMethodsVisitedPerCallSite by property(100)
    var maxAnalysisTimePerFileMs by property(500)
    /** Skip folders marked "Excluded" via the IDE's Mark Directory as… menu. */
    var excludeMarkedExcluded by property(true)

    /** Skip folders marked as "Test Sources Root" via the IDE's Mark Directory as… menu. */
    var excludeTestSources by property(true)

    /** Minimum severity (by ordinal) that gets highlighted. Defaults to LOW. */
    var minHighlightSeverityOrdinal by property(1)

    var enableNameHeuristics by property(true)
    var enableRepositoryClientHeuristics by property(true)
    var enableLoopMultiplier by property(true)
}

/** Application-level persistent settings for the Hot Path Heatmap plugin. */
@State(
    name = "HotPathHeatmapSettings",
    storages = [Storage("hotpath-heatmap.xml")],
)
class HotPathSettings : SimplePersistentStateComponent<HotPathState>(HotPathState()) {
    companion object {
        fun getInstance(): HotPathSettings =
            ApplicationManager.getApplication().getService(HotPathSettings::class.java)
    }
}
