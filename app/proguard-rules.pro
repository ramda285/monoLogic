# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified in the
# Android SDK tools directory.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep kotlinx.serialization annotations
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Keep Jsoup HTML parser
-keep public class org.jsoup.** { public *; }

# Keep OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
