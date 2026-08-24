# Proguard rules for WhisperFlow
-keepattributes *Annotation*
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}
-keep class com.wisprflow.android.data.models.** { *; }
-keep class kotlinx.serialization.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
