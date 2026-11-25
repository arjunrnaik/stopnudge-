# WakeMe ProGuard Rules
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable

# Room Database
-keepclassmembers class * {
    @androidx.room.* <methods>;
    @androidx.room.* <fields>;
}
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# OsmDroid (OpenStreetMap)
-dontwarn org.osmdroid.**
-keep class org.osmdroid.** { *; }

# WakeMe App Classes (DAOs, Entities, Services, ViewModels, Models, UI)
-keep class com.wakeme.app.** { *; }

# Play Services Location & Coroutines
-keep class com.google.android.gms.location.** { *; }
-dontwarn kotlinx.coroutines.**

