package com.spoonlabs.composeinspector

import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Utility for building and resolving design token maps.
 * Uses reflection to automatically extract Color/Dp/TextStyle fields from arbitrary objects.
 */
internal object TokenResolver {

    private const val TAG = "TokenResolver"

    /**
     * Extract Color (Long-backed) fields from an object via reflection and build an ARGB → token name map.
     * Compose Color is an inline class backed by Long, so Long fields are auto-detected.
     */
    fun buildColorMapFromObject(target: Any): Map<Int, List<String>> {
        val map = mutableMapOf<Int, MutableList<String>>()
        try {
            target.javaClass.declaredFields.forEach { field ->
                if (field.type == Long::class.javaPrimitiveType) {
                    field.isAccessible = true
                    val rawLong = field.getLong(target)
                    val color = Color(rawLong.toULong())
                    // Color가 아닌 Long 필드 필터링: Unspecified 또는 alpha=0 (투명) 제외
                    if (color == Color.Unspecified) return@forEach
                    val alpha = (color.toArgb() shr 24) and 0xFF
                    if (alpha == 0) return@forEach
                    val argb = color.toArgb()
                    map.getOrPut(argb) { mutableListOf() }.add(field.name)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "buildColorMapFromObject failed for ${target.javaClass.simpleName}", e)
        }
        return map
    }

    // RGB upper 4-bit bucket → only iterate candidates during approximate matching
    @Volatile
    private var colorBuckets: Map<Int, List<Pair<Int, List<String>>>> = emptyMap()

    /**
     * Build bucket structure for fast approximate color matching.
     * Called automatically by [ComposeInspector.setColorTokens].
     */
    fun buildColorBuckets(colorMap: Map<Int, List<String>>) {
        colorBuckets = colorMap.entries.groupBy(
            keySelector = { bucketKey(it.key) },
            valueTransform = { it.key to it.value },
        )
    }

    private fun bucketKey(argb: Int): Int {
        val r = (argb shr 20) and 0xF
        val g = (argb shr 12) and 0xF
        val b = (argb shr 4) and 0xF
        return (r shl 8) or (g shl 4) or b
    }

    /**
     * Resolve tokens matching a pixel ARGB value.
     * O(1) exact match first, then bucket-based approximate matching (distance < 15).
     */
    fun resolveColor(colorMap: Map<Int, List<String>>, pixelArgb: Int): ColorResolveResult {
        val exact = colorMap[pixelArgb]
        if (exact != null) {
            return ColorResolveResult(tokens = exact, exact = true, distance = 0)
        }

        var minDistance = Float.MAX_VALUE
        var closest: List<String> = emptyList()
        val key = bucketKey(pixelArgb)

        val candidates = mutableListOf<Pair<Int, List<String>>>()
        for (dr in -1..1) for (dg in -1..1) for (db in -1..1) {
            val r = ((key shr 8) and 0xF) + dr
            val g = ((key shr 4) and 0xF) + dg
            val b = (key and 0xF) + db
            if (r in 0..15 && g in 0..15 && b in 0..15) {
                val neighborKey = (r shl 8) or (g shl 4) or b
                colorBuckets[neighborKey]?.let { candidates.addAll(it) }
            }
        }

        for ((argb, tokens) in candidates) {
            val d = colorDistance(pixelArgb, argb)
            if (d < minDistance) {
                minDistance = d
                closest = tokens
            }
        }

        if (candidates.isEmpty()) {
            for ((argb, tokens) in colorMap) {
                val d = colorDistance(pixelArgb, argb)
                if (d < minDistance) {
                    minDistance = d
                    closest = tokens
                }
            }
        }

        return if (minDistance < 15f) {
            ColorResolveResult(tokens = closest, exact = false, distance = minDistance.roundToInt())
        } else {
            ColorResolveResult(tokens = emptyList(), exact = false, distance = minDistance.roundToInt())
        }
    }

    /** Format an ARGB int as a hex string. */
    fun formatHex(argb: Int): String {
        val a = (argb shr 24) and 0xFF
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return if (a == 255) {
            String.format("#%02X%02X%02X", r, g, b)
        } else {
            String.format("#%02X%02X%02X%02X", a, r, g, b)
        }
    }

    /**
     * Resolve tokens matching a dp value from the dimension map.
     * Exact match first, then ±0.5dp tolerance.
     */
    fun resolveDimension(
        dimensionMap: Map<Float, List<String>>,
        dpValue: Float,
        prefix: String = "",
    ): List<String> {
        val exact = dimensionMap[dpValue]
        if (exact != null) {
            return if (prefix.isEmpty()) exact else exact.filter { it.startsWith(prefix) }
        }
        val nearest = dimensionMap.entries
            .filter { abs(it.key - dpValue) < 0.5f }
            .minByOrNull { abs(it.key - dpValue) }
        val tokens = nearest?.value ?: return emptyList()
        return if (prefix.isEmpty()) tokens else tokens.filter { it.startsWith(prefix) }
    }

    /** Extract Float (Dp-backed) fields from an object via reflection and build a dp → token name map. */
    fun buildDimensionMapFromObject(target: Any): Map<Float, List<String>> {
        val map = mutableMapOf<Float, MutableList<String>>()
        try {
            target.javaClass.declaredFields.forEach { field ->
                if (field.type == Float::class.javaPrimitiveType) {
                    field.isAccessible = true
                    val raw = field.getFloat(target)
                    // Dp가 아닌 Float 필드 필터링: 0 이하 또는 비현실적 값 (>1000dp) 제외
                    if (raw <= 0f || raw > 1000f) return@forEach
                    val dpValue = (raw * 10).roundToInt() / 10f
                    map.getOrPut(dpValue) { mutableListOf() }.add(field.name)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "buildDimensionMapFromObject failed for ${target.javaClass.simpleName}", e)
        }
        return map
    }

    /** Extract TextStyle fields from an object via reflection and build a (fontSize|fontWeight|lineHeight) → token name map. */
    fun buildTypoMapFromObject(target: Any): Map<String, List<String>> {
        val map = mutableMapOf<String, MutableList<String>>()
        try {
            target.javaClass.declaredFields.forEach { field ->
                if (field.type == TextStyle::class.java) {
                    field.isAccessible = true
                    val style = field.get(target) as? TextStyle ?: return@forEach
                    val size = style.fontSize
                    val weight = style.fontWeight?.weight ?: 400
                    val lh = style.lineHeight
                    val lhValue = if (lh.isSp) lh.value else 0f
                    if (size.isSp) {
                        val key = "${size.value}|$weight|$lhValue"
                        map.getOrPut(key) { mutableListOf() }.add(field.name)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "buildTypoMapFromObject failed for ${target.javaClass.simpleName}", e)
        }
        return map
    }

    private fun colorDistance(c1: Int, c2: Int): Float {
        val r = ((c1 shr 16) and 0xFF) - ((c2 shr 16) and 0xFF)
        val g = ((c1 shr 8) and 0xFF) - ((c2 shr 8) and 0xFF)
        val b = (c1 and 0xFF) - (c2 and 0xFF)
        return sqrt(0.3f * r * r + 0.59f * g * g + 0.11f * b * b)
    }
}

internal data class ColorResolveResult(
    val tokens: List<String>,
    val exact: Boolean,
    val distance: Int,
)
