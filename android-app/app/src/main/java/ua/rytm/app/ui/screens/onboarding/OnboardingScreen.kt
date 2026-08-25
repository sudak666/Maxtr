package ua.rytm.app.ui.screens.onboarding

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.launch
import ua.rytm.app.R
import ua.rytm.app.ui.icons.RytmIcons
import ua.rytm.app.ui.icons.AccountBalanceWallet
import ua.rytm.app.ui.icons.CalendarMonth
import ua.rytm.app.ui.icons.Security

private data class OnboardingPage(val icon: ImageVector, @StringRes val title: Int, @StringRes val body: Int)

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val pages = remember {
        listOf(
            OnboardingPage(RytmIcons.AccountBalanceWallet, R.string.onboarding_finance_title, R.string.onboarding_finance_body),
            OnboardingPage(RytmIcons.CalendarMonth, R.string.onboarding_shifts_title, R.string.onboarding_shifts_body),
            OnboardingPage(RytmIcons.Security, R.string.onboarding_security_title, R.string.onboarding_security_body),
        )
    }
    // rememberSaveable, not remember: a rotation used to bounce the user
    // back to page 1.
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val page = pagerState.currentPage
    BoxWithConstraints(Modifier.fillMaxSize().testTag("onboarding-screen")) {
    val compactHeight = maxHeight < 500.dp
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp, vertical = if (compactHeight) 8.dp else 24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onComplete) { Text(stringResource(R.string.action_skip)) }
        }
        // A real HorizontalPager: the only way forward used to be the Next
        // button — no swipe, no way back.
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth()) { index ->
            val pageData = pages[index]
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(if (compactHeight) 64.dp else 96.dp).background(
                        Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)),
                        CircleShape,
                    ),
                    contentAlignment = Alignment.Center,
                ) { Icon(pageData.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(if (compactHeight) 32.dp else 48.dp)) }
                Spacer(Modifier.height(if (compactHeight) 8.dp else 28.dp))
                Text(stringResource(pageData.title), style = if (compactHeight) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                Spacer(Modifier.height(if (compactHeight) 4.dp else 12.dp))
                Text(stringResource(pageData.body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            // The dots were decorative Boxes: TalkBack could not tell you
            // which page you were on.
            val pageStatus = stringResource(R.string.onboarding_page_status, page + 1, pages.size)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = pageStatus },
            ) {
                pages.indices.forEach { index ->
                    Box(
                        Modifier.size(if (index == page) 24.dp else 8.dp, 8.dp).background(
                            if (index == page) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            CircleShape,
                        ),
                    )
                }
            }
            Spacer(Modifier.height(if (compactHeight) 8.dp else 20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (page > 0) {
                    OutlinedButton(
                        onClick = { scope.launch { pagerState.animateScrollToPage(page - 1) } },
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                    ) { Text(stringResource(R.string.action_back)) }
                }
                Button(
                    onClick = {
                        if (page == pages.lastIndex) onComplete()
                        else scope.launch { pagerState.animateScrollToPage(page + 1) }
                    },
                    // heightIn: a fixed 52dp clipped the label at large fonts.
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                ) { Text(stringResource(if (page == pages.lastIndex) R.string.action_done else R.string.action_next)) }
            }
        }
    }
    }
}
