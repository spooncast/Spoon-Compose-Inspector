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

    private const val MAX_TREE_DEPTH = 100

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
        Log.d(TAG, "findComposeViews: root=${root::class.java.simpleName}, excludeView=${excludeView?.let { it::class.java.simpleName + "@" + Integer.toHexString(it.hashCode()) }}")
        collectComposeViews(root, result, excludeView)
        Log.d(TAG, "findComposeViews: found ${result.size} ComposeViews")
        result.forEachIndexed { index, view ->
            val loc = IntArray(2)
            view.getLocationOnScreen(loc)
            Log.d(TAG, "  [$index] ${view::class.java.name}@${Integer.toHexString(view.hashCode())} loc=(${loc[0]},${loc[1]}) size=${view.width}x${view.height} parent=${view.parent?.let { it::class.java.simpleName }}")
        }
        return result
    }

    private fun collectComposeViews(view: View, result: MutableList<View>, excludeView: View?) {
        if (excludeView != null) {
            // excludeView 자체를 제외
            if (view === excludeView) return
            // excludeView의 부모(ComposeView 래퍼)도 제외 — 하위 트리 전체 스킵
            val excludeParent = excludeView.parent
            if (excludeParent is View && view === excludeParent) return
        }
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
        colorMap: Map<Int, List<String>> = emptyMap(),
    ): InspectionResult {
        val rootNode = getRootLayoutNode(composeView) ?: return InspectionResult()

        val allNodes = mutableListOf<Pair<Any, Rect>>()
        collectNodesAt(rootNode, position, allNodes)
        allNodes.sortBy { it.second.width * it.second.height }

        val highlightBounds = allNodes.firstOrNull()?.second
        val sizeDp = highlightBounds?.let {
            val wDp = (it.width / density).roundToInt()
            val hDp = (it.height / density).roundToInt()
            Pair(wDp, hDp)
        }

        // 노드별 modifier 리스트를 한 번만 조회하여 캐시
        val nodeModifiers = allNodes.map { (node, _) -> node to getModifierList(node) }

        val paddings = mutableListOf<AutoDetectedPadding>()
        for ((index, entry) in nodeModifiers.withIndex()) {
            val (_, modifiers) = entry
            val level = when (index) {
                0 -> "SELF"
                1 -> "PARENT"
                else -> "ANCESTOR"
            }
            extractPaddingFromModifiers(modifiers, density, dimensionMap, paddings, level)
        }
        if (paddings.isEmpty() && allNodes.size >= 2) {
            val (_, childBounds) = allNodes[0]
            val (_, parentBounds) = allNodes[1]
            extractPadding(parentBounds, childBounds, density, dimensionMap, paddings, "PARENT")
        }

        var typography: TypographyResult? = null
        for ((node, modifiers) in nodeModifiers) {
            typography = extractTypoFromModifiers(node, modifiers, typoMap)
            if (typography != null) break
        }

        var textColorArgb: Int? = null
        for ((node, modifiers) in nodeModifiers) {
            textColorArgb = extractTextColorFromModifiers(node, modifiers)
            if (textColorArgb != null) break
        }

        var modifierBgArgb: Int? = null
        for ((_, modifiers) in nodeModifiers) {
            modifierBgArgb = extractBgColorFromModifiers(modifiers)
            if (modifierBgArgb != null) break
        }

        val innerModifiers = nodeModifiers.firstOrNull()?.second ?: emptyList()
        val innerNode = allNodes.firstOrNull()?.first

        val cornerRadius = extractCornerRadius(innerModifiers, density, dimensionMap)
        val semantics = extractSemanticsInfo(innerModifiers)
        val alpha = extractAlpha(innerModifiers)
        val border = extractBorder(innerModifiers, colorMap)
        val shadow = extractShadow(innerModifiers)
        val tint = extractTint(innerModifiers, colorMap)
        val (componentType, layoutInfo) = innerNode?.let { inferComponentAndLayout(it, innerModifiers, semantics) }
            ?: ComponentResult(null, null)

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
            semantics = semantics,
            alpha = alpha,
            border = border,
            shadow = shadow,
            tint = tint,
            componentType = componentType,
            layoutInfo = layoutInfo,
        )
    }

    /** modifier 리스트를 노드에서 한 번만 추출 */
    private fun getModifierList(node: Any): List<ModifierEntry> {
        val modInfoList = try {
            modifierInfoMethod?.invoke(node) as? List<*>
        } catch (_: Exception) {
            null
        } ?: return emptyList()

        return modInfoList.mapNotNull { modInfo ->
            if (modInfo == null) return@mapNotNull null
            val modifier = getModifierGetter(modInfo::class.java)?.invoke(modInfo) ?: return@mapNotNull null
            ModifierEntry(modifier, modifier::class.java.name, modifier::class.java.declaredFields)
        }
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
        val unsortedBounds = siblings.mapNotNull { child ->
            getNodeBounds(child)?.let { b -> if (b.width > 0 && b.height > 0) b else null }
        }
        if (unsortedBounds.size < 2) return emptyList()

        // 자식 배치 패턴으로 방향 결정: 수평 spread > 수직 spread → Row
        val hSpread = unsortedBounds.maxOf { it.left } - unsortedBounds.minOf { it.left }
        val vSpread = unsortedBounds.maxOf { it.top } - unsortedBounds.minOf { it.top }
        val isHorizontal = hSpread > vSpread
        val siblingBounds = unsortedBounds.sortedBy { if (isHorizontal) it.left else it.top }

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
        modifiers: List<ModifierEntry>,
        density: Float,
        dimensionMap: Map<Float, List<String>>,
        result: MutableList<AutoDetectedPadding>,
        level: String,
    ) {
        try {
            for (entry in modifiers) {
                val hasPaddingFields = lazy {
                    entry.fields.any { f ->
                        val n = f.name; n.contains("start") || n.contains("top") || n.contains("end") || n.contains("bottom")
                    } && entry.fields.any { f -> f.type == Float::class.javaPrimitiveType }
                }
                if (!entry.className.contains("Padding", ignoreCase = true) && !hasPaddingFields.value) continue

                var startDp = 0f
                var topDp = 0f
                var endDp = 0f
                var bottomDp = 0f
                var found = false

                for (field in entry.fields) {
                    field.isAccessible = true
                    val name = field.name.lowercase()
                    if (field.type == Float::class.javaPrimitiveType) {
                        val value = field.getFloat(entry.modifier)
                        when {
                            name.contains("start") || name == "left" -> { startDp = value; found = true }
                            name.contains("top") -> { topDp = value; found = true }
                            name.contains("end") || name == "right" -> { endDp = value; found = true }
                            name.contains("bottom") -> { bottomDp = value; found = true }
                        }
                    }
                }

                if (!found) {
                    for (field in entry.fields) {
                        field.isAccessible = true
                        if (field.type == Float::class.javaPrimitiveType) {
                            val value = field.getFloat(entry.modifier)
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

    private fun extractTypoFromModifiers(node: Any, modifiers: List<ModifierEntry>, typoMap: Map<String, List<String>>): TypographyResult? {
        try {
            for (entry in modifiers) {
                for (field in entry.fields) {
                    field.isAccessible = true
                    val value = field.get(entry.modifier) ?: continue
                    if (value::class.java.name.contains("TextStyle")) {
                        val result = extractFromTextStyle(value, typoMap)
                        if (result != null) return result
                    }
                }
            }
        } catch (_: ReflectiveOperationException) { }
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

    private fun extractTextColorFromModifiers(node: Any, modifiers: List<ModifierEntry>): Int? {
        try {
            for (entry in modifiers) {
                for (field in entry.fields) {
                    field.isAccessible = true
                    val value = field.get(entry.modifier) ?: continue
                    if (value::class.java.name.contains("TextStyle")) {
                        val color = extractColorFromTextStyle(value)
                        if (color != null) return color
                    }
                }
                for (field in entry.fields) {
                    field.isAccessible = true
                    val value = field.get(entry.modifier) ?: continue
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
        } catch (_: ReflectiveOperationException) { }
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

    private fun extractBgColorFromModifiers(modifiers: List<ModifierEntry>): Int? {
        return try {
            for (entry in modifiers) {
                if (!entry.className.contains("Background")) continue
                for (field in entry.fields) {
                    field.isAccessible = true
                    if (field.type == Long::class.javaPrimitiveType && field.name.contains("color", ignoreCase = true)) {
                        val colorLong = field.getLong(entry.modifier)
                        val color = Color(colorLong.toULong())
                        if (color != Color.Unspecified) return color.toArgb()
                        continue
                    }
                    val value = field.get(entry.modifier) ?: continue
                    if (value::class.java.name.contains("SolidColor")) {
                        val valueField = value::class.java.declaredFields.firstOrNull()
                        if (valueField != null) {
                            valueField.isAccessible = true
                            val colorVal = valueField.get(value)
                            if (colorVal is Long) return Color(colorVal.toULong()).toArgb()
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
        modifiers: List<ModifierEntry>,
        density: Float,
        dimensionMap: Map<Float, List<String>>,
    ): CornerRadiusResult? {
        return try {
            for (entry in modifiers) {
                val result = tryExtractCornerFromModifier(entry.modifier, density, dimensionMap)
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
    // --- New extractors ---

    private val knownComponentTypes = setOf(
        "Column", "Row", "Box", "LazyColumn", "LazyRow", "LazyGrid",
        "FlowRow", "FlowColumn", "Surface", "Scaffold", "Card",
        "BottomSheet", "Dialog", "Drawer", "Navigation",
    )

    private data class ComponentResult(
        val type: String?,
        val layoutInfo: LayoutInfo?,
    )

    private fun inferComponentAndLayout(node: Any, modifiers: List<ModifierEntry>, semantics: SemanticsInfo?): ComponentResult {
        return try {
            // 1) measurePolicy 기반: Column, Row, Box 등 레이아웃 컴포넌트
            for (field in node::class.java.declaredFields) {
                if (field.name.contains("measurePolicy", ignoreCase = true)) {
                    field.isAccessible = true
                    val policy = field.get(node) ?: continue
                    val className = policy::class.java.simpleName
                    val name = className
                        .removeSuffix("MeasurePolicy")
                        .removeSuffix("\$1")
                        .removeSuffix("Impl")
                        .takeIf { it.isNotEmpty() && it != className }
                        ?: continue
                    if (knownComponentTypes.any { name.contains(it, ignoreCase = true) }) {
                        return ComponentResult(name, extractLayoutInfo(policy, name))
                    }
                }
            }
            // 2) modifier 클래스명 기반: Text, Image, TextField 등 leaf 컴포넌트
            ComponentResult(inferComponentTypeFromModifiers(modifiers, semantics), null)
        } catch (_: Exception) {
            ComponentResult(null, null)
        }
    }

    // 기본값 — 표시할 필요 없는 값들 (lowercase로 비교)
    private val defaultArrangements = setOf("top", "start")
    private val defaultAlignments = setOf("start", "top", "topstart")

    private fun extractLayoutInfo(measurePolicy: Any, componentType: String): LayoutInfo? {
        return try {
            var arrangement: String? = null
            var alignment: String? = null

            for (field in measurePolicy::class.java.declaredFields) {
                field.isAccessible = true
                val value = field.get(measurePolicy) ?: continue
                val fieldName = field.name.lowercase()

                when {
                    fieldName.contains("arrangement") -> {
                        arrangement = resolveArrangementName(value)
                    }
                    fieldName.contains("alignment") -> {
                        alignment = resolveAlignmentName(value)
                    }
                }
            }

            // 기본값 필터링 (case-insensitive)
            if (arrangement?.lowercase() in defaultArrangements) arrangement = null
            if (alignment?.lowercase() in defaultAlignments) alignment = null

            if (arrangement != null || alignment != null) LayoutInfo(arrangement, alignment) else null
        } catch (_: Exception) {
            null
        }
    }

    private val knownArrangements = setOf(
        "Top", "Bottom", "Start", "End", "Center",
        "SpaceBetween", "SpaceAround", "SpaceEvenly",
    )

    private fun resolveArrangementName(value: Any): String? {
        val cls = value::class.java

        // spacedBy(n.dp) — spacing 필드에서 값 추출
        for (field in cls.declaredFields) {
            if (field.name.contains("spacing", ignoreCase = true)) {
                field.isAccessible = true
                val spacing = field.get(value)
                if (spacing is Float && spacing > 0f) {
                    return "spacedBy(${spacing.roundToInt()}dp)"
                }
            }
        }

        // 알려진 이름만 허용 — 클래스명, toString 순으로 매칭
        val candidates = listOf(
            cls.simpleName,
            value.toString(),
        )
        for (candidate in candidates) {
            for (known in knownArrangements) {
                if (candidate.contains(known, ignoreCase = true)) return known
            }
        }
        return null
    }

    private fun resolveAlignmentName(value: Any): String? {
        return try {
            val cls = value::class.java
            val fields = cls.declaredFields

            // BiasAlignment.Horizontal / BiasAlignment.Vertical (1D — Column, Row)
            val biasField = fields.firstOrNull { it.name == "bias" }
            if (biasField != null) {
                biasField.isAccessible = true
                val bias = biasField.getFloat(value)
                val isHorizontal = cls.name.contains("Horizontal", ignoreCase = true)
                return if (isHorizontal) mapHorizontalBias(bias) else mapVerticalBias(bias)
            }

            // BiasAlignment (2D — Box의 contentAlignment)
            val hBiasField = fields.firstOrNull { it.name == "horizontalBias" }
            val vBiasField = fields.firstOrNull { it.name == "verticalBias" }
            if (hBiasField != null && vBiasField != null) {
                hBiasField.isAccessible = true
                vBiasField.isAccessible = true
                return map2dBias(vBiasField.getFloat(value), hBiasField.getFloat(value))
            }

            null // 알 수 없는 형태는 표시하지 않음
        } catch (_: Exception) {
            null
        }
    }

    private fun mapHorizontalBias(bias: Float): String? = when {
        bias <= -0.9f -> "Start"
        bias in -0.1f..0.1f -> "CenterHorizontally"
        bias >= 0.9f -> "End"
        else -> null
    }

    private fun mapVerticalBias(bias: Float): String? = when {
        bias <= -0.9f -> "Top"
        bias in -0.1f..0.1f -> "CenterVertically"
        bias >= 0.9f -> "Bottom"
        else -> null
    }

    private fun map2dBias(vBias: Float, hBias: Float): String? {
        val v = when {
            vBias <= -0.9f -> "Top"
            vBias in -0.1f..0.1f -> "Center"
            vBias >= 0.9f -> "Bottom"
            else -> return null
        }
        val h = when {
            hBias <= -0.9f -> "Start"
            hBias in -0.1f..0.1f -> "Center"
            hBias >= 0.9f -> "End"
            else -> return null
        }
        if (v == "Center" && h == "Center") return "Center"
        if (v == "Center") return "Center$h"
        if (h == "Center") return "${v}Center"
        return "$v$h"
    }

    private fun inferComponentTypeFromModifiers(modifiers: List<ModifierEntry>, semantics: SemanticsInfo?): String? {
        var hasTextModifier = false
        var hasTextFieldModifier = false
        var hasPainterModifier = false

        for (entry in modifiers) {
            when {
                entry.className.contains("TextStringSimpleElement") ||
                entry.className.contains("TextAnnotatedStringElement") -> hasTextModifier = true

                entry.className.contains("TextFieldCore") ||
                entry.className.contains("TextInputElement") -> hasTextFieldModifier = true

                entry.className.contains("PainterElement") ||
                entry.className.contains("PainterModifier") -> hasPainterModifier = true
            }
        }

        return when {
            hasTextFieldModifier -> "TextField"
            hasTextModifier -> "Text"
            hasPainterModifier && semantics?.imageRole == true -> "Image"
            hasPainterModifier -> "Icon"
            else -> null
        }
    }

    private fun extractSemanticsInfo(modifiers: List<ModifierEntry>): SemanticsInfo? {
        return try {
            var testTag: String? = null
            var contentDesc: String? = null
            var imageRole = false

            for (entry in modifiers) {
                if (!entry.className.contains("Semantics", ignoreCase = true)) continue

                for (field in entry.fields) {
                    field.isAccessible = true
                    val value = field.get(entry.modifier) ?: continue
                    val valClassName = value::class.java.name

                    if (valClassName.contains("SemanticsConfiguration")) {
                        try {
                            val iter = value::class.java.methods
                                .firstOrNull { it.name == "iterator" }
                                ?.invoke(value) as? Iterator<*>
                            if (iter != null) {
                                while (iter.hasNext()) {
                                    val semEntry = iter.next() ?: continue
                                    val keyMethod = semEntry::class.java.methods.firstOrNull { it.name == "getKey" }
                                    val valMethod = semEntry::class.java.methods.firstOrNull { it.name == "getValue" }
                                    val keyName = keyMethod?.invoke(semEntry)?.toString() ?: ""
                                    val entryVal = valMethod?.invoke(semEntry)
                                    when {
                                        keyName.contains("TestTag") && entryVal is String -> testTag = entryVal
                                        keyName.contains("ContentDescription") -> {
                                            contentDesc = when (entryVal) {
                                                is List<*> -> entryVal.firstOrNull()?.toString()
                                                is String -> entryVal
                                                else -> entryVal?.toString()
                                            }
                                        }
                                        keyName.contains("Role") -> {
                                            if (entryVal?.toString()?.contains("Image", ignoreCase = true) == true) {
                                                imageRole = true
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (_: Exception) {}
                    }

                    // Direct field approach as fallback
                    if (field.name.contains("testTag", ignoreCase = true) && value is String) {
                        testTag = value
                    }
                }
            }

            if (testTag != null || contentDesc != null || imageRole) {
                SemanticsInfo(testTag = testTag, contentDescription = contentDesc, imageRole = imageRole)
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun extractAlpha(modifiers: List<ModifierEntry>): Float? {
        return try {
            for (entry in modifiers) {
                if (!entry.className.contains("GraphicsLayer", ignoreCase = true) &&
                    !entry.className.contains("Alpha", ignoreCase = true)
                ) continue

                for (field in entry.fields) {
                    field.isAccessible = true
                    if (field.type == Float::class.javaPrimitiveType &&
                        field.name.contains("alpha", ignoreCase = true)
                    ) {
                        val alpha = field.getFloat(entry.modifier)
                        if (alpha < 1.0f && alpha >= 0f) return alpha
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun extractBorder(modifiers: List<ModifierEntry>, colorMap: Map<Int, List<String>>): BorderResult? {
        return try {
            for (entry in modifiers) {
                if (!entry.className.contains("Border", ignoreCase = true)) continue

                var widthDp = 0f
                var colorArgb: Int? = null

                for (field in entry.fields) {
                    field.isAccessible = true
                    val name = field.name.lowercase()

                    if (field.type == Float::class.javaPrimitiveType && name.contains("width")) {
                        widthDp = field.getFloat(entry.modifier)
                    } else if (field.type == Long::class.javaPrimitiveType && name.contains("color")) {
                        val colorLong = field.getLong(entry.modifier)
                        val c = Color(colorLong.toULong())
                        if (c != Color.Unspecified) colorArgb = c.toArgb()
                    } else {
                        val value = field.get(entry.modifier) ?: continue
                        if (value::class.java.name.contains("SolidColor")) {
                            val colorField = value::class.java.declaredFields.firstOrNull()
                            if (colorField != null) {
                                colorField.isAccessible = true
                                val colorVal = colorField.get(value)
                                if (colorVal is Long) colorArgb = Color(colorVal.toULong()).toArgb()
                            }
                        }
                    }
                }

                if (widthDp > 0f) {
                    val roundedWidth = widthDp.roundToInt().toFloat()
                    val tokens = colorArgb?.let {
                        TokenResolver.resolveColor(colorMap, it).tokens
                    } ?: emptyList()
                    return BorderResult(
                        widthDp = roundedWidth,
                        colorArgb = colorArgb,
                        colorTokens = tokens,
                    )
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun extractShadow(modifiers: List<ModifierEntry>): ShadowResult? {
        return try {
            for (entry in modifiers) {
                if (!entry.className.contains("Shadow", ignoreCase = true) &&
                    !entry.className.contains("GraphicsLayer", ignoreCase = true)
                ) continue

                for (field in entry.fields) {
                    field.isAccessible = true
                    val name = field.name.lowercase()
                    if (field.type == Float::class.javaPrimitiveType &&
                        (name.contains("elevation") || name.contains("shadow"))
                    ) {
                        val elevation = field.getFloat(entry.modifier)
                        if (elevation > 0f) {
                            return ShadowResult(elevationDp = elevation.roundToInt().toFloat())
                        }
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun extractTint(modifiers: List<ModifierEntry>, colorMap: Map<Int, List<String>>): TintResult? {
        return try {
            for (entry in modifiers) {
                // PainterElement/PainterModifier 에서 ColorFilter 추출
                if (!entry.className.contains("Painter", ignoreCase = true) &&
                    !entry.className.contains("ColorFilter", ignoreCase = true) &&
                    !entry.className.contains("Tint", ignoreCase = true)
                ) continue

                for (field in entry.fields) {
                    field.isAccessible = true
                    val value = field.get(entry.modifier) ?: continue
                    val valClassName = value::class.java.name

                    if (valClassName.contains("ColorFilter") || field.name.contains("colorFilter", ignoreCase = true)) {
                        val tintColor = extractColorFromColorFilter(value)
                        if (tintColor != null) {
                            val tokens = TokenResolver.resolveColor(colorMap, tintColor).tokens
                            return TintResult(colorArgb = tintColor, colorTokens = tokens)
                        }
                    }

                    // tint 필드가 Color 타입인 경우 (Long-backed)
                    if (field.name.contains("tint", ignoreCase = true) && field.type == Long::class.javaPrimitiveType) {
                        val colorLong = field.getLong(entry.modifier)
                        val c = Color(colorLong.toULong())
                        if (c != Color.Unspecified) {
                            val argb = c.toArgb()
                            val tokens = TokenResolver.resolveColor(colorMap, argb).tokens
                            return TintResult(colorArgb = argb, colorTokens = tokens)
                        }
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun extractColorFromColorFilter(colorFilter: Any, depth: Int = 0): Int? {
        if (depth > 3) return null
        return try {
            for (field in colorFilter::class.java.declaredFields) {
                field.isAccessible = true
                if (field.type == Long::class.javaPrimitiveType && field.name.contains("color", ignoreCase = true)) {
                    val colorLong = field.getLong(colorFilter)
                    val c = Color(colorLong.toULong())
                    if (c != Color.Unspecified) return c.toArgb()
                }
                val value = field.get(colorFilter) ?: continue
                if (value !== colorFilter && value::class.java.name.contains("ColorFilter")) {
                    val nested = extractColorFromColorFilter(value, depth + 1)
                    if (nested != null) return nested
                }
            }
            null
        } catch (_: Throwable) {
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

internal data class SemanticsInfo(
    val testTag: String?,
    val contentDescription: String?,
    val imageRole: Boolean = false,
)

internal data class BorderResult(
    val widthDp: Float,
    val colorArgb: Int?,
    val colorTokens: List<String>,
)

internal data class ShadowResult(
    val elevationDp: Float,
)

internal data class TintResult(
    val colorArgb: Int,
    val colorTokens: List<String>,
)

internal data class LayoutInfo(
    val arrangement: String?,
    val alignment: String?,
)

internal class ModifierEntry(
    val modifier: Any,
    val className: String,
    val fields: Array<java.lang.reflect.Field>,
)

internal data class InspectionResult(
    val paddings: List<AutoDetectedPadding> = emptyList(),
    val highlightBounds: Rect? = null,
    val sizeDp: Pair<Int, Int>? = null,
    val typography: TypographyResult? = null,
    val spacings: List<SpacingResult> = emptyList(),
    val cornerRadius: CornerRadiusResult? = null,
    val textColorArgb: Int? = null,
    val modifierBgArgb: Int? = null,
    val semantics: SemanticsInfo? = null,
    val alpha: Float? = null,
    val border: BorderResult? = null,
    val shadow: ShadowResult? = null,
    val tint: TintResult? = null,
    val componentType: String? = null,
    val layoutInfo: LayoutInfo? = null,
)
