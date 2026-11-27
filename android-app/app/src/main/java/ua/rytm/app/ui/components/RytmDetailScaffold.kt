package ua.rytm.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import ua.rytm.app.R
import ua.rytm.app.ui.icons.ArrowBack
import ua.rytm.app.ui.icons.RytmIcons
import ua.rytm.app.ui.theme.RytmSpacing

/**
 * A screen one level below a tab (Capital, Cash forecast): a pinned top bar
 * with «Назад» and the title, then one scrolling column. The bar takes a
 * tonal fill once the content scrolls under it, as M3 pinned bars do. The
 * bottom capsule is hidden on these routes (RytmNavHost), so the column
 * only clears the system navigation bar.
 *
 * Bottom sheets were fine while these were a glance; with a scrubbable
 * chart and a day-by-day list they became places people read, and a sheet
 * dismissed itself on a downward drag meant for scrolling (2026-11-27).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RytmDetailScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val background = MaterialTheme.colorScheme.background
    Column(
        Modifier
            .fillMaxSize()
            .background(background)
            .nestedScroll(scrollBehavior.nestedScrollConnection),
    ) {
        TopAppBar(
            title = {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(RytmIcons.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
            },
            // The status bar inset is already applied above the nav graph.
            windowInsets = WindowInsets(0),
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = background,
                scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
            scrollBehavior = scrollBehavior,
        )
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = RytmSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(RytmSpacing.md),
        ) {
            Spacer(Modifier)
            content()
            Spacer(Modifier.height(RytmSpacing.lg))
        }
    }
}
