# StopNudge ProGuard & R8 Rules
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep Room entities, DAOs, and database classes
-keep class androidx.room.** { *; }
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }

# Keep Kotlin coroutines internals
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Strip debug and verbose logging in release builds to prevent information leakage
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}
