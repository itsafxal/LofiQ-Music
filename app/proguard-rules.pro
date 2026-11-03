# LofiQ ProGuard / R8 Optimization & Security Hardening Rules

# General Attributes
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Kotlinx Serialization
-dontnote kotlinx.serialization.SerializationKt
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers @kotlinx.serialization.Serializable class * {
    *** Companion;
}
-keep @kotlinx.serialization.Serializable class * { *; }

# Room Database
-keepclassmembers class * {
    @androidx.room.* <methods>;
}
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# AndroidX Media3 & ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# OkHttp & Retrofit
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keepattributes Signature
-keepattributes Exceptions

# Coil Image Loader
-keep class coil.** { *; }
-dontwarn coil.**

# Firebase & App Check
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**
