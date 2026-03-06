package com.spoonlabs.composeinspector

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private val TooltipBg = Color(0xF01E1E2E)
private val LabelColor = Color(0xFF94A3B8)
private val TokenColor = Color(0xFF818CF8)
private val HexColor = Color(0xFFA5B4FC)
private val ApproxColor = Color(0xFFFBBF24)
private val CrosshairColor = Color(0x806366F1)
private val PaddingTokenColor = Color(0xFF34D399)
private val PaddingValueColor = Color(0xFF6EE7B7)
private val SizeColor = Color(0xFF60A5FA)
private val TypoColor = Color(0xFFF472B6)
private val SpacingColor = Color(0xFFFBBF24)
private val CornerColor = Color(0xFFC084FC)

@Composable
internal fun InspectorOverlay(
    state: InspectorState,
    activity: Activity,
) {
    val density = LocalDensity.current
    val overlayView = LocalView.current
    var overlaySize by remember { mutableStateOf(IntSize.Zero) }
    val colorMap = ComposeInspector.colorMap
    val dimensionMap = ComposeInspector.dimensionMap
    val typoMap = ComposeInspector.typoMap

    val contentView = remember {
        activity.findViewById<View>(android.R.id.content)
    } ?: return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { overlaySize = it }
            .pointerInput(contentView) {
                detectTapGestures { offset ->
                    // 현재 하이라이트 영역 재탭 → 포커스 해제
                    val currentInfo = state.detectedInfo.value
                    if (currentInfo?.highlightBounds != null) {
                        val b = currentInfo.highlightBounds
                        if (offset.x in b.left..b.right && offset.y in b.top..b.bottom) {
                            state.detectedInfo.value = null
                            return@detectTapGestures
                        }
                    }

                    // Pixel sampling — overlay를 숨기고 content만 캡처
                    var renderedArgb = 0
                    var renderedResult = ColorResolveResult(emptyList(), false, 0)
                    if (contentView.width > 0 && contentView.height > 0) {
                        var bitmap: Bitmap? = null
                        val inspectorOverlay = ComposeInspector.getOverlayView(activity)
                        try {
                            bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
                            val canvas = AndroidCanvas(bitmap)
                            val tx = offset.x.toInt().coerceIn(0, contentView.width - 1)
                            val ty = offset.y.toInt().coerceIn(0, contentView.height - 1)
                            canvas.translate(-tx.toFloat(), -ty.toFloat())

                            // Overlay를 숨겨서 pixel sampling에서 제외
                            inspectorOverlay?.visibility = View.INVISIBLE
                            contentView.draw(canvas)

                            renderedArgb = bitmap.getPixel(0, 0)
                            renderedResult = TokenResolver.resolveColor(colorMap, renderedArgb)
                        } catch (e: Exception) {
                            Log.d("InspectorOverlay", "Pixel sampling failed", e)
                        } finally {
                            // 반드시 visibility 복원 (예외 발생해도)
                            inspectorOverlay?.visibility = View.VISIBLE
                            bitmap?.recycle()
                        }
                    }

                    // LayoutNode detection — 모든 ComposeView에서 탭 위치에 해당하는 것 찾기
                    var textColorTokens = emptyList<String>()
                    var modifierBgTokens = emptyList<String>()

                    // Overlay 자체를 제외하고 ComposeView 탐색
                    val inspectorOverlay = ComposeInspector.getOverlayView(activity)
                    val composeViews = LayoutInspector.findComposeViews(
                        contentView,
                        excludeView = inspectorOverlay,
                    )

                    // Overlay 좌표 기준점
                    val overlayLoc = IntArray(2)
                    overlayView.getLocationOnScreen(overlayLoc)

                    // 탭 위치의 screen 좌표
                    val tapScreenX = offset.x + overlayLoc[0]
                    val tapScreenY = offset.y + overlayLoc[1]

                    // 탭 위치에 해당하는 ComposeView 찾기
                    val targetComposeView = composeViews.firstOrNull { cv ->
                        val loc = IntArray(2)
                        cv.getLocationOnScreen(loc)
                        tapScreenX >= loc[0] && tapScreenX <= loc[0] + cv.width &&
                            tapScreenY >= loc[1] && tapScreenY <= loc[1] + cv.height
                    } ?: composeViews.firstOrNull()

                    var cvOffsetX = 0f
                    var cvOffsetY = 0f

                    val inspection = if (targetComposeView != null) {
                        // ComposeView의 overlay 기준 상대 위치
                        val cvLoc = IntArray(2)
                        targetComposeView.getLocationOnScreen(cvLoc)
                        cvOffsetX = (cvLoc[0] - overlayLoc[0]).toFloat()
                        cvOffsetY = (cvLoc[1] - overlayLoc[1]).toFloat()

                        // 탭 좌표를 ComposeView 로컬 좌표로 변환
                        val localOffset = Offset(
                            offset.x - cvOffsetX,
                            offset.y - cvOffsetY,
                        )

                        val result = LayoutInspector.detectAll(
                            composeView = targetComposeView,
                            position = localOffset,
                            density = density.density,
                            dimensionMap = dimensionMap,
                            typoMap = typoMap,
                        )
                        result.textColorArgb?.let { argb ->
                            val r = TokenResolver.resolveColor(colorMap, argb)
                            if (r.exact || r.distance < 15) textColorTokens = r.tokens
                        }
                        result.modifierBgArgb?.let { argb ->
                            val r = TokenResolver.resolveColor(colorMap, argb)
                            if (r.exact || r.distance < 15) modifierBgTokens = r.tokens
                        }
                        result
                    } else {
                        null
                    }

                    // Highlight bounds를 overlay 좌표로 변환
                    val adjustedHighlightBounds = inspection?.highlightBounds?.let { bounds ->
                        Rect(
                            left = bounds.left + cvOffsetX,
                            top = bounds.top + cvOffsetY,
                            right = bounds.right + cvOffsetX,
                            bottom = bounds.bottom + cvOffsetY,
                        )
                    }

                    val newInfo = DetectedInfo(
                        position = offset,
                        renderedArgb = renderedArgb,
                        renderedHex = TokenResolver.formatHex(renderedArgb),
                        renderedColor = Color(renderedArgb),
                        renderedTokens = renderedResult.tokens,
                        isExactRenderedMatch = renderedResult.exact,
                        paddings = inspection?.paddings ?: emptyList(),
                        highlightBounds = adjustedHighlightBounds,
                        sizeDp = inspection?.sizeDp,
                        typography = inspection?.typography,
                        spacings = inspection?.spacings ?: emptyList(),
                        cornerRadius = inspection?.cornerRadius,
                        textColorArgb = inspection?.textColorArgb,
                        textColorTokens = textColorTokens,
                        modifierBgArgb = inspection?.modifierBgArgb,
                        modifierBgTokens = modifierBgTokens,
                    )
                    if (state.detectedInfo.value != newInfo) {
                        state.detectedInfo.value = newInfo
                    }
                }
            }
    ) {
        val info = state.detectedInfo.value ?: return@Box

        // Crosshair + highlight
        Canvas(modifier = Modifier.fillMaxSize()) {
            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
            drawLine(
                color = CrosshairColor,
                start = Offset(0f, info.position.y),
                end = Offset(size.width, info.position.y),
                strokeWidth = 1.dp.toPx(),
                pathEffect = dashEffect,
            )
            drawLine(
                color = CrosshairColor,
                start = Offset(info.position.x, 0f),
                end = Offset(info.position.x, size.height),
                strokeWidth = 1.dp.toPx(),
                pathEffect = dashEffect,
            )
            drawCircle(
                color = Color(0xFF6366F1),
                radius = 4.dp.toPx(),
                center = info.position,
            )
            drawCircle(
                color = Color.White,
                radius = 4.dp.toPx(),
                center = info.position,
                style = Stroke(width = 1.5f.dp.toPx()),
            )

            // Highlight innermost node bounds
            info.highlightBounds?.let { bounds ->
                drawRect(
                    color = Color(0x1A34D399),
                    topLeft = Offset(bounds.left, bounds.top),
                    size = Size(bounds.width, bounds.height),
                )
                drawRect(
                    color = Color(0xFF34D399),
                    topLeft = Offset(bounds.left, bounds.top),
                    size = Size(bounds.width, bounds.height),
                    style = Stroke(
                        width = 1.5f.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 3.dp.toPx())),
                    ),
                )
            }
        }

        // Tooltip
        InspectorTooltip(
            info = info,
            overlaySize = overlaySize,
            density = density,
        )
    }
}

@Composable
private fun InspectorTooltip(
    info: DetectedInfo,
    overlaySize: IntSize,
    density: Density,
) {
    var measuredHeight by remember(info.position) { mutableStateOf(0f) }
    val effectiveHeight = if (measuredHeight > 0f) measuredHeight else with(density) { 200.dp.toPx() }

    val tooltipY = with(density) {
        val belowY = info.position.y + 24.dp.toPx()
        val aboveY = info.position.y - effectiveHeight - 8.dp.toPx()
        val margin = 8.dp.toPx()

        when {
            belowY + effectiveHeight + margin < overlaySize.height -> belowY
            aboveY >= margin -> aboveY
            info.position.y > overlaySize.height / 2 -> margin
            else -> (overlaySize.height - effectiveHeight - margin).coerceAtLeast(margin)
        }
    }

    val tooltipX = with(density) {
        val tooltipWidth = 240.dp.toPx()
        val margin = 8.dp.toPx()
        val centerX = info.position.x - tooltipWidth / 2
        centerX.coerceIn(margin, (overlaySize.width - tooltipWidth - margin).coerceAtLeast(margin))
    }

    Column(
        modifier = Modifier
            .offset { IntOffset(tooltipX.roundToInt(), tooltipY.roundToInt()) }
            .onSizeChanged { measuredHeight = it.height.toFloat() }
            .widthIn(max = 280.dp)
            .heightIn(max = 360.dp)
            .background(TooltipBg, RoundedCornerShape(10.dp))
            .padding(14.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // === 1. SIZE ===
        info.sizeDp?.let { (w, h) ->
            SectionLabel("SIZE", SizeColor)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${w} x ${h} dp",
                color = SizeColor,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
            )
        }

        // === 2. PADDING (토큰 매칭되는 것만, 레벨별 그룹) ===
        val tokenPaddings = info.paddings.filter {
            it.matchingTokens.any { t -> t.startsWith("padding") }
        }
        if (tokenPaddings.isNotEmpty()) {
            val byLevel = tokenPaddings.groupBy { it.level }
            byLevel.forEach { (level, paddings) ->
                if (info.sizeDp != null || byLevel.keys.first() != level) {
                    Spacer(modifier = Modifier.height(10.dp))
                }
                SectionLabel("$level PADDING", PaddingTokenColor)
                Spacer(modifier = Modifier.height(4.dp))
                paddings.forEach { padding ->
                    val shortTokens = padding.matchingTokens
                        .filter { it.startsWith("padding") }
                        .map { it.removePrefix("padding") }
                    if (shortTokens.isNotEmpty()) {
                        Text(
                            text = "${padding.direction}: ${shortTokens.joinToString(", ")} (${padding.dpValue.roundToInt()}dp)",
                            color = PaddingValueColor,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(vertical = 1.dp),
                        )
                    }
                }
            }
        }

        // === 3. SPACING (padding 바로 아래) ===
        if (info.spacings.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            SectionLabel("SPACING", SpacingColor)
            Spacer(modifier = Modifier.height(4.dp))
            info.spacings.forEach { spacing ->
                val shortTokens = spacing.matchingTokens.map { it.removePrefix("spacing") }
                Text(
                    text = "${spacing.direction}: ${shortTokens.joinToString(", ")} (${spacing.dpValue.roundToInt()}dp)",
                    color = SpacingColor.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(vertical = 1.dp),
                )
            }
        }

        // === 4. TYPOGRAPHY — 간략화: tokenName(sp) 형식 ===
        info.typography?.let { typo ->
            Spacer(modifier = Modifier.height(10.dp))
            SectionLabel("TYPOGRAPHY", TypoColor)
            Spacer(modifier = Modifier.height(4.dp))
            if (typo.matchingTokens.isNotEmpty()) {
                val spSize = typo.fontSizeSp.roundToInt()
                typo.matchingTokens.forEach { token ->
                    Text(
                        text = "$token(${spSize}sp)",
                        color = TypoColor,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(vertical = 1.dp),
                    )
                }
            } else {
                val lhStr = if (typo.lineHeightSp > 0f) " / lh${typo.lineHeightSp.roundToInt()}" else ""
                Text(
                    text = "${typo.fontSizeSp.roundToInt()}sp / w${typo.fontWeight}$lhStr",
                    color = TypoColor.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }

        // === 5. COLOR (pixel sampling) + TEXT COLOR (reflection) ===
        if (info.renderedArgb != 0) {
            Spacer(modifier = Modifier.height(10.dp))
            val allTokens = (info.renderedTokens + info.modifierBgTokens).distinct()
            val displayTokens = prioritizeColorTokens(allTokens).take(5)
            val hasExactToken = info.isExactRenderedMatch || info.modifierBgArgb != null
            SectionLabel("COLOR", if (hasExactToken) LabelColor else ApproxColor)
            Spacer(modifier = Modifier.height(4.dp))
            ColorSwatch(argb = info.renderedArgb, hex = info.renderedHex, color = info.renderedColor)
            if (displayTokens.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                displayTokens.forEach { token ->
                    Text(text = token, color = TokenColor, fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(vertical = 1.dp))
                }
            }
        } else if (info.modifierBgArgb != null) {
            Spacer(modifier = Modifier.height(10.dp))
            val displayTokens = prioritizeColorTokens(info.modifierBgTokens).take(5)
            ColorRow(label = "COLOR", argb = info.modifierBgArgb, tokens = displayTokens)
        }

        // TEXT COLOR — text 접두사 토큰 우선
        info.textColorArgb?.let { textArgb ->
            Spacer(modifier = Modifier.height(8.dp))
            val textTokens = info.textColorTokens.filter { it.startsWith("text", ignoreCase = true) }
            val displayTokens = textTokens.ifEmpty { info.textColorTokens }.take(5)
            ColorRow(
                label = "TEXT COLOR",
                argb = textArgb,
                tokens = displayTokens,
            )
        }

        // === 6. CORNER RADIUS ===
        info.cornerRadius?.let { corner ->
            Spacer(modifier = Modifier.height(10.dp))
            SectionLabel("CORNER RADIUS", CornerColor)
            Spacer(modifier = Modifier.height(4.dp))
            val shortTokens = corner.matchingTokens.map { it.removePrefix("cornerRadius") }
            val displayText = if (shortTokens.isNotEmpty()) {
                "${shortTokens.joinToString(", ")} (${corner.radiusDp.roundToInt()}dp)"
            } else {
                "${corner.radiusDp.roundToInt()}dp"
            }
            Text(
                text = displayText,
                color = CornerColor.copy(alpha = 0.8f),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun ColorRow(
    label: String,
    argb: Int,
    tokens: List<String>,
) {
    SectionLabel(label, LabelColor)
    Spacer(modifier = Modifier.height(4.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(RoundedCornerShape(3.dp))
                .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(3.dp))
                .background(Color(argb))
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = TokenResolver.formatHex(argb),
            color = HexColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
    }
    if (tokens.isNotEmpty()) {
        Spacer(modifier = Modifier.height(2.dp))
        tokens.forEach { line ->
            Text(
                text = line,
                color = TokenColor,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(vertical = 1.dp),
            )
        }
    }
}

@Composable
private fun ColorSwatch(argb: Int, hex: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(RoundedCornerShape(3.dp))
                .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(3.dp))
                .background(color)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = hex,
            color = HexColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun SectionLabel(text: String, color: Color) {
    Text(
        text = text,
        color = color,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        letterSpacing = 1.sp,
    )
}

private fun prioritizeColorTokens(tokens: List<String>): List<String> {
    if (tokens.size <= 1) return tokens
    val prefixes = ComposeInspector.colorPriorityPrefixes
    val (priority, rest) = tokens.partition { token ->
        prefixes.any { prefix -> token.startsWith(prefix, ignoreCase = true) }
    }
    return priority + rest
}
