package ua.rytm.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

val LocalHideAmounts = staticCompositionLocalOf { false }

@Composable
fun maskedAmount(value: String): String = if (LocalHideAmounts.current) "••••" else value
