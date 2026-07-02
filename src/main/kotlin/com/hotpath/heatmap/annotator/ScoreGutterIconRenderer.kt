package com.hotpath.heatmap.annotator

import com.hotpath.heatmap.model.Severity
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.ui.JBColor
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import javax.swing.Icon

/**
 * Paints the numeric cost score directly in the editor gutter (left of the code, by the line
 * numbers) so the estimate is visible at a glance, not only on hover. Colour follows the
 * [Severity] band; the full breakdown is still available as the gutter tooltip.
 */
class ScoreGutterIconRenderer(
    private val score: Int,
    private val severity: Severity,
    private val tooltip: String,
) : GutterIconRenderer() {

    private val iconImpl = ScoreIcon(score.toString(), severity.gutterForeground() ?: JBColor.GRAY)

    override fun getIcon(): Icon = iconImpl

    override fun getTooltipText(): String = tooltip

    // Sit on the left edge of the gutter icon area, nearest the line numbers.
    override fun getAlignment(): Alignment = Alignment.LEFT

    override fun isNavigateAction(): Boolean = false

    // GutterIconRenderer requires value-equality so the daemon can diff highlights efficiently.
    // The tooltip is part of the identity: if only the explanation changes (same score/severity),
    // the daemon must still swap in the new renderer so the score's tooltip never goes stale.
    override fun equals(other: Any?): Boolean =
        other is ScoreGutterIconRenderer && other.score == score &&
            other.severity == severity && other.tooltip == tooltip

    override fun hashCode(): Int = (score * 31 + severity.ordinal) * 31 + tooltip.hashCode()

    /** A small right-aligned numeric badge. Width is approximated for monospace digits. */
    private class ScoreIcon(private val text: String, private val color: Color) : Icon {
        private val font = JBUI.Fonts.create(java.awt.Font.MONOSPACED, 10).asBold()
        private val pad get() = JBUIScale.scale(2)
        private val digitWidth get() = JBUIScale.scale(7)

        override fun getIconWidth(): Int = digitWidth * text.length + pad * 2

        override fun getIconHeight(): Int = JBUIScale.scale(13)

        override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
            val g2 = g.create() as Graphics2D
            try {
                GraphicsUtil.setupAntialiasing(g2)
                g2.font = font
                g2.color = color
                val fm = g2.fontMetrics
                val textY = y + (iconHeight - fm.height) / 2 + fm.ascent
                // Right-align the digits within the badge so single/double digits line up.
                val textX = x + iconWidth - pad - fm.stringWidth(text)
                g2.drawString(text, textX, textY)
            } finally {
                g2.dispose()
            }
        }
    }
}
