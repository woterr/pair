# Pair ProGuard/R8 rules

# ---------------------------------------------------------------------------
# Firebase
# ---------------------------------------------------------------------------
# Firebase uses reflection to discover generated component classes; keep them.
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-keep class com.google.firebase.components.** { *; }
-dontwarn com.google.firebase.**

# Realtime Database model classes are resolved reflectively by the SDK.
-keepclassmembers class com.wood.pair.data.model.** {
    <fields>;
    <init>(...);
}

# ---------------------------------------------------------------------------
# Kotlin
# ---------------------------------------------------------------------------
-dontwarn kotlinx.coroutines.**
-keepattributes *Annotation*, InnerClasses, Signature, RuntimeVisible*Annotations

# ---------------------------------------------------------------------------
# Compose
# ---------------------------------------------------------------------------
-dontwarn androidx.compose.**
