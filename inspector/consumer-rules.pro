# ComposeInspector - Consumer ProGuard/R8 Rules
# These rules are automatically included when the library is consumed.

# Keep the inspector library itself
-keep class com.spoonlabs.composeinspector.** { *; }

# Keep Compose internal classes accessed via reflection
-keep class androidx.compose.ui.node.LayoutNode { *; }
-keep class androidx.compose.ui.platform.AndroidComposeView { *; }
-keep class androidx.compose.ui.layout.LayoutCoordinates { *; }
-keep class androidx.compose.ui.layout.LayoutCoordinatesKt { *; }

# Keep modifier info classes used by LayoutInspector
-keep class androidx.compose.ui.node.ModifierInfo { *; }
-keepclassmembers class * implements androidx.compose.ui.Modifier$Element { *; }

# Keep Compose internal layout/drawing classes accessed via reflection
-keepclassmembers class androidx.compose.ui.node.** { *; }
-keepclassmembers class androidx.compose.ui.layout.** { *; }
-keepclassmembers class androidx.compose.ui.draw.** { *; }
-keepclassmembers class androidx.compose.ui.graphics.** { *; }
-keepclassmembers class androidx.compose.foundation.layout.** { *; }
-keepclassmembers class androidx.compose.foundation.** { *; }
-keepclassmembers class androidx.compose.material.** { *; }
-keepclassmembers class androidx.compose.material3.** { *; }
-keepclassmembers class androidx.compose.ui.text.** { *; }

# Keep InspectorLauncherActivity (referenced in Intent)
-keep class com.spoonlabs.composeinspector.InspectorLauncherActivity
