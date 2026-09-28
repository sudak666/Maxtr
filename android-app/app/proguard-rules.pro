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

# WorkManager (used by Glance to render the home-screen widget) instantiates
# these reflectively; R8 stripped OverwritingInputMerger's no-arg constructor
# in release builds and the widget stayed on its loading spinner forever
# (seen live on the A51, 2026-09-28).
-keep class * extends androidx.work.InputMerger { <init>(); }
-keep class * extends androidx.work.ListenableWorker { <init>(android.content.Context, androidx.work.WorkerParameters); }
