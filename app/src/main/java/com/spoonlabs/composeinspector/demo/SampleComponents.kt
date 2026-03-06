package com.spoonlabs.composeinspector.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.spoonlabs.composeinspector.demo.theme.DemoColors
import com.spoonlabs.composeinspector.demo.theme.DemoDimensions
import com.spoonlabs.composeinspector.demo.theme.DemoTypography

@Composable
internal fun SampleComponent1() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .background(DemoColors.surface)
            .padding(DemoDimensions.paddingM),
        verticalArrangement = Arrangement.spacedBy(DemoDimensions.spacingM),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(width = 40.dp, height = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(DemoColors.textTertiary),
        )

        Text(
            text = stringResource(R.string.payment_title),
            style = DemoTypography.titleMedium,
            color = DemoColors.textPrimary,
        )

        PaymentOption(
            stringResource(R.string.payment_credit_card),
            stringResource(R.string.payment_credit_card_detail),
            DemoColors.primary,
        )
        PaymentOption(
            stringResource(R.string.payment_bank_transfer),
            stringResource(R.string.payment_bank_detail),
            DemoColors.secondary,
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(DemoDimensions.cornerRadiusM))
                .background(DemoColors.primary)
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.payment_button),
                style = DemoTypography.bodyLarge,
                color = DemoColors.surface,
            )
        }
    }
}

@Composable
private fun PaymentOption(title: String, subtitle: String, accentColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DemoDimensions.cornerRadiusM))
            .background(DemoColors.surfaceVariant)
            .padding(DemoDimensions.paddingM),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DemoDimensions.spacingS),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(accentColor),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = title.first().toString(),
                style = DemoTypography.bodyLarge,
                color = DemoColors.surface,
            )
        }
        Column {
            Text(text = title, style = DemoTypography.bodyLarge, color = DemoColors.textPrimary)
            Text(text = subtitle, style = DemoTypography.bodyMedium, color = DemoColors.textSecondary)
        }
    }
}

@Composable
internal fun SampleComponent2() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DemoDimensions.cornerRadiusL))
            .background(DemoColors.surface)
            .padding(DemoDimensions.paddingL),
        verticalArrangement = Arrangement.spacedBy(DemoDimensions.spacingM),
    ) {
        Text(
            text = stringResource(R.string.dialog_title),
            style = DemoTypography.titleMedium,
            color = DemoColors.textPrimary,
        )
        Text(
            text = stringResource(R.string.dialog_body),
            style = DemoTypography.bodyMedium,
            color = DemoColors.textSecondary,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(DemoDimensions.spacingS),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(DemoDimensions.cornerRadiusM))
                    .background(DemoColors.surfaceVariant)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.dialog_cancel), style = DemoTypography.bodyLarge, color = DemoColors.textPrimary)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(DemoDimensions.cornerRadiusM))
                    .background(DemoColors.error)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.dialog_confirm), style = DemoTypography.bodyLarge, color = DemoColors.surface)
            }
        }
    }
}
