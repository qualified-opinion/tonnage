# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Room Database keep rules
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Keep entities, DAOs, and database classes
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keepclassmembers class * {
    @androidx.room.Dao *;
    @androidx.room.Entity *;
}

# Kotlin Coroutines & Serialization
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Jetpack Compose keep rules
-keep class androidx.compose.** { *; }
