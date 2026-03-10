package com.spoonlabs.composeinspector.demo

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BasicDemoScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF9FAFB))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.basic_demo_title),
            style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 32.sp),
            color = Color(0xFF111827),
        )

        Text(
            text = stringResource(R.string.basic_demo_description),
            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp),
            color = Color(0xFF6B7280),
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.section_colors),
            style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 28.sp),
            color = Color(0xFF111827),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicColorBox(stringResource(R.string.color_indigo), Color(0xFF6366F1))
            BasicColorBox(stringResource(R.string.color_green), Color(0xFF10B981))
            BasicColorBox(stringResource(R.string.color_red), Color(0xFFEF4444))
            BasicColorBox(stringResource(R.string.color_amber), Color(0xFFF59E0B))
        }

        Text(
            text = stringResource(R.string.section_card),
            style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 28.sp),
            color = Color(0xFF111827),
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFFFFFFF))
                .border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(8.dp))
                .padding(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.card_title),
                    style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 28.sp),
                    color = Color(0xFF111827),
                )
                Text(
                    text = stringResource(R.string.basic_card_body),
                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp),
                    color = Color(0xFF6B7280),
                )
                Text(
                    text = stringResource(R.string.label_text),
                    style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp),
                    color = Color(0xFF9CA3AF),
                )
            }
        }

        Text(
            text = stringResource(R.string.section_spacing),
            style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 28.sp),
            color = Color(0xFF111827),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFFF3F4F6))
                .padding(24.dp),
        ) {
            Text(
                text = stringResource(R.string.basic_padding_container),
                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp),
                color = Color(0xFF111827),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF6366F1))
                    .padding(8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.basic_corner_radius),
                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp),
                    color = Color(0xFFFFFFFF),
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color(0xFFE5E7EB)),
        )

        Text(
            text = stringResource(R.string.section_typography_basic),
            style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 28.sp),
            color = Color(0xFF111827),
        )

        Text("24sp Bold", style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold), color = Color(0xFF111827))
        Text("20sp SemiBold", style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold), color = Color(0xFF111827))
        Text("16sp Normal", style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal), color = Color(0xFF111827))
        Text("14sp Normal", style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal), color = Color(0xFF6B7280))
        Text("12sp Medium", style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium), color = Color(0xFF9CA3AF))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color(0xFFE5E7EB)),
        )

        // TINT
        Text(
            text = "Tint",
            style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 28.sp),
            color = Color(0xFF111827),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = "Favorite",
                tint = Color(0xFFEF4444),
                modifier = Modifier.size(40.dp),
            )
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = "Star",
                tint = Color(0xFFF59E0B),
                modifier = Modifier.size(40.dp),
            )
            Image(
                painter = rememberVectorPainter(Icons.Filled.Favorite),
                contentDescription = "Tinted Image",
                colorFilter = ColorFilter.tint(Color(0xFF6366F1)),
                modifier = Modifier.size(40.dp),
            )
        }

        // OPACITY
        Text(
            text = "Opacity",
            style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 28.sp),
            color = Color(0xFF111827),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF6366F1)),
            )
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .alpha(0.5f)
                    .background(Color(0xFF6366F1)),
            )
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .alpha(0.2f)
                    .background(Color(0xFF6366F1)),
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun BasicColorBox(label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(color),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
            color = Color(0xFF6B7280),
        )
    }
}
