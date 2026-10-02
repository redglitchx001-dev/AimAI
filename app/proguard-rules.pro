-keep class com.redglitchx.aimai.** { *; }
-keepclassmembers class * {
    @androidx.compose.runtime.Composable *;
}
-dontwarn androidx.**
-keep class androidx.compose.** { *; }
