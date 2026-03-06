package com.spoonlabs.composeinspector

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color

internal data class DetectedInfo(
    val position: Offset,
    // Rendered pixel color (fallback)
    val renderedArgb: Int,
    val renderedHex: String,
    val renderedColor: Color,
    val renderedTokens: List<String>,
    val isExactRenderedMatch: Boolean,
    // Padding (auto-detected from LayoutNode reflection)
    val paddings: List<AutoDetectedPadding>,
    // Innermost node bounds for highlight
    val highlightBounds: Rect?,
    // Size (w x h dp)
    val sizeDp: Pair<Int, Int>?,
    // Typography
    val typography: TypographyResult?,
    // Spacing (sibling gap)
    val spacings: List<SpacingResult>,
    // Corner Radius
    val cornerRadius: CornerRadiusResult?,
    // Text color (from reflection)
    val textColorArgb: Int?,
    val textColorTokens: List<String>,
    // Modifier background color (from reflection)
    val modifierBgArgb: Int?,
    val modifierBgTokens: List<String>,
)

internal class InspectorState {
    var enabled = mutableStateOf(false)
        private set
    var detectedInfo = mutableStateOf<DetectedInfo?>(null)
        private set

    fun toggle() {
        enabled.value = !enabled.value
        if (!enabled.value) {
            detectedInfo.value = null
        }
    }
}
