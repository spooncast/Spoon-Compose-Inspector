package com.spoonlabs.composeinspector.demo.theme

import android.content.Context
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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

/** Material3 토큰 등록 예시 — light/dark 모두 등록 */
fun registerMaterial3Tokens(context: Context) {
    val light = lightColorScheme()
    val dark = darkColorScheme()

    ComposeInspector.setDesignTokens(
        context = context,
        colors = listOf(light, dark),
        dimen = DemoDimensions,
        typo = DemoTypography,
    )
}

fun clearTokens() {
    ComposeInspector.setColorTokens(emptyMap())
    ComposeInspector.setDimensionTokens(emptyMap())
    ComposeInspector.setTypographyTokens(emptyMap())
}
