package com.spoonlabs.composeinspector

/**
 * Marks a class as containing design token fields that should be preserved
 * from R8/ProGuard obfuscation.
 *
 * When [ComposeInspector.buildColorMap], [ComposeInspector.buildDimensionMap],
 * or [ComposeInspector.buildTypoMap] is used, field names are extracted via reflection.
 * In minified builds, R8 renames fields (e.g., `primary` → `a`), breaking token resolution.
 *
 * Annotate your token classes with `@InspectableTokens` to prevent this:
 * ```kotlin
 * @InspectableTokens
 * object MyColors {
 *     val primary = Color(0xFF6200EE)
 *     val secondary = Color(0xFF03DAC6)
 * }
 * ```
 *
 * This library's bundled consumer ProGuard rules automatically keep all classes
 * annotated with `@InspectableTokens` and their members.
 *
 * **For library authors**: If your design system is a separate library module that
 * does not depend on this inspector, add keep rules directly in your library's
 * `consumer-rules.pro` instead:
 * ```
 * -keep class your.package.YourTokenClass { *; }
 * ```
 */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS)
annotation class InspectableTokens
