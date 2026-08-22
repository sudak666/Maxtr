package ua.rytm.app.ui.screens.onboarding

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import ua.rytm.app.R

private data class OnboardingPage(val icon: ImageVector, @StringRes val title: Int, @StringRes val body: Int)

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val pages = remember {
        listOf(
            OnboardingPage(Icons.Filled.AccountBalanceWallet, R.string.onboarding_finance_title, R.string.onboarding_finance_body),
            OnboardingPage(Icons.Filled.CalendarMonth, R.string.onboarding_shifts_title, R.string.onboarding_shifts_body),
            OnboardingPage(Icons.Filled.Security, R.string.onboarding_security_title, R.string.onboarding_security_body),
        )
    }
    var page by remember { mutableIntStateOf(0) }
    val current = pages[page]
    Column(
        Modifier.fillMaxSize().testTag("onboarding-screen").padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onComplete) { Text(stringResource(R.string.action_skip)) }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(96.dp).background(
                    Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)),
                    CircleShape,
                ),
                contentAlignment = Alignment.Center,
            ) { Icon(current.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(48.dp)) }
            Spacer(Modifier.height(28.dp))
            Text(stringResource(current.title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(current.body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pages.indices.forEach { index ->
                    Box(
                        Modifier.size(if (index == page) 24.dp else 8.dp, 8.dp).background(
                            if (index == page) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            CircleShape,
                        ),
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { if (page == pages.lastIndex) onComplete() else page++ },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(stringResource(if (page == pages.lastIndex) R.string.action_done else R.string.action_next)) }
        }
    }
}
