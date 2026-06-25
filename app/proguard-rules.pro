# ---- Chaquopy / embedded Python ----
-keep class com.chaquo.python.** { *; }
-keep class org.python.** { *; }
-dontwarn com.chaquo.python.**

# App code that bridges to Python or is (de)serialized via org.json
-keep class com.fortnitecloudsync.data.python.** { *; }
-keep class com.fortnitecloudsync.data.model.** { *; }

# JSON (used to marshal results across the Kotlin <-> Python bridge)
-keep class org.json.** { *; }

# ViewModels are instantiated reflectively by the framework, so keep their
# constructors.
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}
-keep class com.fortnitecloudsync.ui.MainViewModel { <init>(...); }
