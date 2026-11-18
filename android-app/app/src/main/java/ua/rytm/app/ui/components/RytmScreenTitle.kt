package ua.rytm.app.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import ua.rytm.app.ui.theme.RytmSpacing

/**
 * The title a tab screen opens with (Зміни, Позики, Налаштування). Finance
 * has none: its balance is the header. Added 2026-11-18 so every section
 * says where you are, the way the Settings tab already did.
 */
@Composable
fun RytmScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(top = RytmSpacing.sm).semantics { heading() },
    )
}
