package com.spoonlabs.composeinspector

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * An Activity that automatically attaches the Inspector overlay.
 * Useful for rendering components (e.g., BottomSheet/Dialog content)
 * in a standalone screen for Inspector analysis.
 *
 * Usage:
 * ```kotlin
 * class MyTestActivity : InspectorActivity() {
 *     override fun onCreate(savedInstanceState: Bundle?) {
 *         super.onCreate(savedInstanceState)
 *         setInspectorContent {
 *             MyTheme {
 *                 MyBottomSheetContent()
 *             }
 *         }
 *     }
 * }
 * ```
 */
open class InspectorActivity : ComponentActivity() {

    /**
     * Render the content and automatically attach the Inspector overlay.
     * [ComposeInspector.init] must be called beforehand.
     */
    protected fun setInspectorContent(content: @Composable () -> Unit) {
        enableEdgeToEdge()
        setContent {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding(),
            ) {
                content()
            }
        }
        if (ComposeInspector.isEnabled) {
            ComposeInspector.attachToWindow(this)
        }
    }
}
