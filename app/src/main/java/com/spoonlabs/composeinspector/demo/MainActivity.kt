package com.spoonlabs.composeinspector.demo

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.spoonlabs.composeinspector.ComposeInspector
import com.spoonlabs.composeinspector.demo.theme.clearTokens
import com.spoonlabs.composeinspector.demo.theme.registerMaterial3Tokens
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DemoApp(activity: Activity) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val context = LocalContext.current

    val tabs = listOf(
        stringResource(R.string.tab_custom_tokens),
        stringResource(R.string.tab_material3),
        stringResource(R.string.tab_without_tokens),
        stringResource(R.string.tab_component_test),
    )

    LaunchedEffect(selectedTab) {
        when (selectedTab) {
            0 -> registerTokens()
            1 -> registerMaterial3Tokens(context)
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
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(R.string.app_name)) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    edgePadding = 16.dp,
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        when (selectedTab) {
            0 -> TokenDemoScreen(modifier = Modifier.padding(innerPadding))
            1 -> TokenDemoScreen(modifier = Modifier.padding(innerPadding))
            2 -> BasicDemoScreen(modifier = Modifier.padding(innerPadding))
            3 -> InspectorActivityDemoScreen(modifier = Modifier.padding(innerPadding))
        }
    }
}
