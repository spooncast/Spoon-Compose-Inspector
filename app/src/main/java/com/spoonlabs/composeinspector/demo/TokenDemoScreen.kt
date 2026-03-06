package com.spoonlabs.composeinspector.demo

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.spoonlabs.composeinspector.demo.theme.DemoColors
import com.spoonlabs.composeinspector.demo.theme.DemoDimensions
import com.spoonlabs.composeinspector.demo.theme.DemoTypography

@Composable
fun TokenDemoScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DemoColors.background)
            .verticalScroll(rememberScrollState())
            .padding(DemoDimensions.paddingM),
        verticalArrangement = Arrangement.spacedBy(DemoDimensions.spacingM),
    ) {
        Text(
            text = stringResource(R.string.token_demo_title),
            style = DemoTypography.titleLarge,
            color = DemoColors.textPrimary,
        )

        Text(
            text = stringResource(R.string.token_demo_description),
            style = DemoTypography.bodyMedium,
            color = DemoColors.textSecondary,
        )

        Spacer(modifier = Modifier.height(DemoDimensions.spacingS))

        Text(
            text = stringResource(R.string.section_colors),
            style = DemoTypography.titleMedium,
            color = DemoColors.textPrimary,
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(DemoDimensions.spacingS),
        ) {
            ColorBox(stringResource(R.string.color_primary), DemoColors.primary)
            ColorBox(stringResource(R.string.color_secondary), DemoColors.secondary)
            ColorBox(stringResource(R.string.color_error), DemoColors.error)
            ColorBox(stringResource(R.string.color_warning), DemoColors.warning)
        }

        Text(
            text = stringResource(R.string.section_surface_border),
            style = DemoTypography.titleMedium,
            color = DemoColors.textPrimary,
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(DemoDimensions.cornerRadiusM))
                .background(DemoColors.surface)
                .border(1.dp, DemoColors.borderDefault, RoundedCornerShape(DemoDimensions.cornerRadiusM))
                .padding(DemoDimensions.paddingM),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(DemoDimensions.spacingS)) {
                Text(
                    text = stringResource(R.string.card_title),
                    style = DemoTypography.titleMedium,
                    color = DemoColors.textPrimary,
                )
                Text(
                    text = stringResource(R.string.card_body),
                    style = DemoTypography.bodyMedium,
                    color = DemoColors.textSecondary,
                )
                Text(
                    text = stringResource(R.string.label_text),
                    style = DemoTypography.labelMedium,
                    color = DemoColors.textTertiary,
                )
            }
        }

        Text(
            text = stringResource(R.string.section_spacing),
            style = DemoTypography.titleMedium,
            color = DemoColors.textPrimary,
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(DemoDimensions.cornerRadiusS))
                .background(DemoColors.surfaceVariant)
                .padding(DemoDimensions.paddingL),
        ) {
            Text(
                text = stringResource(R.string.padding_container),
                style = DemoTypography.bodyMedium,
                color = DemoColors.textPrimary,
            )
            Spacer(modifier = Modifier.height(DemoDimensions.spacingM))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(DemoDimensions.cornerRadiusL))
                    .background(DemoColors.primary)
                    .padding(DemoDimensions.paddingS),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.corner_radius),
                    style = DemoTypography.bodyMedium,
                    color = DemoColors.surface,
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(DemoColors.divider),
        )

        Text(
            text = stringResource(R.string.section_typography),
            style = DemoTypography.titleMedium,
            color = DemoColors.textPrimary,
        )

        Text("titleLarge (24sp Bold)", style = DemoTypography.titleLarge, color = DemoColors.textPrimary)
        Text("titleMedium (20sp SemiBold)", style = DemoTypography.titleMedium, color = DemoColors.textPrimary)
        Text("bodyLarge (16sp Normal)", style = DemoTypography.bodyLarge, color = DemoColors.textPrimary)
        Text("bodyMedium (14sp Normal)", style = DemoTypography.bodyMedium, color = DemoColors.textSecondary)
        Text("labelMedium (12sp Medium)", style = DemoTypography.labelMedium, color = DemoColors.textTertiary)

        HorizontalDivider(color = DemoColors.divider)

        Text(
            text = stringResource(R.string.section_component_1),
            style = DemoTypography.titleMedium,
            color = DemoColors.textPrimary,
        )

        SampleComponent1()

        HorizontalDivider(color = DemoColors.divider)

        Text(
            text = stringResource(R.string.section_component_2),
            style = DemoTypography.titleMedium,
            color = DemoColors.textPrimary,
        )

        SampleComponent2()

        Spacer(modifier = Modifier.height(DemoDimensions.spacingXL))
    }
}

@Composable
private fun ColorBox(label: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(DemoDimensions.cornerRadiusM))
                .background(color),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = DemoTypography.labelMedium,
            color = DemoColors.textSecondary,
        )
    }
}
