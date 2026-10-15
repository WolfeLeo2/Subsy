# onnxruntime's native code finds its Java classes and fields by name over JNI.
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**
