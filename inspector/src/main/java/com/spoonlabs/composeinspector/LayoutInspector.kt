package com.spoonlabs.composeinspector

import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import java.lang.reflect.Field
import java.lang.reflect.Method
import android.util.Log
import kotlin.math.abs
import kotlin.math.roundToInt

private const val TAG = "LayoutInspector"

/**
 * LayoutNode reflection 기반 자동 패딩 감지.
 * AndroidComposeView → root LayoutNode → 트리 순회로
 * 코드 변경 없이 패딩을 자동 감지한다.
 */
internal object LayoutInspector {

    private const val MAX_TREE_DEPTH = 50

    // --- Reflection lazy cache ---

    private val layoutNodeClass: Class<*>? by lazy {
        runCatching { Class.forName("androidx.compose.ui.node.LayoutNode") }.getOrNull()
    }

    private val childrenMethod: Method? by lazy {
        layoutNodeClass?.let { cls ->
            cls.methods.firstOrNull { it.name == "getChildren\$ui_release" }
                ?: cls.methods.firstOrNull { it.name.contains("getChildren") }
        }?.apply { isAccessible = true }
    }

    private val childrenField: Field? by lazy {
        layoutNodeClass?.let { cls ->
            runCatching { cls.getDeclaredField("_children").apply { isAccessible = true } }.getOrNull()
        }
    }

    private val coordinatesMethod: Method? by lazy {
        layoutNodeClass?.let { cls ->
            runCatching { cls.getDeclaredMethod("getCoordinates").apply { isAccessible = true } }.getOrNull()
        }
    }

    private val boundsInRootMethod: Method? by lazy {
        runCatching {
            val extClass = Class.forName("androidx.compose.ui.layout.LayoutCoordinatesKt")
            val coordsClass = Class.forName("androidx.compose.ui.layout.LayoutCoordinates")
            extClass.getDeclaredMethod("boundsInRoot", coordsClass)
        }.getOrNull()
    }

    private val positionInRootMethod: Method? by lazy {
        runCatching {
            val extClass = Class.forName("androidx.compose.ui.layout.LayoutCoordinatesKt")
            val coordsClass = Class.forName("androidx.compose.ui.layout.LayoutCoordinates")
            extClass.getDeclaredMethod("positionInRoot", coordsClass)
        }.getOrNull()
    }

    private val modifierInfoMethod: Method? by lazy {
        layoutNodeClass?.let { cls ->
            cls.methods.firstOrNull {
                it.name == "getModifierInfo" || it.name == "getModifierInfo\$ui_release"
            }?.apply { isAccessible = true }
        }
    }

    private val rootField: Field? by lazy {
        runCatching {
            Class.forName("androidx.compose.ui.platform.AndroidComposeView")
                .getDeclaredField("root").apply { isAccessible = true }
        }.getOrNull()
    }

    private val modifierGetterCache = java.util.Collections.synchronizedMap(
        java.util.WeakHashMap<Class<*>, Method?>()
    )

    private fun getModifierGetter(modInfoClass: Class<*>): Method? {
        return modifierGetterCache.getOrPut(modInfoClass) {
            modInfoClass.methods.firstOrNull { it.name == "getModifier" }
        }
    }

    fun findComposeView(root: View): View? {
        if (root::class.java.simpleName == "AndroidComposeView") return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                val found = findComposeView(root.getChildAt(i))
                if (found != null) return found
            }
        }
        return null
    }

    fun findComposeViews(root: View, excludeView: View? = null): List<View> {
        val result = mutableListOf<View>()
        collectComposeViews(root, result, excludeView)
        return result
    }

    private fun collectComposeViews(view: View, result: MutableList<View>, excludeView: View?) {
        if (view === excludeView) return
        if (view::class.java.simpleName == "AndroidComposeView") {
            result.add(view)
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                collectComposeViews(view.getChildAt(i), result, excludeView)
            }
        }
    }

    fun detectAll(
        composeView: View,
        position: Offset,
        density: Float,
        dimensionMap: Map<Float, List<String>>,
        typoMap: Map<String, List<String>>,
    ): InspectionResult {
        val rootNode = getRootLayoutNode(composeView)
            ?: return InspectionResult(emptyList(), null, null, null, emptyList(), null, null, null)

        val allNodes = mutableListOf<Pair<Any, Rect>>()
        collectNodesAt(rootNode, position, allNodes)
        allNodes.sortBy { it.second.width * it.second.height }

        val highlightBounds = allNodes.firstOrNull()?.second
        val sizeDp = highlightBounds?.let {
            val wDp = (it.width / density).roundToInt()
            val hDp = (it.height / density).roundToInt()
            Pair(wDp, hDp)
        }

        val paddings = mutableListOf<AutoDetectedPadding>()
        for ((index, pair) in allNodes.withIndex()) {
            val (node, _) = pair
            val level = when (index) {
                0 -> "SELF"
                1 -> "PARENT"
                else -> "ANCESTOR $index"
            }
            extractPaddingFromModifiers(node, density, dimensionMap, paddings, level)
        }
        if (paddings.isEmpty() && allNodes.size >= 2) {
            val (_, childBounds) = allNodes[0]
            val (_, parentBounds) = allNodes[1]
            extractPadding(parentBounds, childBounds, density, dimensionMap, paddings, "LAYOUT")
        }

        var typography: TypographyResult? = null
        for ((node, _) in allNodes) {
            typography = extractTypoFromModifiers(node, typoMap)
            if (typography != null) break
        }

        var textColorArgb: Int? = null
        for ((node, _) in allNodes) {
            textColorArgb = extractTextColorFromModifiers(node)
            if (textColorArgb != null) break
        }

        var modifierBgArgb: Int? = null
        for ((node, _) in allNodes) {
            modifierBgArgb = extractBgColorFromNode(node)
            if (modifierBgArgb != null) break
        }

        val cornerRadius = allNodes.firstOrNull()?.first?.let {
            extractCornerRadius(it, density, dimensionMap)
        }

        val spacings = if (allNodes.size >= 2) {
            detectSpacingFromNodes(rootNode, allNodes, density, dimensionMap)
        } else {
            emptyList()
        }

        return InspectionResult(
            paddings = paddings,
            highlightBounds = highlightBounds,
            sizeDp = sizeDp,
            typography = typography,
            spacings = spacings,
            cornerRadius = cornerRadius,
            textColorArgb = textColorArgb,
            modifierBgArgb = modifierBgArgb,
        )
    }

    private fun detectSpacingFromNodes(
        rootNode: Any,
        allNodes: List<Pair<Any, Rect>>,
        density: Float,
        dimensionMap: Map<Float, List<String>>,
    ): List<SpacingResult> {
        val parent = allNodes.getOrNull(1) ?: return emptyList()
        val parentNode = findNodeContaining(rootNode, parent.second) ?: return emptyList()
        val siblings = getChildren(parentNode)
        val siblingBounds = siblings.mapNotNull { child ->
            getNodeBounds(child)?.let { b -> if (b.width > 0 && b.height > 0) b else null }
        }.sortedBy { it.left + it.top }

        if (siblingBounds.size < 2) return emptyList()

        val results = mutableListOf<SpacingResult>()
        for (i in 0 until siblingBounds.size - 1) {
            val curr = siblingBounds[i]
            val next = siblingBounds[i + 1]
            val hGapPx = next.left - curr.right
            val vGapPx = next.top - curr.bottom

            if (abs(hGapPx) > abs(vGapPx) && hGapPx > 0) {
                val gapDp = (hGapPx / density).roundToInt().toFloat()
                if (gapDp > 0f) {
                    val tokens = TokenResolver.resolveDimension(dimensionMap, gapDp, "spacing")
                    if (tokens.isNotEmpty()) results.add(SpacingResult("horizontal", gapDp, tokens))
                }
            } else if (vGapPx > 0) {
                val gapDp = (vGapPx / density).roundToInt().toFloat()
                if (gapDp > 0f) {
                    val tokens = TokenResolver.resolveDimension(dimensionMap, gapDp, "spacing")
                    if (tokens.isNotEmpty()) results.add(SpacingResult("vertical", gapDp, tokens))
                }
            }
        }
        return results.distinctBy { "${it.direction}|${it.dpValue}" }
    }

    private fun getRootLayoutNode(composeView: View): Any? {
        return try {
            rootField?.get(composeView)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get root LayoutNode", e)
            null
        }
    }

    private fun getNodeBounds(node: Any): Rect? {
        return try {
            val coords = coordinatesMethod?.invoke(node) ?: return null
            val isAttached = try {
                val attachedMethod = coords::class.java.getDeclaredMethod("isAttached")
                attachedMethod.invoke(coords) as? Boolean ?: false
            } catch (_: Exception) {
                true
            }
            if (!isAttached) return null

            try {
                boundsInRootMethod?.invoke(null, coords) as? Rect
                    ?: computeBoundsFromPositionAndSize(coords)
            } catch (_: Exception) {
                computeBoundsFromPositionAndSize(coords)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun computeBoundsFromPositionAndSize(coords: Any): Rect? {
        return try {
            val position = positionInRootMethod?.invoke(null, coords) as? Offset ?: return null
            val sizeMethod = coords::class.java.methods.firstOrNull {
                it.name.startsWith("getSize") && it.parameterCount == 0
            } ?: return null
            val sizeResult = sizeMethod.invoke(coords)
            val w: Int
            val h: Int
            if (sizeResult is Long) {
                w = (sizeResult shr 32).toInt()
                h = (sizeResult and 0xFFFFFFFFL).toInt()
            } else {
                val wMethod = sizeResult?.javaClass?.methods?.firstOrNull { it.name == "getWidth" }
                val hMethod = sizeResult?.javaClass?.methods?.firstOrNull { it.name == "getHeight" }
                w = (wMethod?.invoke(sizeResult) as? Int) ?: return null
                h = (hMethod?.invoke(sizeResult) as? Int) ?: return null
            }
            Rect(position.x, position.y, position.x + w, position.y + h)
        } catch (_: Exception) {
            null
        }
    }

    private fun getChildren(node: Any): List<Any> {
        return try {
            val m = childrenMethod
            if (m != null) {
                @Suppress("UNCHECKED_CAST")
                (m.invoke(node) as? Iterable<Any>)?.toList() ?: emptyList()
            } else {
                val f = childrenField
                if (f != null) {
                    @Suppress("UNCHECKED_CAST")
                    (f.get(node) as? Iterable<Any>)?.toList() ?: emptyList()
                } else {
                    emptyList()
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun extractPadding(
        parent: Rect,
        child: Rect,
        density: Float,
        dimensionMap: Map<Float, List<String>>,
        result: MutableList<AutoDetectedPadding>,
        level: String,
    ) {
        val startPx = child.left - parent.left
        val topPx = child.top - parent.top
        val endPx = parent.right - child.right
        val bottomPx = parent.bottom - child.bottom

        fun toDp(px: Float): Float {
            val dp = px / density
            return if (abs(dp) < 0.5f) 0f else dp.roundToInt().toFloat()
        }

        val s = toDp(startPx).coerceAtLeast(0f)
        val t = toDp(topPx).coerceAtLeast(0f)
        val e = toDp(endPx).coerceAtLeast(0f)
        val b = toDp(bottomPx).coerceAtLeast(0f)

        if (s == 0f && t == 0f && e == 0f && b == 0f) return

        val isAllSame = s == t && t == e && e == b
        val isHorizontalSame = s == e
        val isVerticalSame = t == b

        fun resolve(dp: Float) = TokenResolver.resolveDimension(dimensionMap, dp)

        if (isAllSame && s > 0f) {
            result.add(AutoDetectedPadding(level, "all", s, resolve(s)))
        } else if (isHorizontalSame && isVerticalSame) {
            if (s > 0f) result.add(AutoDetectedPadding(level, "horizontal", s, resolve(s)))
            if (t > 0f) result.add(AutoDetectedPadding(level, "vertical", t, resolve(t)))
        } else {
            if (s > 0f) result.add(AutoDetectedPadding(level, "start", s, resolve(s)))
            if (t > 0f) result.add(AutoDetectedPadding(level, "top", t, resolve(t)))
            if (e > 0f) result.add(AutoDetectedPadding(level, "end", e, resolve(e)))
            if (b > 0f) result.add(AutoDetectedPadding(level, "bottom", b, resolve(b)))
        }
    }

    private fun extractPaddingFromModifiers(
        node: Any,
        density: Float,
        dimensionMap: Map<Float, List<String>>,
        result: MutableList<AutoDetectedPadding>,
        level: String,
    ) {
        try {
            val modInfoList = modifierInfoMethod?.invoke(node) as? List<*> ?: return

            for (modInfo in modInfoList) {
                if (modInfo == null) continue
                val modifier = getModifierGetter(modInfo::class.java)?.invoke(modInfo) ?: continue
                val modClassName = modifier::class.java.name

                val hasPaddingFields = lazy {
                    val fields = modifier::class.java.declaredFields
                    fields.any { f ->
                        val n = f.name; n.contains("start") || n.contains("top") || n.contains("end") || n.contains("bottom")
                    } && fields.any { f -> f.type == Float::class.javaPrimitiveType }
                }
                if (!modClassName.contains("Padding", ignoreCase = true) && !hasPaddingFields.value) continue

                var startDp = 0f
                var topDp = 0f
                var endDp = 0f
                var bottomDp = 0f
                var found = false

                for (field in modifier::class.java.declaredFields) {
                    field.isAccessible = true
                    val name = field.name.lowercase()
                    if (field.type == Float::class.javaPrimitiveType) {
                        val value = field.getFloat(modifier)
                        when {
                            name.contains("start") || name == "left" -> { startDp = value; found = true }
                            name.contains("top") -> { topDp = value; found = true }
                            name.contains("end") || name == "right" -> { endDp = value; found = true }
                            name.contains("bottom") -> { bottomDp = value; found = true }
                        }
                    }
                }

                if (!found) {
                    for (field in modifier::class.java.declaredFields) {
                        field.isAccessible = true
                        if (field.type == Float::class.javaPrimitiveType) {
                            val value = field.getFloat(modifier)
                            if (value > 0f) {
                                val fname = field.name.lowercase()
                                if (!fname.contains("start") && !fname.contains("top") &&
                                    !fname.contains("end") && !fname.contains("bottom") &&
                                    !fname.contains("left") && !fname.contains("right")
                                ) {
                                    startDp = value; topDp = value; endDp = value; bottomDp = value
                                    found = true
                                    break
                                }
                            }
                        }
                    }
                }

                if (!found || (startDp == 0f && topDp == 0f && endDp == 0f && bottomDp == 0f)) continue

                val s = startDp.roundToInt().toFloat()
                val t = topDp.roundToInt().toFloat()
                val e = endDp.roundToInt().toFloat()
                val b = bottomDp.roundToInt().toFloat()

                if (s == 0f && t == 0f && e == 0f && b == 0f) continue

                val isAllSame = s == t && t == e && e == b
                val isHorizontalSame = s == e
                val isVerticalSame = t == b

                fun resolve(dp: Float) = TokenResolver.resolveDimension(dimensionMap, dp)

                if (isAllSame && s > 0f) {
                    result.add(AutoDetectedPadding(level, "all", s, resolve(s)))
                } else if (isHorizontalSame && isVerticalSame) {
                    if (s > 0f) result.add(AutoDetectedPadding(level, "horizontal", s, resolve(s)))
                    if (t > 0f) result.add(AutoDetectedPadding(level, "vertical", t, resolve(t)))
                } else {
                    if (s > 0f) result.add(AutoDetectedPadding(level, "start", s, resolve(s)))
                    if (t > 0f) result.add(AutoDetectedPadding(level, "top", t, resolve(t)))
                    if (e > 0f) result.add(AutoDetectedPadding(level, "end", e, resolve(e)))
                    if (b > 0f) result.add(AutoDetectedPadding(level, "bottom", b, resolve(b)))
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "extractPaddingFromModifiers failed", e)
        }
    }

    private fun collectNodesAt(
        node: Any,
        position: Offset,
        result: MutableList<Pair<Any, Rect>>,
        depth: Int = 0,
    ) {
        if (depth > MAX_TREE_DEPTH) return
        val bounds = getNodeBounds(node) ?: return
        if (!bounds.contains(position)) return
        if (bounds.width > 0 && bounds.height > 0) {
            result.add(node to bounds)
        }
        for (child in getChildren(node)) {
            collectNodesAt(child, position, result, depth + 1)
        }
    }

    private fun findNodeContaining(node: Any, targetBounds: Rect, depth: Int = 0): Any? {
        if (depth > MAX_TREE_DEPTH) return null
        val children = getChildren(node)
        for (child in children) {
            val childBounds = getNodeBounds(child)
            if (childBounds != null && childBounds == targetBounds) return node
            val found = findNodeContaining(child, targetBounds, depth + 1)
            if (found != null) return found
        }
        return null
    }

    private fun extractTypoFromModifiers(node: Any, typoMap: Map<String, List<String>>): TypographyResult? {
        try {
            val modInfoList = modifierInfoMethod?.invoke(node) as? List<*> ?: emptyList<Any>()
            if (modInfoList.isNotEmpty()) {
                for (modInfo in modInfoList) {
                    if (modInfo == null) continue
                    val modifier = getModifierGetter(modInfo::class.java)?.invoke(modInfo) ?: continue
                    for (field in modifier::class.java.declaredFields) {
                        field.isAccessible = true
                        val value = field.get(modifier) ?: continue
                        if (value::class.java.name.contains("TextStyle")) {
                            val result = extractFromTextStyle(value, typoMap)
                            if (result != null) return result
                        }
                    }
                }
            }
        } catch (_: ReflectiveOperationException) {
            // Reflection may fail on different Compose versions
        }
        return extractTypoFromNodeFields(node, typoMap)
    }

    private fun extractTypoFromNodeFields(node: Any, typoMap: Map<String, List<String>>): TypographyResult? {
        return try {
            for (field in node::class.java.declaredFields) {
                field.isAccessible = true
                val value = field.get(node) ?: continue
                val valClassName = value::class.java.name
                if (valClassName.contains("TextDelegate") || valClassName.contains("TextFieldState")) {
                    for (innerField in value::class.java.declaredFields) {
                        innerField.isAccessible = true
                        val innerVal = innerField.get(value) ?: continue
                        if (innerVal::class.java.name.contains("TextStyle")) {
                            val result = extractFromTextStyle(innerVal, typoMap)
                            if (result != null) return result
                        }
                        if (innerVal::class.java.name.contains("TextDelegate")) {
                            for (f2 in innerVal::class.java.declaredFields) {
                                f2.isAccessible = true
                                val v2 = f2.get(innerVal) ?: continue
                                if (v2::class.java.name.contains("TextStyle")) {
                                    val result = extractFromTextStyle(v2, typoMap)
                                    if (result != null) return result
                                }
                            }
                        }
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun extractFromTextStyle(style: Any, typoMap: Map<String, List<String>>): TypographyResult? {
        return try {
            val fontSizeMethod = style::class.java.methods.firstOrNull {
                it.name.startsWith("getFontSize") && it.parameterCount == 0
            }
            val fontWeightMethod = style::class.java.methods.firstOrNull {
                it.name == "getFontWeight"
            }

            val fontSizeRaw = fontSizeMethod?.invoke(style) ?: return null
            val fontSizeValue: Float = if (fontSizeRaw is Long) {
                Float.fromBits(fontSizeRaw.toInt())
            } else {
                fontSizeRaw::class.java.methods
                    .firstOrNull { it.name == "getValue" }
                    ?.invoke(fontSizeRaw) as? Float ?: return null
            }

            val weight = try {
                val fw = fontWeightMethod?.invoke(style)
                fw?.let {
                    it::class.java.methods.firstOrNull { m -> m.name == "getWeight" }?.invoke(it) as? Int
                } ?: 400
            } catch (_: Exception) { 400 }

            val lineHeightMethod = style::class.java.methods.firstOrNull {
                it.name.startsWith("getLineHeight") && it.parameterCount == 0
            }
            val lineHeightValue: Float = try {
                val lhRaw = lineHeightMethod?.invoke(style)
                if (lhRaw is Long) Float.fromBits(lhRaw.toInt()) else 0f
            } catch (_: Exception) { 0f }

            val key = "$fontSizeValue|$weight|$lineHeightValue"
            val tokens = typoMap[key] ?: emptyList()

            TypographyResult(
                fontSizeSp = fontSizeValue,
                fontWeight = weight,
                lineHeightSp = lineHeightValue,
                matchingTokens = tokens,
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun extractTextColorFromModifiers(node: Any): Int? {
        try {
            val modInfoList = modifierInfoMethod?.invoke(node) as? List<*> ?: emptyList<Any>()
            if (modInfoList.isNotEmpty()) {
                for (modInfo in modInfoList) {
                    if (modInfo == null) continue
                    val modifier = getModifierGetter(modInfo::class.java)?.invoke(modInfo) ?: continue
                    for (field in modifier::class.java.declaredFields) {
                        field.isAccessible = true
                        val value = field.get(modifier) ?: continue
                        if (value::class.java.name.contains("TextStyle")) {
                            val color = extractColorFromTextStyle(value)
                            if (color != null) return color
                        }
                    }
                    for (field in modifier::class.java.declaredFields) {
                        field.isAccessible = true
                        val value = field.get(modifier) ?: continue
                        val valClass = value::class.java.name
                        if (valClass.contains("ColorProducer") || field.name == "color") {
                            try {
                                val invokeMethod = value::class.java.methods.firstOrNull {
                                    it.name == "invoke" && it.parameterCount == 0
                                }
                                if (invokeMethod != null) {
                                    val result = invokeMethod.invoke(value)
                                    if (result is Long) {
                                        val c = Color(result.toULong())
                                        if (c != Color.Unspecified) return c.toArgb()
                                    }
                                }
                            } catch (_: Exception) {}
                        }
                    }
                }
            }
        } catch (_: ReflectiveOperationException) {
            // Reflection may fail on different Compose versions
        }
        return extractTextColorFromNodeFields(node)
    }

    private fun extractTextColorFromNodeFields(node: Any): Int? {
        return try {
            for (field in node::class.java.declaredFields) {
                field.isAccessible = true
                val value = field.get(node) ?: continue
                val valClassName = value::class.java.name
                if (valClassName.contains("TextDelegate") || valClassName.contains("TextFieldState")) {
                    for (innerField in value::class.java.declaredFields) {
                        innerField.isAccessible = true
                        val innerVal = innerField.get(value) ?: continue
                        if (innerVal::class.java.name.contains("TextStyle")) {
                            val color = extractColorFromTextStyle(innerVal)
                            if (color != null) return color
                        }
                        if (innerVal::class.java.name.contains("TextDelegate")) {
                            for (f2 in innerVal::class.java.declaredFields) {
                                f2.isAccessible = true
                                val v2 = f2.get(innerVal) ?: continue
                                if (v2::class.java.name.contains("TextStyle")) {
                                    val color = extractColorFromTextStyle(v2)
                                    if (color != null) return color
                                }
                            }
                        }
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun extractColorFromTextStyle(style: Any): Int? {
        return try {
            val colorMethod = style::class.java.methods.firstOrNull {
                it.name.startsWith("getColor") && it.parameterTypes.isEmpty()
            }
            if (colorMethod != null) {
                val colorResult = colorMethod.invoke(style)
                if (colorResult is Long) {
                    val color = Color(colorResult.toULong())
                    if (color != Color.Unspecified) {
                        return color.toArgb()
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun extractBgColorFromNode(node: Any): Int? {
        return try {
            val modInfoList = modifierInfoMethod?.invoke(node) as? List<*> ?: return null
            for (modInfo in modInfoList) {
                if (modInfo == null) continue
                val modifier = getModifierGetter(modInfo::class.java)?.invoke(modInfo) ?: continue
                val modClassName = modifier::class.java.name
                if (modClassName.contains("Background")) {
                    for (field in modifier::class.java.declaredFields) {
                        field.isAccessible = true
                        val value = field.get(modifier) ?: continue
                        val valueClassName = value::class.java.name
                        if (valueClassName.contains("SolidColor")) {
                            val valueField = value::class.java.declaredFields.firstOrNull()
                            if (valueField != null) {
                                valueField.isAccessible = true
                                val colorVal = valueField.get(value)
                                if (colorVal is Long) {
                                    val color = Color(colorVal.toULong())
                                    return color.toArgb()
                                }
                            }
                        }
                        if (field.type == Long::class.javaPrimitiveType && field.name.contains("color", ignoreCase = true)) {
                            val colorLong = field.getLong(modifier)
                            val color = Color(colorLong.toULong())
                            if (color != Color.Unspecified) {
                                return color.toArgb()
                            }
                        }
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun extractCornerRadius(
        node: Any,
        density: Float,
        dimensionMap: Map<Float, List<String>>,
    ): CornerRadiusResult? {
        return try {
            val modInfoList = modifierInfoMethod?.invoke(node) as? List<*> ?: return null
            for (modInfo in modInfoList) {
                if (modInfo == null) continue
                val modifier = getModifierGetter(modInfo::class.java)?.invoke(modInfo) ?: continue
                val result = tryExtractCornerFromModifier(modifier, density, dimensionMap)
                if (result != null) return result
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun tryExtractCornerFromModifier(
        modifier: Any,
        density: Float,
        dimensionMap: Map<Float, List<String>>,
    ): CornerRadiusResult? {
        return try {
            val modClass = modifier::class.java
            for (field in modClass.declaredFields) {
                field.isAccessible = true
                val value = field.get(modifier) ?: continue
                val valueClass = value::class.java.name
                if (valueClass.contains("RoundedCornerShape")) {
                    val topStart = extractCornerSizeDp(value, "topStart", density)
                    if (topStart != null && topStart > 0f) {
                        val dpRounded = topStart.roundToInt().toFloat()
                        val tokens = TokenResolver.resolveDimension(dimensionMap, dpRounded, "cornerRadius")
                        return CornerRadiusResult(
                            radiusDp = dpRounded,
                            matchingTokens = tokens,
                        )
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun extractCornerSizeDp(shape: Any, cornerName: String, density: Float): Float? {
        return try {
            val cornerField = shape::class.java.declaredFields.firstOrNull {
                it.name.contains(cornerName, ignoreCase = true)
            } ?: shape::class.java.declaredFields.firstOrNull()
            if (cornerField != null) {
                cornerField.isAccessible = true
                val cornerSize = cornerField.get(shape) ?: return null
                val csClass = cornerSize::class.java

                for (f in csClass.declaredFields) {
                    if (f.type == Float::class.javaPrimitiveType) {
                        f.isAccessible = true
                        val v = f.getFloat(cornerSize)
                        if (v > 0f) return v
                    }
                }

                val toPxMethod = csClass.methods.firstOrNull { it.name == "toPx" } ?: return null
                val w = java.lang.Float.floatToRawIntBits(100f).toLong()
                val h = java.lang.Float.floatToRawIntBits(100f).toLong()
                val packedSize = (w shl 32) or (h and 0xFFFFFFFFL)
                val px = toPxMethod.invoke(cornerSize, packedSize, density) as? Float
                return px?.let { it / density }
            }
            null
        } catch (_: Exception) {
            null
        }
    }
}

internal data class AutoDetectedPadding(
    val level: String,
    val direction: String,
    val dpValue: Float,
    val matchingTokens: List<String>,
)

internal data class TypographyResult(
    val fontSizeSp: Float,
    val fontWeight: Int,
    val lineHeightSp: Float = 0f,
    val matchingTokens: List<String>,
)

internal data class SpacingResult(
    val direction: String,
    val dpValue: Float,
    val matchingTokens: List<String>,
)

internal data class CornerRadiusResult(
    val radiusDp: Float,
    val matchingTokens: List<String>,
)

internal data class InspectionResult(
    val paddings: List<AutoDetectedPadding>,
    val highlightBounds: Rect?,
    val sizeDp: Pair<Int, Int>?,
    val typography: TypographyResult?,
    val spacings: List<SpacingResult>,
    val cornerRadius: CornerRadiusResult?,
    val textColorArgb: Int?,
    val modifierBgArgb: Int?,
)
