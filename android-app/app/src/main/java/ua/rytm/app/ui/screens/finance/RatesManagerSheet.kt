package ua.rytm.app.ui.screens.finance
import androidx.compose.foundation.layout.navigationBarsPadding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
import ua.rytm.app.R
import kotlinx.coroutines.launch
import ua.rytm.app.data.CurrencyRatesSyncRepository
import ua.rytm.app.data.DEFAULT_PROFILE_ID
import ua.rytm.app.data.FinanceRepository
import ua.rytm.app.data.SEED_RATES
import ua.rytm.app.data.local.SettingsStore
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RatesManagerSheet(
    uid: String,
    profileId: String = DEFAULT_PROFILE_ID,
    financeRepository: FinanceRepository,
    syncRepository: CurrencyRatesSyncRepository,
    settingsStore: SettingsStore,
    onDismiss: () -> Unit,
) {
    val rates by financeRepository.currencyRates.collectAsState(initial = emptyMap())
    val source by settingsStore.ratesSource.collectAsState(initial = "nbu")
    val updatedAt by settingsStore.ratesUpdatedAt.collectAsState(initial = null)
    var busy by remember { mutableStateOf(false) }
    @StringRes var messageRes by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun refresh(selectedSource: String = source) {
        if (busy) return
        scope.launch {
            busy = true
            messageRes = null
            runCatching { syncRepository.refreshOnline(uid, profileId, selectedSource == "privat") }
                .onSuccess { settingsStore.setRatesUpdatedAt(it); messageRes = R.string.rates_updated }
                .onFailure { messageRes = R.string.rates_update_failed }
            busy = false
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val locale = Locale.forLanguageTag(LocalConfiguration.current.locales[0].toLanguageTag())
            val numberFormat = remember(locale) { NumberFormat.getNumberInstance(locale).apply { minimumFractionDigits = 2; maximumFractionDigits = 4 } }
            val updatedText = updatedAt?.let {
                stringResource(R.string.rates_updated_at, DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale).format(Date(it)))
            } ?: stringResource(R.string.rates_never_updated)
            val usdRate = rates["USD"] ?: SEED_RATES.getValue("USD")

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.rates_title), style = androidx.compose.material3.MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    Text(stringResource(R.string.rates_dashboard_subtitle), style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)),
            ) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(48.dp).background(Color(0xFF059669).copy(alpha = 0.16f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Text("$", color = Color(0xFF059669), fontWeight = FontWeight.Black) }
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(stringResource(R.string.rates_usd_hero), style = androidx.compose.material3.MaterialTheme.typography.labelMedium, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${numberFormat.format(usdRate)} ₴", style = androidx.compose.material3.MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                        Text(updatedText, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Text(stringResource(R.string.rates_source), style = androidx.compose.material3.MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("nbu" to R.string.rates_nbu, "privat" to R.string.rates_privat).forEach { (value, labelRes) ->
                    FilterChip(
                        selected = source == value,
                        onClick = {
                            if (source != value && !busy) scope.launch {
                                settingsStore.setRatesSource(value)
                                refresh(value)
                            }
                        },
                        label = { Text(stringResource(labelRes)) },
                        enabled = !busy,
                    )
                }
            }

            if (source == "privat") {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Filled.Info, contentDescription = null, tint = androidx.compose.material3.MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.rates_privat_note), style = androidx.compose.material3.MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    }
                }
            }

            Button(onClick = { refresh() }, enabled = !busy, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Text(stringResource(if (busy) R.string.rates_updating else R.string.rates_refresh), modifier = Modifier.padding(start = 8.dp))
            }
            messageRes?.let {
                val isError = it == R.string.rates_update_failed
                Text(
                    stringResource(it),
                    color = if (isError) androidx.compose.material3.MaterialTheme.colorScheme.error else Color(0xFF059669),
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Text(
                stringResource(R.string.rates_current),
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            SEED_RATES.forEach { (code, fallback) ->
                val tint = currencyTint(code)
                val value = rates[code] ?: fallback
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = tint.copy(alpha = 0.09f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, tint.copy(alpha = 0.24f)),
                ) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).background(tint.copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) {
                            Text(currencyMark(code), color = tint, fontWeight = FontWeight.Black)
                        }
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Text(code, fontWeight = FontWeight.Bold)
                            Text(
                                runCatching { java.util.Currency.getInstance(code).getDisplayName(locale) }.getOrDefault(code),
                                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(numberFormat.format(value), style = androidx.compose.material3.MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = tint)
                            Text(stringResource(R.string.rates_per_unit, code), style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

        }
    }
}

private fun currencyTint(code: String): Color = when (code) {
    "USD" -> Color(0xFF059669)
    "EUR" -> Color(0xFF2563EB)
    "GBP" -> Color(0xFF7C3AED)
    "PLN" -> Color(0xFFDC2626)
    else -> Color(0xFF6B7280)
}

private fun currencyMark(code: String): String = when (code) {
    "USD" -> "$"
    "EUR" -> "€"
    "GBP" -> "£"
    "PLN" -> "zł"
    else -> code
}
