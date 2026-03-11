# ComposeInspector - Consumer ProGuard/R8 Rules
# These rules are automatically included when the library is consumed.

# Keep the inspector library itself
-keep class com.spoonlabs.composeinspector.** { *; }

# Keep Compose internal classes accessed via Class.forName / reflection
-keep class androidx.compose.ui.node.LayoutNode { *; }
-keep class androidx.compose.ui.platform.AndroidComposeView { *; }
-keep class androidx.compose.ui.layout.LayoutCoordinates { *; }
-keep class androidx.compose.ui.layout.LayoutCoordinatesKt { *; }
-keep class androidx.compose.ui.node.ModifierInfo { *; }

# Keep InspectorLauncherActivity (referenced in Intent)
-keep class com.spoonlabs.composeinspector.InspectorLauncherActivity

# Keep Compose classes and members accessed via reflection
# Inspector uses className.contains("TextStyle"), className.contains("SolidColor"), etc.
# so class names must be preserved (not just members)
-keep class androidx.compose.ui.node.** { *; }
-keep class androidx.compose.ui.layout.** { *; }
-keep class androidx.compose.ui.draw.** { *; }
-keep class androidx.compose.ui.graphics.** { *; }
-keep class androidx.compose.ui.text.** { *; }
-keep class androidx.compose.foundation.layout.** { *; }
-keep class androidx.compose.foundation.** { *; }
-keep class androidx.compose.material.** { *; }
-keep class androidx.compose.material3.** { *; }

# Keep Modifier implementations (accessed via getModifierInfo reflection)
-keep class * implements androidx.compose.ui.Modifier$Element { *; }

# @InspectableTokens — keep annotation and annotated classes for token name reflection
-keep @interface com.spoonlabs.composeinspector.InspectableTokens
-keep @com.spoonlabs.composeinspector.InspectableTokens class * { *; }
