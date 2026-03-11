package com.spoonlabs.composeinspector

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * ComposeInspector — Public API for the Compose UI Inspector that
 * analyzes UI elements at runtime.
 *
 * Usage:
 * 1. `ComposeInspector.init(enabled = BuildConfig.DEBUG)` — call at app startup
 * 2. Register token maps inside your Theme composable + call `attachToWindow(activity)`
 *
 * Quick launch for individual component inspection:
 * ```kotlin
 * ComposeInspector.launch(context) {
 *     MyComponent()
 * }
 * ```
 */
object ComposeInspector {
    internal var enabledState = mutableStateOf(false)

    val isEnabled: Boolean
        get() = enabledState.value

    /**
     * Initialize the Inspector. Call this at app startup (e.g., in `Application.onCreate()`).
     *
     * @param enabled Whether the inspector is active. Typically `BuildConfig.DEBUG`.
     */
    fun init(enabled: Boolean) {
        enabledState.value = enabled
        if (!enabled) {
            detachAll()
        }
    }

    // Token registries
    @Volatile internal var colorMap: Map<Int, List<String>> = emptyMap()
    @Volatile internal var dimensionMap: Map<Float, List<String>> = emptyMap()
    @Volatile internal var typoMap: Map<String, List<String>> = emptyMap()

    /**
     * Register a color token map. ARGB int → token name list.
     * Automatically rebuilds internal color buckets for approximate matching.
     */
    fun setColorTokens(colorMap: Map<Int, List<String>>) {
        // buckets를 먼저 빌드 — resolveColor에서 exact match가 먼저 체크되므로
        // buckets가 잠시 새 맵 기준이어도 colorMap이 아직 이전이면 exact에서 처리됨
        TokenResolver.buildColorBuckets(colorMap)
        this.colorMap = colorMap
    }

    /**
     * Register a dimension token map. dp Float → token name list.
     */
    fun setDimensionTokens(dimensionMap: Map<Float, List<String>>) {
        this.dimensionMap = dimensionMap
    }

    /**
     * Register a typography token map. "fontSize|fontWeight|lineHeight" → token name list.
     */
    fun setTypographyTokens(typoMap: Map<String, List<String>>) {
        this.typoMap = typoMap
    }

    /**
     * Build a color token map from an arbitrary object using reflection.
     * Extracts all Color (Long-backed) fields and maps ARGB values to field names.
     *
     * Note: This method uses reflection to extract field names from the target object.
     * In minified (R8/ProGuard) builds, annotate the target class with [@InspectableTokens]
     * to prevent field name obfuscation.
     * Alternatively, add an R8/ProGuard keep rule such as
     * `-keep class your.TokenClass { *; }` in your app or library configuration.
     */
    fun buildColorMap(target: Any): Map<Int, List<String>> =
        TokenResolver.buildColorMapFromObject(target)

    /**
     * Build a dimension token map from an arbitrary object using reflection.
     * Extracts all Float (Dp-backed) fields and maps dp values to field names.
     *
     * @see buildColorMap for R8/ProGuard considerations.
     */
    fun buildDimensionMap(target: Any): Map<Float, List<String>> =
        TokenResolver.buildDimensionMapFromObject(target)

    /**
     * Build a typography token map from an arbitrary object using reflection.
     * Extracts all TextStyle fields and maps (fontSize|fontWeight|lineHeight) keys to field names.
     *
     * @see buildColorMap for R8/ProGuard considerations.
     */
    fun buildTypoMap(target: Any): Map<String, List<String>> =
        TokenResolver.buildTypoMapFromObject(target)

    /**
     * Register all design tokens and attach the inspector overlay in one call.
     * For a single color theme object.
     *
     * Uses reflection internally via [buildColorMap], [buildDimensionMap], [buildTypoMap].
     * @see buildColorMap for R8/ProGuard considerations.
     */
    fun setDesignTokens(
        context: Context,
        colors: Any,
        dimen: Any,
        typo: Any,
    ) {
        setDesignTokens(context, listOf(colors), dimen, typo)
    }

    /**
     * Register all design tokens and attach the inspector overlay in one call.
     * Accepts multiple color objects (e.g. light + dark) which are automatically merged.
     *
     * Uses reflection internally via [buildColorMap], [buildDimensionMap], [buildTypoMap].
     * @see buildColorMap for R8/ProGuard considerations.
     */
    fun setDesignTokens(
        context: Context,
        colors: List<Any>,
        dimen: Any,
        typo: Any,
    ) {
        if (!isEnabled) return
        val mergedColorMap = mergeColorMaps(colors.map { buildColorMap(it) })
        setColorTokens(mergedColorMap)
        setDimensionTokens(buildDimensionMap(dimen))
        setTypographyTokens(buildTypoMap(typo))
        context.findActivity()?.let { attachToWindow(it) }
    }

    private fun mergeColorMaps(maps: List<Map<Int, List<String>>>): Map<Int, List<String>> {
        if (maps.size == 1) return maps[0]
        val allKeys = maps.flatMapTo(mutableSetOf()) { it.keys }
        return allKeys.associateWith { key ->
            maps.flatMap { it[key].orEmpty() }.distinct()
        }
    }

    private fun Context.findActivity(): Activity? {
        var ctx = this
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    // Color priority prefixes for tooltip display
    @Volatile
    internal var colorPriorityPrefixes: List<String> = emptyList()

    /**
     * Set the priority prefixes for color token display in the tooltip.
     * Tokens whose names start with these prefixes are shown first.
     */
    fun setColorPriorityPrefixes(prefixes: List<String>) {
        colorPriorityPrefixes = prefixes
    }

    // --- One-line launcher ---

    private val pendingContentRef = AtomicReference<(@Composable () -> Unit)?>(null)

    /**
     * Launch an individual component in a standalone Inspector screen.
     * No Activity subclassing or Manifest registration required.
     *
     * ```kotlin
     * ComposeInspector.launch(context) {
     *     MyComponent()
     * }
     * ```
     */
    fun launch(context: Context, content: @Composable () -> Unit) {
        if (!isEnabled) return
        pendingContentRef.set(content)
        context.startActivity(
            Intent(context, InspectorLauncherActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    internal fun consumePendingContent(): (@Composable () -> Unit)? {
        return pendingContentRef.getAndSet(null)
    }

    // --- Window-level overlay management ---

    private val mainHandler = Handler(Looper.getMainLooper())

    private val attachedActivities: MutableMap<Activity, ComposeView> =
        java.util.Collections.synchronizedMap(WeakHashMap())

    /**
     * Attach the Inspector overlay to an Activity window.
     * Only one overlay is attached per Activity; duplicate calls are ignored.
     */
    fun attachToWindow(activity: Activity) {
        if (!isEnabled) return
        if (attachedActivities.containsKey(activity)) return

        val contentView = activity.findViewById<FrameLayout>(android.R.id.content)
        if (contentView == null) {
            Log.w("ComposeInspector", "Content view not found for ${activity::class.java.simpleName}")
            return
        }

        val overlayView = ComposeView(activity).apply {
            setViewCompositionStrategy(
                androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
            )
        }

        contentView.addView(
            overlayView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        overlayView.elevation = 100f

        overlayView.setContent {
            ComposeInspectorWindow(activity = activity)
        }

        attachedActivities[activity] = overlayView

        if (activity is LifecycleOwner) {
            activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) {
                    detachFromWindow(activity)
                    owner.lifecycle.removeObserver(this)
                }
            })
        }
    }

    private fun detachAll() {
        val runDetach = Runnable {
            val activities = synchronized(attachedActivities) { attachedActivities.keys.toList() }
            for (activity in activities) {
                detachFromWindow(activity)
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runDetach.run()
        } else {
            mainHandler.post(runDetach)
        }
    }

    internal fun detachFromWindow(activity: Activity) {
        val overlayView = attachedActivities.remove(activity) ?: return
        val contentView =
            activity.findViewById<FrameLayout>(android.R.id.content) ?: return
        contentView.removeView(overlayView)
    }

    internal fun getOverlayView(activity: Activity): ComposeView? {
        return attachedActivities[activity]
    }
}
