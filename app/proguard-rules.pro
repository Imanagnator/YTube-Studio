# YTube Studio — Production R8 / ProGuard Rules
# Author: Tamal Mitra (https://github.com/Imanagnator/YTube-Studio)

-keepattributes SourceFile,LineNumberTable,Signature,*Annotation*,InnerClasses,EnclosingMethod

# Mozilla GeckoView JNI & reflection classes
-keep class org.mozilla.gecko.** { *; }
-keep class org.mozilla.geckoview.** { *; }
-dontwarn org.mozilla.gecko.**
-dontwarn org.mozilla.geckoview.**

# youtubedl-android, FFmpeg & Jackson JSON metadata models
-keep class com.yausername.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-dontwarn com.yausername.**
-dontwarn com.fasterxml.jackson.**
-dontwarn org.apache.commons.**

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
