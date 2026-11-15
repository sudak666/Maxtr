# R8 is enabled for release (app/build.gradle.kts). The rules below cover the
# reflection-based libraries this app uses; everything else (Compose, Room,
# AndroidX) ships consumer rules of its own.

# --- Firebase / Firestore ---
# No class of this app is (de)serialized reflectively: every Firestore read
# and write maps fields by hand (no toObject()/POJO set()), so the old blanket
# keep on ua.rytm.app.data.** (all fields and constructors) only blocked R8
# from shrinking. Removed 2026-11-15 after a minified QA run.
-keepattributes Signature,*Annotation*,EnclosingMethod,InnerClasses
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

# PDFBox loads fonts and filters reflectively (PUMB statements).
-keep class com.tom_roush.** { *; }
-dontwarn com.gemalto.jp2.**
# bcpkix is excluded (see app/build.gradle.kts): only certificate-encrypted PDFs need it.
-dontwarn org.bouncycastle.cert.**
-dontwarn org.bouncycastle.cms.**
-dontwarn org.bouncycastle.operator.**
-dontwarn org.bouncycastle.asn1.**
