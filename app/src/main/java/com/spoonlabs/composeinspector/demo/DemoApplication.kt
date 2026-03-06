package com.spoonlabs.composeinspector.demo

import android.app.Application
import com.spoonlabs.composeinspector.ComposeInspector

class DemoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ComposeInspector.init(enabled = BuildConfig.DEBUG)
    }
}
