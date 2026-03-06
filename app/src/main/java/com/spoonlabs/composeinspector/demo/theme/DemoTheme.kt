package com.spoonlabs.composeinspector.demo.theme

import com.spoonlabs.composeinspector.ComposeInspector

fun registerTokens() {
    ComposeInspector.setColorTokens(ComposeInspector.buildColorMap(DemoColors))
    ComposeInspector.setDimensionTokens(ComposeInspector.buildDimensionMap(DemoDimensions))
    ComposeInspector.setTypographyTokens(ComposeInspector.buildTypoMap(DemoTypography))
}

fun clearTokens() {
    ComposeInspector.setColorTokens(emptyMap())
    ComposeInspector.setDimensionTokens(emptyMap())
    ComposeInspector.setTypographyTokens(emptyMap())
}
