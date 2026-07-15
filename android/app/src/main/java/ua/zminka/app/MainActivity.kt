package ua.zminka.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import ua.zminka.app.ui.navigation.ZminkaNavHost
import ua.zminka.app.ui.theme.ZminkaTheme

class MainActivity : ComponentActivity() {
    // A no-op result callback is fine either way - MainScreen's own
    // registerCurrentToken() call still runs afterward regardless of the
    // outcome; if denied, NotificationManagerCompat.notify() (see
    // ZminkaMessagingService) just silently doesn't post anything, same
    // failure mode as the web client's ensureNotifPermission() returning
    // false.
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        maybeRequestNotificationPermission()
        setContent {
            ZminkaTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ZminkaNavHost()
                }
            }
        }
    }

    // POST_NOTIFICATIONS is a runtime-requested permission only from
    // Android 13 (TIRAMISU) on - older versions grant it automatically at
    // install time from the AndroidManifest.xml declaration alone.
    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
