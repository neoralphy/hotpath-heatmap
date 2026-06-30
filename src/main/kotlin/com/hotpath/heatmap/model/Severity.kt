package com.hotpath.heatmap.model

import com.intellij.ui.JBColor
import java.awt.Color
import java.awt.Font
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.TextAttributes

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

    /** Whether a highlight should be drawn at all for this band. */
    val isHighlighted: Boolean get() = this != NONE

    /**
     * Editor text attributes for this band: a dotted underline beneath the call name, colored
     * by severity (amber → orange → magenta → purple). A dotted line reads as an informational
     * marker rather than an error — a solid red box was too easily mistaken for a problem — and
     * the warm-to-purple ramp tops out at purple instead of an alarming red. The text itself is
     * left untouched.
     */
    fun textAttributes(): TextAttributes? {
        val color = gutterForeground() ?: return null
        return TextAttributes(null, null, color, EffectType.BOLD_DOTTED_LINE, Font.PLAIN)
    }

    /**
     * Saturated foreground color for the score badge painted in the editor gutter (shared with
     * the dotted call-site marker). Warm-to-purple ramp: amber → orange → magenta → purple,
     * tuned to stay legible against the neutral gutter background in both light and dark themes.
     */
    fun gutterForeground(): JBColor? = when (this) {
        NONE -> null
        LOW -> JBColor(Color(0xC9, 0xA1, 0x00), Color(0xE0, 0xC0, 0x4A))
        MEDIUM -> JBColor(Color(0xD8, 0x73, 0x0A), Color(0xF0, 0x97, 0x4A))
        HIGH -> JBColor(Color(0xC0, 0x3A, 0x8A), Color(0xE0, 0x60, 0xA8))
        VERY_HIGH -> JBColor(Color(0x7E, 0x3F, 0xF2), Color(0xA7, 0x7B, 0xFF))
    }

    companion object {
        /** Maps a raw cost score onto the highest band whose [minScore] it reaches. */
        fun fromScore(score: Int): Severity =
            entries.lastOrNull { score >= it.minScore } ?: NONE
    }
}
