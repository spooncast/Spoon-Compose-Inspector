package com.spoonlabs.composeinspector.demo.theme

import android.content.Context
import com.spoonlabs.composeinspector.ComposeInspector

fun registerTokens() {
    ComposeInspector.setColorTokens(ComposeInspector.buildColorMap(DemoColors))
    ComposeInspector.setDimensionTokens(ComposeInspector.buildDimensionMap(DemoDimensions))
    ComposeInspector.setTypographyTokens(ComposeInspector.buildTypoMap(DemoTypography))
}

/** setDesignTokens() 단일 호출 예시 */
fun registerTokensSimple(context: Context) {
    ComposeInspector.setDesignTokens(context, DemoColors, DemoDimensions, DemoTypography)
}

fun clearTokens() {
    ComposeInspector.setColorTokens(emptyMap())
    ComposeInspector.setDimensionTokens(emptyMap())
    ComposeInspector.setTypographyTokens(emptyMap())
}
