# Retrofit
-keepattributes Signature
-keepattributes *Annotation*
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# Gson
-keep class com.google.gson.** { *; }
-keep class com.fortnitecloudsync.data.remote.** { *; }
-keep class com.fortnitecloudsync.data.model.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
