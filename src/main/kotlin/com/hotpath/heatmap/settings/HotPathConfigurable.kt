package com.hotpath.heatmap.settings

import com.hotpath.heatmap.model.Severity
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel

/** Settings page under Settings | Tools | Hot Path Heatmap. */
class HotPathConfigurable : BoundConfigurable("Hot Path Heatmap") {

    private val state: HotPathState get() = HotPathSettings.getInstance().state

    override fun createPanel(): DialogPanel = panel {
        group("General") {
            row {
                checkBox("Enable plugin").bindSelected(state::enabled)
            }
            row("Highlight from severity:") {
                comboBox(Severity.entries.filter { it.isHighlighted })
                    .bindItem(
                        { Severity.entries.getOrElse(state.minHighlightSeverityOrdinal) { Severity.LOW } },
                        { state.minHighlightSeverityOrdinal = (it ?: Severity.LOW).ordinal },
                    )
            }
        }

        group("Traversal limits") {
            row("Max call depth:") {
                intTextField(1..20).bindIntText(state::maxCallDepth)
            }
            row("Max methods visited per call site:") {
                intTextField(1..5000).bindIntText(state::maxMethodsVisitedPerCallSite)
            }
            row("Max analysis time per file (ms):") {
                intTextField(50..10_000).bindIntText(state::maxAnalysisTimePerFileMs)
            }
            row {
                checkBox("Exclude folders marked “Excluded”")
                    .bindSelected(state::excludeMarkedExcluded)
                    .comment("Respects the IDE's Mark Directory as → Excluded (e.g. vendor/, node_modules/).")
            }
            row {
                checkBox("Exclude test source folders")
                    .bindSelected(state::excludeTestSources)
                    .comment("Respects the IDE's Mark Directory as → Test Sources Root.")
            }
        }

        group("Heuristics") {
            row {
                checkBox("Enable method-name heuristics (find, save, send, …)")
                    .bindSelected(state::enableNameHeuristics)
            }
            row {
                checkBox("Enable repository / client class heuristics")
                    .bindSelected(state::enableRepositoryClientHeuristics)
            }
            row {
                checkBox("Enable loop multiplier")
                    .bindSelected(state::enableLoopMultiplier)
            }
        }
    }
}
