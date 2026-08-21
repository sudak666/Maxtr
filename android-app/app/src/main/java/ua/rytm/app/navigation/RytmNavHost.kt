package ua.rytm.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ua.rytm.app.ui.screens.DebtScreen
import ua.rytm.app.ui.screens.SettingsScreen
import ua.rytm.app.ui.screens.finance.FinanceScreen
import ua.rytm.app.ui.screens.shifts.ShiftsScreen
import ua.rytm.app.ui.screens.shopping.ShoppingScreen

@Composable
fun RytmNavHost() {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = { RytmBottomBar(navController) },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = RytmDestination.Finance.route,
            modifier = androidx.compose.ui.Modifier.padding(innerPadding),
        ) {
            composable(RytmDestination.Finance.route) { FinanceScreen() }
            composable(RytmDestination.Shifts.route) { ShiftsScreen() }
            composable(RytmDestination.Debt.route) { DebtScreen() }
            composable(RytmDestination.Shopping.route) { ShoppingScreen() }
            composable(RytmDestination.Settings.route) { SettingsScreen() }
        }
    }
}

// Matches the PWA's .tabs: a detached rounded pill inset from the screen
// edges (14dp on all sides, matching index.html's `left/right/bottom:14px`)
// with a shadow, not an edge-to-edge Material3 NavigationBar. Each tab's
// icon sits in its own circular badge that only picks up a per-tab color
// gradient (RytmDestination.activeGradient) while selected — mirrors the
// PWA's .tab-btn.tab-c-*.active .tab-icon-wrap overrides.
@Composable
private fun RytmBottomBar(navController: androidx.navigation.NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val shape = RoundedCornerShape(26.dp)

    Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(elevation = 14.dp, shape = shape, ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.25f))
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, shape)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            RytmDestination.entries.forEach { destination ->
                val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
                RytmTabButton(
                    destination = destination,
                    selected = selected,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        navController.navigate(destination.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun RytmTabButton(destination: RytmDestination, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier.clickable(onClick = onClick).padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (selected) Brush.linearGradient(destination.activeGradient) else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                destination.icon,
                contentDescription = stringResource(destination.labelRes),
                tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(23.dp),
            )
        }
        Text(
            stringResource(destination.labelRes),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
