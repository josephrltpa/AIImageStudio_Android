# Keep ONNX Runtime native bindings
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# Keep Room entities
-keep class com.aiimagestudio.data.local.db.** { *; }

# Keep Hilt generated code
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager

# Keep TensorFlow Lite classes
-keep class org.tensorflow.lite.** { *; }
-keep class org.tensorflow.lite.support.** { *; }

# Keep your model data classes (change the package name to match yours)
-keep class com.yourname.aiimagestudio.ml.** { *; }

# Keep anything related to ByteBuffer and Bitmap processing
-keepclassmembers class * {
    public <init>(android.graphics.Bitmap);
}
