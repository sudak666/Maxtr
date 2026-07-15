package ua.zminka.app.ui.finance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ua.zminka.app.data.model.Transaction
import ua.zminka.app.data.model.Wallet
import java.util.Locale

@Composable
fun FinanceScreen(viewModel: FinanceViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    var showAddSheet by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            // Mirrors the web client's .fin-fab + bottom-sheet-modal pattern
            // (see CLAUDE.md's Mobile UI redesign section) rather than an
            // always-expanded inline form.
            FloatingActionButton(onClick = { showAddSheet = true }) {
                Icon(Icons.Default.Add, contentDescription = "Нова операція")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            HeroBalance(totalUah = state.totalBalanceUah)
            WalletChipRow(wallets = state.wallets, balances = state.walletBalances)
            TransactionList(
                transactions = state.transactions,
                wallets = state.wallets,
                onDelete = viewModel::deleteTransaction,
            )
        }
    }

    if (showAddSheet) {
        AddTransactionSheet(
            wallets = state.wallets,
            onDismiss = { showAddSheet = false },
            onSave = { type, amount, currency, category, walletId, date, comment ->
                viewModel.addTransaction(type, amount, currency, category, walletId, date, comment)
                showAddSheet = false
            },
        )
    }
}

@Composable
private fun HeroBalance(totalUah: Double) {
    // Mirrors .hero-metric/.hero-balance — one large primary number, not a
    // grid of equal-weight stat cards (CLAUDE.md's Mobile UI redesign section).
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = "Загальний баланс",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        Text(
            text = formatAmount(totalUah) + " ₴",
            style = MaterialTheme.typography.headlineLarge,
        )
    }
}

@Composable
private fun WalletChipRow(wallets: List<Wallet>, balances: Map<String, Double>) {
    if (wallets.isEmpty()) return
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(wallets, key = { it.id }) { wallet ->
            val balance = balances[wallet.id] ?: 0.0
            SuggestionChip(
                onClick = {},
                label = {
                    Text("${wallet.name}: ${formatAmount(balance)} ${wallet.currency}")
                },
                colors = SuggestionChipDefaults.suggestionChipColors(),
            )
        }
    }
    Spacer(Modifier.size(12.dp))
}

@Composable
private fun TransactionList(
    transactions: List<Transaction>,
    wallets: List<Wallet>,
    onDelete: (Long) -> Unit,
) {
    if (transactions.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "Ще немає жодної операції",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(transactions, key = { it.id }) { tx ->
            TransactionRow(tx = tx, walletName = wallets.find { it.id == tx.wallet }?.name ?: tx.wallet, onDelete = onDelete)
        }
    }
}

// Native equivalent of the web client's .tx-item/.tx-swipe-delete
// swipe-to-delete pattern (see CLAUDE.md's Mobile UI redesign section) —
// uses Compose Material3's own SwipeToDismissBox rather than a hand-rolled
// pointer-drag port of the web version, since that's the idiomatic API for
// this exact gesture on Android. onDelete was already threaded all the way
// down from FinanceScreen -> TransactionList -> TransactionRow but never
// actually invoked from inside this composable — the parameter was dead
// weight until this. confirmValueChange (not a LaunchedEffect on
// currentValue) is what actually triggers the delete, matching Material3's
// documented pattern: returning true commits the swipe-away animation,
// false snaps the row back (used for the unsupported StartToEnd direction
// below, disabled via enableDismissFromStartToEnd).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionRow(tx: Transaction, walletName: String, onDelete: (Long) -> Unit) {
    val sign = when (tx.type) {
        "income" -> "+"
        "expense" -> "−"
        else -> ""
    }
    val color = when (tx.type) {
        "income" -> ua.zminka.app.ui.theme.ZminkaIncome
        "expense" -> ua.zminka.app.ui.theme.ZminkaExpense
        else -> MaterialTheme.colorScheme.onSurface
    }
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete(tx.id)
                true
            } else {
                false
            }
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.error, RoundedCornerShape(12.dp))
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Видалити",
                    tint = MaterialTheme.colorScheme.onError,
                )
            }
        },
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(tx.category, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "$walletName · ${tx.date}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
                Text(
                    "$sign${formatAmount(tx.amount)} ${tx.currency}",
                    color = color,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

private fun formatAmount(amount: Double): String = String.format(Locale.US, "%,.2f", amount)
