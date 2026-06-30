package com.hotpath.heatmap.actions

import com.hotpath.heatmap.settings.HotPathSettings
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.IconLoader

/**
 * Show/hide button for the heatmap. Clicking flips the persisted [HotPathState.enabled] flag:
 * when off, [com.hotpath.heatmap.annotator.HotPathAnnotator] short-circuits before any call-graph
 * scoring happens; when on, calculation resumes.
 *
 * This is a plain [AnAction] (not a ToggleAction) on purpose: the state is conveyed entirely by the
 * icon — a lit flame when on, a greyed flame with a diagonal slash when off — so we avoid the
 * toolbar's "selected" highlighted background.
 *
 * Each click restarts the daemon for every open project so markers appear/disappear immediately
 * instead of waiting for the next edit.
 */
class ToggleHeatmapAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val settings = HotPathSettings.getInstance().state
        settings.enabled = !settings.enabled
        // Repaint open editors now: re-run highlighting (which recomputes when on, clears when off).
        // restart(requestor) is the non-deprecated form (the no-arg restart() was deprecated in
        // 2025.3); we pass this action as the requestor.
        for (project in ProjectManager.getInstance().openProjects) {
            if (!project.isDisposed) {
                DaemonCodeAnalyzer.getInstance(project).restart(this)
            }
        }
    }

    override fun update(e: AnActionEvent) {
        val on = HotPathSettings.getInstance().state.enabled
        // Label reflects what the click will do; icon reflects current state.
        e.presentation.text = if (on) "Hide Hot Path Heatmap" else "Show Hot Path Heatmap"
        e.presentation.icon = if (on) ON_ICON else OFF_ICON
    }

    private companion object {
        val ON_ICON = IconLoader.getIcon("/icons/heatmap.svg", ToggleHeatmapAction::class.java)
        val OFF_ICON = IconLoader.getIcon("/icons/heatmap_off.svg", ToggleHeatmapAction::class.java)
    }
}
