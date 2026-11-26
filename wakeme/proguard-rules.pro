# WakeMe ProGuard Rules
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable

# Room Database
-keepclassmembers class * {
    @androidx.room.* <methods>;
    @androidx.room.* <fields>;
}
-keep class * extends androidx.room.RoomDatabase
-keep class com.wakeme.app.data.local.** { *; }
-dontwarn androidx.room.paging.**

# OsmDroid (OpenStreetMap)
-dontwarn org.osmdroid.**
-keep class org.osmdroid.** { *; }

# Android Entry Points & Models
-keep class com.wakeme.app.WakeMeApp
-keep class com.wakeme.app.MainActivity
-keep class com.wakeme.app.alarm.AlarmDismissActivity
-keep class com.wakeme.app.service.WakeMeTrackingService
-keep class com.wakeme.app.model.** { *; }

# Play Services Location & Coroutines
-keep class com.google.android.gms.location.** { *; }
-dontwarn kotlinx.coroutines.**

