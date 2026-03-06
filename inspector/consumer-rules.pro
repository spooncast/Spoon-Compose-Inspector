# ComposeInspector - Consumer ProGuard/R8 Rules
# These rules are automatically included when the library is consumed.

# Keep Compose internal classes accessed via reflection
-keep class androidx.compose.ui.node.LayoutNode { *; }
-keep class androidx.compose.ui.platform.AndroidComposeView { *; }

# Keep modifier info classes used by LayoutInspector
-keepclassmembers class * {
    @androidx.compose.ui.Modifier$Element *;
}

# Keep InspectorLauncherActivity (referenced in Intent)
-keep class com.spoonlabs.composeinspector.InspectorLauncherActivity
