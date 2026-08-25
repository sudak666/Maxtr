# R8 is enabled for release (app/build.gradle.kts). The rules below cover the
# reflection-based libraries this app uses; everything else (Compose, Room,
# AndroidX) ships consumer rules of its own.

# --- Firebase / Firestore ---
# Firestore serializes POJOs reflectively; the app's Firestore models live in
# data/ and are read back field-by-field.
-keepattributes Signature,*Annotation*,EnclosingMethod,InnerClasses
-keepclassmembers class ua.rytm.app.data.** {
  <init>();
  <fields>;
}
-keepnames class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# --- Kotlin coroutines / serialization internals ---
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlin.Metadata { *; }

# --- Keep crash-report readability ---
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
