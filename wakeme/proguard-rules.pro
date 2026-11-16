# WakeMe ProGuard Rules
-keepattributes *Annotation*
-keepclassmembers class * {
    @androidx.room.* <methods>;
    @androidx.room.* <fields>;
}
-dontwarn org.osmdroid.**
-keep class org.osmdroid.** { *; }
