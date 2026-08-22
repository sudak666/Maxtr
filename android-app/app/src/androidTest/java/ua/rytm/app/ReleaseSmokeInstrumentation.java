package ua.rytm.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

/** Dependency-minimal runtime gate for the actual R8/resource-shrunk release APK. */
public final class ReleaseSmokeInstrumentation extends Instrumentation {
    private static final String TAG = "RytmReleaseSmoke";
    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            Context target = getTargetContext();
            Log.i(TAG, "initializing critical SDK entry points");
            Bundle started = testStatus(".", 1);
            sendStatus(1, started);
            Class<?> probe = Class.forName("ua.rytm.app.ReleaseSdkProbe", true, target.getClassLoader());
            probe.getMethod("run", Context.class).invoke(null, target);

            Intent launch = target.getPackageManager().getLaunchIntentForPackage(target.getPackageName());
            require(launch);
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            target.startActivity(launch);
            waitForIdleSync();
            Log.i(TAG, "passed");
            sendStatus(0, testStatus("", 1));
            result.putString("stream", "R8 release SDK/activity smoke passed\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            Log.e(TAG, "failed", failure);
            result.putString("stream", "R8 release smoke failed: " + failure + "\n");
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private static void require(Object value) {
        if (value == null) throw new AssertionError("Required release entry point returned null");
    }

    private static Bundle testStatus(String stream, int current) {
        Bundle status = new Bundle();
        status.putString("id", "RytmReleaseSmoke");
        status.putInt("numtests", 1);
        status.putInt("current", current);
        status.putString("class", "ua.rytm.app.ReleaseSdkProbe");
        status.putString("test", "criticalSdkAndActivityEntryPoints");
        status.putString("stream", stream);
        return status;
    }
}
