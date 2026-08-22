package ua.rytm.app.ui.screens.finance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ua.rytm.app.data.CurrencyRatesSyncRepository
import ua.rytm.app.data.DEFAULT_PROFILE_ID
import ua.rytm.app.data.FinanceRepository
import ua.rytm.app.data.SEED_RATES
import ua.rytm.app.data.local.SettingsStore
import java.text.DateFormat
import java.util.Date

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
    val drafts = remember { mutableStateMapOf<String, String>() }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(rates) {
        SEED_RATES.forEach { (code, fallback) -> drafts[code] = (rates[code] ?: fallback).toString() }
    }

    fun refresh(selectedSource: String = source) {
        if (busy) return
        scope.launch {
            busy = true
            message = null
            runCatching { syncRepository.refreshOnline(uid, profileId, selectedSource == "privat") }
                .onSuccess { settingsStore.setRatesUpdatedAt(it); message = "Курси оновлено" }
                .onFailure { message = "Не вдалося оновити курси" }
            busy = false
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Курси валют", fontWeight = FontWeight.Bold)
            Text("Скільки гривень за 1 одиницю валюти. База — гривня (курс завжди 1).")
            Text("Джерело курсу")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("nbu" to "НБУ (офіційний)", "privat" to "ПриватБанк (готівка)").forEach { (value, label) ->
                    FilterChip(
                        selected = source == value,
                        onClick = {
                            if (source != value && !busy) scope.launch {
                                settingsStore.setRatesSource(value)
                                refresh(value)
                            }
                        },
                        label = { Text(label) },
                        enabled = !busy,
                    )
                }
            }
            Text("ПриватБанк дає готівковий курс лише для USD/EUR; інші валюти — курс НБУ.")
            Button(onClick = { refresh() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Text(if (busy) "Оновлення…" else "Оновити онлайн")
            }
            Text(updatedAt?.let { "Оновлено ${DateFormat.getDateTimeInstance().format(Date(it))}" } ?: "Ще не оновлювалося")
            message?.let { Text(it) }

            SEED_RATES.forEach { (code, fallback) ->
                OutlinedTextField(
                    value = drafts[code] ?: (rates[code] ?: fallback).toString(),
                    onValueChange = { drafts[code] = it },
                    label = { Text(code) },
                    supportingText = { Text("грн за 1 $code") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    enabled = !busy,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().onFocusChanged { state ->
                        if (!state.isFocused) {
                            val value = drafts[code]?.replace(',', '.')?.toDoubleOrNull()
                            if (value != null && value > 0.0 && value != rates[code]) {
                                scope.launch {
                                    runCatching { syncRepository.saveRate(uid, profileId, code, value) }
                                        .onFailure { drafts[code] = (rates[code] ?: fallback).toString(); message = "Не вдалося зберегти курс" }
                                }
                            } else if (value == null || value <= 0.0) drafts[code] = (rates[code] ?: fallback).toString()
                        }
                    },
                )
            }
        }
    }
}
