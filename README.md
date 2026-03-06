# Spoon Compose Inspector

A lightweight Compose UI inspector for Android. Tap any element to view its layout properties, design tokens (color, dimension, typography), and component hierarchy — all without leaving the app.

## Features

- **Layout Inspector** — Tap any composable to see its bounds, padding, size, and position
- **Design Token Mapping** — Automatically resolves colors, dimensions, and typography to your design system token names
- **One-line Launcher** — Open any component in a standalone inspector screen with a single line of code
- **Preview Integration** — Use `InspectablePreview` to add inspector support directly in `@Preview` functions
- **Non-intrusive** — Only activates in debug builds; zero overhead in release

## Installation

Add JitPack to your project-level `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        maven { url = uri("https://jitpack.io") }
    }
}
```

Add the dependency (debug only):

```kotlin
dependencies {
    debugImplementation("com.github.spooncast:Spoon-Compose-Inspector:1.0.0")
}
```

## Quick Start

### 1. Initialize

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ComposeInspector.init(enabled = BuildConfig.DEBUG)
    }
}
```

### 2. Attach to a screen

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MyApp() }
        ComposeInspector.attachToWindow(this)
    }
}
```

A floating button appears. Tap it to enter inspect mode, then tap any element.

### 3. Register design tokens (optional)

```kotlin
ComposeInspector.setColorTokens(ComposeInspector.buildColorMap(MyColors))
ComposeInspector.setDimensionTokens(ComposeInspector.buildDimensionMap(MyDimensions))
ComposeInspector.setTypographyTokens(ComposeInspector.buildTypoMap(MyTypography))
```

Token names will appear in the inspector tooltip when you tap an element.

## One-line Component Launcher

Open any composable in a standalone inspector screen:

```kotlin
ComposeInspector.launch(context) {
    MyComponent()
}
```

## InspectablePreview

A drop-in card for `@Preview` functions that renders the component and adds an "Open in Inspector" button:

```kotlin
@Preview
@Composable
private fun MyButtonPreview() {
    InspectablePreview(label = "MyButton") {
        MyButton(text = "Click me")
    }
}
```

## Advanced: InspectorActivity

For full control (custom themes, token registration per screen), extend `InspectorActivity`:

```kotlin
class MyTestActivity : InspectorActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setInspectorContent {
            MyTheme {
                MyComponent()
            }
        }
    }
}
```

## API Reference

| Method | Description |
|--------|-------------|
| `ComposeInspector.init(enabled)` | Initialize the inspector |
| `ComposeInspector.attachToWindow(activity)` | Attach overlay to an activity |
| `ComposeInspector.launch(context) { }` | Open a component in standalone inspector |
| `ComposeInspector.setColorTokens(map)` | Register color token names |
| `ComposeInspector.setDimensionTokens(map)` | Register dimension token names |
| `ComposeInspector.setTypographyTokens(map)` | Register typography token names |
| `ComposeInspector.buildColorMap(obj)` | Build color map from an object via reflection |
| `ComposeInspector.buildDimensionMap(obj)` | Build dimension map from an object via reflection |
| `ComposeInspector.buildTypoMap(obj)` | Build typography map from an object via reflection |
| `InspectablePreview(label, buttonText) { }` | Preview card with inspector launch button |

## Requirements

- Android SDK 28+
- Jetpack Compose
- Kotlin 2.0+

## License

```
Copyright 2025 Spoon Radio

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
