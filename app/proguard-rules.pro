# ML Kit Rules
-keep class com.google.android.gms.internal.mlkit_vision_text_bundled.** { *; }
-keep class com.google.android.gms.tasks.** { *; }

# Coroutines
-keep class kotlinx.coroutines.android.** { *; }

# Gson
-keep class com.example.superpower.data.** { *; }
-keep class com.google.gson.** { *; }

# Ensure ViewBinding works (if generic)
-keepclassmembers class * implements androidx.viewbinding.ViewBinding {
    public static androidx.viewbinding.ViewBinding bind(android.view.View);
    public static * inflate(android.view.LayoutInflater);
}
