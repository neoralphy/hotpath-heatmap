package com.hotpath.heatmap.model

/**
 * Sensitivity preset that maps a raw cost score onto a [Severity] band. A *lower* preset is more
 * sensitive — bands are reached at lower scores, so more call sites light up; a *higher* preset
 * raises every band boundary so only progressively more expensive call sites are flagged.
 *
 * [LOW] reproduces the plugin's original fixed thresholds; [MEDIUM] is the default.
 */
enum class ThresholdPreset(
    val displayName: String,
    val description: String,
    val lowCut: Int,
    val mediumCut: Int,
    val highCut: Int,
    val veryHighCut: Int,
) {
    LOW(
        "Low (most sensitive)",
        "Flags call sites at low scores — the original thresholds.",
        lowCut = 3, mediumCut = 6, highCut = 9, veryHighCut = 13,
    ),
    MEDIUM(
        "Medium",
        "Balanced — only moderately expensive call sites are flagged.",
        lowCut = 5, mediumCut = 9, highCut = 14, veryHighCut = 20,
    ),
    HIGH(
        "High (least sensitive)",
        "Only clearly expensive call sites are flagged.",
        lowCut = 8, mediumCut = 14, highCut = 22, veryHighCut = 32,
    );
}
