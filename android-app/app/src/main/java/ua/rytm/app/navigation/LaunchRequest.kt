package ua.rytm.app.navigation

import android.content.Intent

data class LaunchRequest(
    val route: String,
    val openTransaction: Boolean = false,
    val sharedText: String? = null,
    val nonce: Long = System.nanoTime(),
)

fun parseLaunchRequest(intent: Intent?): LaunchRequest? {
    intent ?: return null
    if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()?.takeIf(String::isNotEmpty)?.take(500)
        return LaunchRequest(RytmDestination.Finance.route, openTransaction = true, sharedText = text)
    }
    val uri = intent.data ?: return null
    val segments = uri.pathSegments
    val candidate = when {
        uri.scheme == "rytm" -> uri.host
        uri.scheme == "https" && uri.host == "maxtr-c238f.web.app" && segments.firstOrNull() == "app" -> segments.getOrNull(1)
        else -> null
    } ?: return null
    val route = RytmDestination.entries.firstOrNull { it.route == candidate }?.route ?: return null
    val openTransaction = route == RytmDestination.Finance.route &&
        (segments.lastOrNull() == "new" || uri.getQueryParameter("action") == "new")
    return LaunchRequest(route, openTransaction)
}
