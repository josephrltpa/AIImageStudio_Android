# Keep ONNX Runtime native bindings
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# Keep Room entities
-keep class com.aiimagestudio.data.local.db.** { *; }

# Keep Hilt generated code
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager
