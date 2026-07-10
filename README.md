# Spoon Compose Inspector

[![JitPack](https://jitpack.io/v/spooncast/Spoon-Compose-Inspector.svg)](https://jitpack.io/#spooncast/Spoon-Compose-Inspector)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![API](https://img.shields.io/badge/API-28%2B-brightgreen.svg)](https://developer.android.com/about/versions)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0%2B-7F52FF.svg)](https://kotlinlang.org)

> **A lightweight, on-device Jetpack Compose UI inspector for Android.** Tap any composable to view its layout, size, padding, colors, typography, and design-system token names — live, on a real device, without leaving the app or attaching a debugger.

Spoon Compose Inspector is a debug-only overlay that maps the visual properties of your Compose UI back to your **design tokens** (color, dimension, typography). It's built for designers, QA, and engineers who need to verify that what's on screen matches the design system — fast.

**Keywords:** Jetpack Compose inspector · Android Compose layout inspector · design token debugger · Compose UI overlay · on-device inspector · Compose design system QA · token resolver.

## Table of Contents

- [Features](#features)
- [Screenshots](#screenshots)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [Inspector Tooltip](#inspector-tooltip)
- [One-line Component Launcher](#one-line-component-launcher)
- [InspectablePreview](#inspectablepreview)
- [Advanced: InspectorActivity](#advanced-inspectoractivity)
- [R8 / ProGuard (Minified Builds)](#r8--proguard-minified-builds)
- [API Reference](#api-reference)
- [Requirements](#requirements)
- [License](#license)

## Features

- **Layout Inspector** — Tap any composable to see its bounds, padding, size, and position
- **Design Token Mapping** — Automatically resolves colors, dimensions, and typography to your design system token names
- **Component Type Detection** — Identifies Column, Row, Box, Text, Image, Icon, TextField and more
- **Layout Arrangement & Alignment** — Detects non-default `Arrangement` and `Alignment` on Row/Column/Box
- **Visual Property Extraction** — Background color, text color, gradient/brush, tint, opacity, border, shadow, corner radius
- **Semantics** — Shows testTag, contentDescription, and accessibility roles
- **One-line Launcher** — Open any component in a standalone inspector screen with a single line of code
- **Preview Integration** — Use `InspectablePreview` to add inspector support directly in `@Preview` functions
- **R8 / ProGuard Ready** — `@InspectableTokens` keeps token names intact even in minified builds
- **Non-intrusive** — Only activates in debug builds; zero overhead in release

## Screenshots

<!-- TODO: add screenshots — see note at the bottom of this section -->

| Inspect mode | Token tooltip | Standalone launcher |
|:---:|:---:|:---:|
| _floating button + tap-to-inspect_ | _size / padding / color / token names_ | _`launch { }` screen_ |

> 📸 **Images needed.** This table currently has placeholders. To make the README compelling (and to rank in GitHub/search results, where the first image is used as the social preview), please add:
> 1. **Inspect mode** — the floating button and a highlighted composable.
> 2. **Token tooltip** — a tapped element showing the property/token panel.
> 3. **Standalone launcher** — a component opened via `ComposeInspector.launch`.
>
> Drop them in a `docs/` or `art/` folder and update the links above (e.g. `![Inspect mode](docs/inspect-mode.png)`). A short GIF of tap-to-inspect at the top of the README works especially well.

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
    debugImplementation("com.github.spooncast:Spoon-Compose-Inspector:1.3.3")
}
```

> Using `debugImplementation` keeps the inspector out of your release APK entirely.

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

### 2. Register tokens and attach (recommended)

```kotlin
// Single color theme
ComposeInspector.setDesignTokens(context, MyColors, MyDimensions, MyTypography)

// Multiple color themes (light + dark auto-merge)
ComposeInspector.setDesignTokens(context, listOf(lightColors, darkColors), MyDimensions, MyTypography)
```

`setDesignTokens` builds token maps via reflection, registers them, and attaches the overlay — all in one call.

### 3. Or attach manually

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

### 4. Register design tokens individually (optional)

```kotlin
ComposeInspector.setColorTokens(ComposeInspector.buildColorMap(MyColors))
ComposeInspector.setDimensionTokens(ComposeInspector.buildDimensionMap(MyDimensions))
ComposeInspector.setTypographyTokens(ComposeInspector.buildTypoMap(MyTypography))
```

## Inspector Tooltip

When you tap an element, the tooltip displays:

| Section | Description |
|---------|-------------|
| **Title** | Component type (Column, Text, Icon...) |
| **resourceId** | `Modifier.testTag` value, shown as `resourceId : <tag>` right below the title |
| **Size** | Width x Height in dp |
| **Layout** | Non-default arrangement & alignment (Row/Column/Box) |
| **Padding** | Self padding with token names |
| **Typography** | Font size, weight, line height + token name |
| **Color** | Background color with swatch + token names |
| **Text Color** | Text color with token name |
| **Opacity** | Alpha value when < 1.0 |
| **Border** | Width + color with token name |
| **Shadow** | Elevation in dp |
| **Tint** | ColorFilter tint color with token name |
| **Corner Radius** | Radius in dp + token name |
| **Accessibility** | contentDescription |
| **Parent Padding** | Parent node padding (below separator) |
| **Spacing** | Sibling gap with token names (below separator) |

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

## R8 / ProGuard (Minified Builds)

Token names are resolved from your token classes **via reflection**. In minified builds, R8 renames fields (`primary` → `a`), which breaks token resolution. The library ships consumer ProGuard rules that keep the inspector itself and the Compose internals it inspects — but it cannot know about *your* token classes.

Annotate your design-token classes with `@InspectableTokens` so the bundled rules keep their field names:

```kotlin
@InspectableTokens
object MyColors {
    val primary = Color(0xFF6200EE)
    val secondary = Color(0xFF03DAC6)
}
```

**Library authors:** if your design system lives in a separate module that does *not* depend on this inspector, add keep rules directly to that module's `consumer-rules.pro` instead:

```pro
-keep class your.package.YourTokenClass { *; }
```

> Most apps build the inspector with `debugImplementation` against a non-minified debug build, in which case no extra rules are needed.

## API Reference

| Method | Description |
|--------|-------------|
| `ComposeInspector.init(enabled)` | Initialize the inspector |
| `ComposeInspector.setDesignTokens(context, colors, dimen, typo)` | Register all tokens + attach overlay in one call |
| `ComposeInspector.setDesignTokens(context, colorsList, dimen, typo)` | Same but accepts multiple color objects (auto-merge) |
| `ComposeInspector.attachToWindow(activity)` | Attach overlay to an activity |
| `ComposeInspector.launch(context) { }` | Open a component in standalone inspector |
| `ComposeInspector.setColorTokens(map)` | Register color token names |
| `ComposeInspector.setDimensionTokens(map)` | Register dimension token names |
| `ComposeInspector.setTypographyTokens(map)` | Register typography token names |
| `ComposeInspector.buildColorMap(obj)` | Build color map from an object via reflection |
| `ComposeInspector.buildDimensionMap(obj)` | Build dimension map from an object via reflection |
| `ComposeInspector.buildTypoMap(obj)` | Build typography map from an object via reflection |
| `ComposeInspector.setColorPriorityPrefixes(list)` | Set prefix priority for color token display |
| `InspectablePreview(label, buttonText) { }` | Preview card with inspector launch button |
| `@InspectableTokens` | Annotation that keeps token classes from R8/ProGuard obfuscation |

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
