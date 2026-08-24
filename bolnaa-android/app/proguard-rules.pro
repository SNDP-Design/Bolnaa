# Proguard rules for Bolnaa
-keepattributes *Annotation*
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}
-keep class com.bolnaa.android.data.models.** { *; }
-keep class kotlinx.serialization.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
