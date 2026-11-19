# YTube Studio — Production R8 / ProGuard Rules
# Author: Tamal Mitra (https://github.com/Imanagnator/YTube-Studio)

-keepattributes SourceFile,LineNumberTable,Signature,*Annotation*,InnerClasses,EnclosingMethod,Exceptions

# Mozilla GeckoView JNI & reflection classes
-keep class org.mozilla.gecko.** { *; }
-keep class org.mozilla.geckoview.** { *; }
-dontwarn org.mozilla.gecko.**
-dontwarn org.mozilla.geckoview.**

# youtubedl-android, FFmpeg, Jackson JSON mappers & archive decompression (CRITICAL for Release APK!)
-keep class com.yausername.** { *; }
-keep interface com.yausername.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-keep class org.apache.commons.** { *; }
-keep class org.tukaani.** { *; }
-keepclassmembers class com.yausername.youtubedl_android.mapper.** { *; }
-keepclassmembers class * {
    @com.fasterxml.jackson.annotation.* <fields>;
    @com.fasterxml.jackson.annotation.* <methods>;
}
-dontwarn com.yausername.**
-dontwarn com.fasterxml.jackson.**
-dontwarn org.apache.commons.**
-dontwarn org.tukaani.**
-dontwarn java.nio.file.**
-dontwarn javax.xml.**
-dontwarn org.w3c.dom.**

# App Data & Model classes
-keep class com.example.data.model.** { *; }
-keep class com.example.gecko.** { *; }

# AndroidX Room entities & DAOs
-keep class com.example.data.local.** { *; }
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *

# AndroidX Media3 / ExoPlayer & MediaSession
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# OkHttp & Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
