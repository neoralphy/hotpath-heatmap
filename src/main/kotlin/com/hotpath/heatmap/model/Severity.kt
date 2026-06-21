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
     * Editor text attributes for this band: a rounded-box outline drawn around the call name,
     * colored by severity (amber → orange → red). A border reads as a deliberate marker and
     * stays legible against any theme, unlike a background fill that can wash out. The text
     * itself is left untouched.
     */
    fun textAttributes(): TextAttributes? {
        val color = gutterForeground() ?: return null
        return TextAttributes(null, null, color, EffectType.ROUNDED_BOX, Font.PLAIN)
    }

    /**
     * Saturated foreground color for the score badge painted in the editor gutter. Tuned to
     * stay legible against the neutral gutter background in both light and dark themes.
     */
    fun gutterForeground(): JBColor? = when (this) {
        NONE -> null
        LOW -> JBColor(Color(0xB8, 0x8A, 0x00), Color(0xD0, 0xB0, 0x40))
        MEDIUM -> JBColor(Color(0xC4, 0x7A, 0x00), Color(0xE0, 0xB1, 0x55))
        HIGH -> JBColor(Color(0xD8, 0x5A, 0x00), Color(0xF0, 0x97, 0x4A))
        VERY_HIGH -> JBColor(Color(0xC0, 0x2A, 0x2A), Color(0xF0, 0x6A, 0x6A))
    }

    companion object {
        /** Maps a raw cost score onto the highest band whose [minScore] it reaches. */
        fun fromScore(score: Int): Severity =
            entries.lastOrNull { score >= it.minScore } ?: NONE
    }
}
