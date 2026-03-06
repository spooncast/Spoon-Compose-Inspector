package com.spoonlabs.composeinspector

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A card that renders a component preview with an "Open in Inspector" button.
 * Tapping the button launches the component in a standalone Inspector screen.
 *
 * ```kotlin
 * @Preview
 * @Composable
 * private fun MyComponentPreview() {
 *     InspectablePreview(label = "MyComponent") {
 *         MyComponent()
 *     }
 * }
 * ```
 *
 * @param label Title displayed at the top of the card.
 * @param buttonText Text displayed on the launch button.
 * @param content The composable to preview and inspect.
 */
@Composable
fun InspectablePreview(
    label: String = "Component",
    buttonText: String = "Open in Inspector",
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(12.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, Color(0xFFE5E7EB), shape)
            .background(Color.White),
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF9CA3AF),
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp),
        )

        Box(modifier = Modifier.padding(16.dp)) {
            content()
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF6366F1))
                .clickable { ComposeInspector.launch(context) { content() } }
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = buttonText,
                fontSize = 14.sp,
                color = Color.White,
            )
        }
    }
}
