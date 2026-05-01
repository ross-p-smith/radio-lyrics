# ----- Hilt / Dagger -----
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.* { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }

# ----- Room -----
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *
-keepclassmembers class * { @androidx.room.* *; }

# ----- Moshi (codegen JsonClass adapters) -----
-keep @com.squareup.moshi.JsonClass class * { *; }
-keep class **JsonAdapter { *; }
-keepnames @com.squareup.moshi.JsonClass class *
-keepclassmembers @com.squareup.moshi.JsonClass class * {
    <init>(...);
    <fields>;
}

# ----- Media3 (reflection-based component discovery) -----
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# ----- omri-usb / OMRI consumers (LGPL AAR) -----
-keep class org.omri.** { *; }
-dontwarn org.omri.**

# ----- Kotlin coroutines / serialization helpers -----
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlin.Metadata { *; }
