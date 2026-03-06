package com.spoonlabs.composeinspector

import android.os.Bundle

/**
 * Internal Activity used by [ComposeInspector.launch].
 * Not intended for direct use — it is automatically launched via the launch() API.
 */
internal class InspectorLauncherActivity : InspectorActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content = ComposeInspector.consumePendingContent() ?: run {
            finish()
            return
        }
        setInspectorContent { content() }
    }
}
