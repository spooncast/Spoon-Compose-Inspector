package com.spoonlabs.composeinspector.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spoonlabs.composeinspector.ComposeInspector
import com.spoonlabs.composeinspector.demo.theme.DemoColors
import com.spoonlabs.composeinspector.demo.theme.DemoDimensions
import com.spoonlabs.composeinspector.demo.theme.DemoTypography

@Composable
fun InspectorActivityDemoScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DemoColors.background)
            .verticalScroll(rememberScrollState())
            .padding(DemoDimensions.paddingM),
        verticalArrangement = Arrangement.spacedBy(DemoDimensions.spacingM),
    ) {
        Text(
            text = stringResource(R.string.inspector_demo_title),
            style = DemoTypography.titleLarge,
            color = DemoColors.textPrimary,
        )

        Text(
            text = stringResource(R.string.launch_section_description),
            style = DemoTypography.bodyMedium,
            color = DemoColors.textSecondary,
        )

        InspectablePreview(
            label = stringResource(R.string.section_component_1),
            buttonText = stringResource(R.string.open_component_1),
        ) {
            SampleComponent1()
        }

        InspectablePreview(
            label = stringResource(R.string.section_component_2),
            buttonText = stringResource(R.string.open_component_2),
        ) {
            SampleComponent2()
        }

        HorizontalDivider(color = DemoColors.divider)

        Text(
            text = stringResource(R.string.code_example_label),
            style = DemoTypography.labelMedium,
            color = DemoColors.textTertiary,
        )

        CodeBox(LAUNCH_CODE_EXAMPLE)

        Spacer(modifier = Modifier.height(DemoDimensions.spacingXL))
    }
}

/**
 * 컴포넌트 미리보기 + Inspector 실행 버튼을 하나로 묶는 카드.
 * 상단에 실제 컴포넌트를 렌더링하고, 하단 버튼을 누르면 자동으로 Inspector로 실행한다.
 */
@Composable
private fun InspectablePreview(
    label: String = "Component",
    buttonText: String = "Component 열기",
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DemoDimensions.cornerRadiusM))
            .border(1.dp, DemoColors.borderDefault, RoundedCornerShape(DemoDimensions.cornerRadiusM))
            .background(DemoColors.surface),
    ) {
        Text(
            text = label,
            style = DemoTypography.labelMedium,
            color = DemoColors.textTertiary,
            modifier = Modifier.padding(
                start = DemoDimensions.paddingM,
                top = DemoDimensions.paddingS,
                end = DemoDimensions.paddingM,
            ),
        )

        Box(modifier = Modifier.padding(DemoDimensions.paddingM)) {
            content()
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(DemoColors.primary)
                .clickable { ComposeInspector.launch(context) { content() } }
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = buttonText,
                style = DemoTypography.bodyMedium,
                color = DemoColors.surface,
            )
        }
    }
}

@Composable
private fun CodeBox(code: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DemoDimensions.cornerRadiusM))
            .background(DemoColors.surfaceVariant)
            .padding(DemoDimensions.paddingM),
    ) {
        Text(
            text = code,
            color = DemoColors.textPrimary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = 16.sp,
        )
    }
}

private val LAUNCH_CODE_EXAMPLE = """
ComposeInspector.launch(context) {
    MyComponent()
}
""".trimIndent()



@Preview
@Composable
fun SampleComponent1Preview() {
    InspectablePreview(
        label = stringResource(R.string.section_component_1),
        buttonText = stringResource(R.string.open_component_1),
    ) {
        SampleComponent1()
    }
}