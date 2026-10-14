package ua.rytm.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext
import ua.rytm.app.RytmApplication

/** The app's dependency container (repositories, stores) from a composable. */
@Composable
@ReadOnlyComposable
fun rytmApp(): RytmApplication = LocalContext.current.applicationContext as RytmApplication
