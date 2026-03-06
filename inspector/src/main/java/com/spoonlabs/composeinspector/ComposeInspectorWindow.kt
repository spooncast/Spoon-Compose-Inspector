package com.spoonlabs.composeinspector

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Window-level Inspector composable.
 * Activity의 content view 위에 투명 레이어로 부착되어
 * FAB + Overlay를 단일 인스턴스로 관리한다.
 */
@Composable
internal fun ComposeInspectorWindow(
    activity: Activity,
) {
    val inspectorState = remember { InspectorState() }
    val density = LocalDensity.current

    // System bar insets: FAB이 시스템바(상태바/네비게이션바) 영역으로 이동하지 않도록 제한
    val insets = WindowInsets.systemBars
    val topInsetPx = with(density) { insets.getTop(this).toFloat() }
    val bottomInsetPx = with(density) { insets.getBottom(this).toFloat() }

    // 화면 전환(뒤로가기 등) 시 포커스 해제
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                inspectorState.detectedInfo.value = null
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
    ) {
        // Inspector overlay (활성 시에만)
        if (inspectorState.enabled.value) {
            InspectorOverlay(
                state = inspectorState,
                activity = activity,
            )
        }

        // Draggable FAB
        val fabSizePx = with(density) { 40.dp.toPx() }
        val initialOffset = with(density) {
            Offset(-16.dp.toPx(), topInsetPx + 8.dp.toPx())
        }
        var fabOffset by remember(topInsetPx) { mutableStateOf(initialOffset) }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(fabOffset.x.roundToInt(), fabOffset.y.roundToInt()) }
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    if (inspectorState.enabled.value) Color(0xFF6366F1)
                    else Color(0x99374151)
                )
                .pointerInput(containerSize) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        var isDrag = false
                        var totalDrag = Offset.Zero

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break

                            if (!change.pressed) {
                                if (!isDrag) {
                                    inspectorState.toggle()
                                }
                                break
                            }

                            val delta = change.positionChange()
                            totalDrag += delta

                            if (!isDrag && totalDrag.getDistance() > viewConfiguration.touchSlop) {
                                isDrag = true
                            }

                            if (isDrag) {
                                change.consume()
                                val maxLeft = -(containerSize.width - fabSizePx)
                                val maxBottom = containerSize.height - fabSizePx - bottomInsetPx
                                fabOffset = Offset(
                                    (fabOffset.x + delta.x).coerceIn(maxLeft, 0f),
                                    (fabOffset.y + delta.y).coerceIn(topInsetPx, maxBottom),
                                )
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = InspectorIcon,
                contentDescription = "Toggle Inspector",
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private val InspectorIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Inspector",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = 2f,
        ) {
            moveTo(12f, 2f)
            arcTo(10f, 10f, 0f, true, true, 12f, 22f)
            arcTo(10f, 10f, 0f, true, true, 12f, 2f)
        }
        path(
            fill = null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = 2f,
        ) {
            moveTo(12f, 8f)
            arcTo(4f, 4f, 0f, true, true, 12f, 16f)
            arcTo(4f, 4f, 0f, true, true, 12f, 8f)
        }
        path(
            fill = null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = 2f,
        ) {
            moveTo(12f, 0f)
            lineTo(12f, 4f)
            moveTo(12f, 20f)
            lineTo(12f, 24f)
            moveTo(0f, 12f)
            lineTo(4f, 12f)
            moveTo(20f, 12f)
            lineTo(24f, 12f)
        }
    }.build()
}
