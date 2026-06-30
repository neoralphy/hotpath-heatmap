package com.hotpath.heatmap.model

import com.intellij.ui.JBColor
import java.awt.Color

/**
 * Severity bands for an estimated call-site cost, per the spec:
 * `0-2 none · 3-5 low · 6-8 medium · 9-12 high · 13+ very high`.
 */
enum class Severity(val displayName: String, val minScore: Int) {
    NONE("none", 0),
    LOW("low", 3),
    MEDIUM("medium", 6),
    HIGH("high", 9),
    VERY_HIGH("very high", 13);

    /** Whether anything (the gutter score badge) should be drawn at all for this band. */
    val isHighlighted: Boolean get() = this != NONE

    /**
     * Color for the numeric score badge painted in the editor gutter (left of the code, by the
     * line numbers). The estimate shows only here — no inline marker touches the code text — so
     * a warm yellow → orange → red heat ramp is safe to use: it lives in the gutter and never
     * collides with IDE diagnostics, which paint wavy underlines/highlights inline. Tuned to
     * stay legible against the neutral gutter background in both light and dark themes.
     */
    fun gutterForeground(): JBColor? = when (this) {
        NONE -> null
        LOW -> JBColor(Color(0xBF, 0x90, 0x00), Color(0xE6, 0xC8, 0x4A))
        MEDIUM -> JBColor(Color(0xC2, 0x71, 0x0A), Color(0xF0, 0xA2, 0x4A))
        HIGH -> JBColor(Color(0xC8, 0x48, 0x1E), Color(0xF0, 0x83, 0x4A))
        VERY_HIGH -> JBColor(Color(0xBF, 0x24, 0x19), Color(0xF0, 0x6A, 0x5A))
    }

    companion object {
        /** Maps a raw cost score onto the highest band whose [minScore] it reaches. */
        fun fromScore(score: Int): Severity =
            entries.lastOrNull { score >= it.minScore } ?: NONE
    }
}
