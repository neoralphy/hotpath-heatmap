package com.hotpath.heatmap.model

import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.ui.JBColor
import java.awt.Color
import java.awt.Font

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

    /** Whether anything (inline marker + gutter score badge) should be drawn for this band. */
    val isHighlighted: Boolean get() = this != NONE

    /**
     * Inline marker for the call name: a bold, *straight* underline colored by severity. A
     * straight underline is deliberately distinct from the *wavy* underlines IDE diagnostics
     * use (warnings/errors/typos). It lives in the editor's shared "underline slot", and the
     * annotation is emitted at [com.intellij.lang.annotation.HighlightSeverity.INFORMATION] —
     * the lowest layer — so on any token that also carries a warning/error, that diagnostic's
     * underline wins the slot and ours is suppressed. We therefore never double-underline a
     * token nor paint over a diagnostic. Only the underline is set; the text is left untouched.
     */
    fun textAttributes(): TextAttributes? {
        val color = gutterForeground() ?: return null
        return TextAttributes(null, null, color, EffectType.BOLD_LINE_UNDERSCORE, Font.PLAIN)
    }

    /**
     * Color shared by the inline underline marker and the numeric score badge painted in the
     * editor gutter (left of the code, by the line numbers). A warm yellow → orange → red heat
     * ramp: intuitive as "heat", and on a *straight* underline (plus the gutter) it does not
     * read as a diagnostic, whose underlines are wavy. Tuned to stay legible against the
     * neutral gutter background in both light and dark themes.
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
