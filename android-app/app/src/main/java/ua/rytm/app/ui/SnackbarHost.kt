package ua.rytm.app.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The app-wide snackbar host, provided once by `RytmNavHost`.
 *
 * Before this, three screens had their own `Scaffold(snackbarHost = …)` while
 * three others showed the very same class of message — a transient
 * "couldn't save, try again" — in a modal `AlertDialog`. Same event, two
 * completely different interruption levels, which is what made the app feel
 * stitched together from two products.
 *
 * Rule: `AlertDialog` is for decisions the user has to make (confirmations);
 * notifications go to the snackbar.
 *
 * Null outside the nav graph (e.g. the login screen, which is rendered before
 * `RytmNavHost` exists and provides its own host).
 */
val LocalSnackbarHost = staticCompositionLocalOf<SnackbarHostState?> { null }
