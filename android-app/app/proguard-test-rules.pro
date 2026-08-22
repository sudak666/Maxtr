# The release-under-test stays fully minified. Keep only the instrumentation
# harness in the separate test APK so AndroidJUnitRunner can discover and run it.
-keep class ua.rytm.app.ReleaseSmokeInstrumentation { *; }
