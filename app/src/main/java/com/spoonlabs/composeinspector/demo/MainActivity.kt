package com.spoonlabs.composeinspector.demo

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.spoonlabs.composeinspector.ComposeInspector
import com.spoonlabs.composeinspector.demo.theme.clearTokens
import com.spoonlabs.composeinspector.demo.theme.registerTokens

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DemoApp(activity = this)
        }
    }
}

@Composable
private fun DemoApp(activity: Activity) {
    var selectedTab by remember { mutableIntStateOf(0) }

    val tabs = listOf(
        stringResource(R.string.tab_with_tokens),
        stringResource(R.string.tab_without_tokens),
        stringResource(R.string.tab_component_test),
    )

    LaunchedEffect(selectedTab) {
        when (selectedTab) {
            0 -> registerTokens()
            else -> clearTokens()
        }
    }

    LaunchedEffect(Unit) {
        ComposeInspector.attachToWindow(activity)
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding(),
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, title ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = {},
                        label = { Text(title) },
                    )
                }
            }
        },
    ) { innerPadding ->
        when (selectedTab) {
            0 -> TokenDemoScreen(modifier = Modifier.padding(innerPadding))
            1 -> BasicDemoScreen(modifier = Modifier.padding(innerPadding))
            2 -> InspectorActivityDemoScreen(modifier = Modifier.padding(innerPadding))
        }
    }
}
