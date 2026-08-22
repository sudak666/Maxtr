package ua.rytm.app.ui.screens.finance

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.TipsAndUpdates
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.platform.LocalConfiguration
import ua.rytm.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import ua.rytm.app.RytmApplication
import ua.rytm.app.ui.maskedAmount
import ua.rytm.app.data.FinanceRepository
import java.net.HttpURLConnection
import java.net.URL
import java.text.NumberFormat
import java.util.Locale
import java.util.Currency

data class CryptoQuote(val symbol: String, val price: Double, val change: Double, val spark: List<Double>)

@Composable
fun FinanceDashboardWidget(key: String, app: RytmApplication) {
    when (key) {
        "goals" -> GoalsDashboardWidget(app)
        "dailyTip" -> WidgetCard(stringResource(R.string.widget_tip), Icons.Filled.TipsAndUpdates, Color(0xFF3B82F6)) {
            val tips = stringArrayResource(R.array.finance_tips)
            val day = System.currentTimeMillis() / 86_400_000L
            Text(tips[(day % tips.size).toInt()], color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        "cryptoTop" -> CryptoDashboardWidget(app)
    }
}

@Composable
private fun GoalsDashboardWidget(app: RytmApplication) {
    val goals by app.financeRepository.goals.collectAsState(initial = emptyList())
    val wallets by app.financeRepository.wallets.collectAsState(initial = emptyList())
    val transactions by app.financeRepository.transactions.collectAsState(initial = emptyList())
    if (goals.isEmpty()) return
    WidgetCard(stringResource(R.string.widget_goals), Icons.Filled.Flag, Color(0xFF10B981)) {
        goals.forEach { goal ->
            val wallet = wallets.firstOrNull { it.id == goal.walletId }
            val current = FinanceRepository.walletBalance(transactions, goal.walletId)
            val progress = if (goal.targetAmount > 0) (current / goal.targetAmount).coerceIn(0.0, 1.0) else 0.0
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(wallet?.name ?: stringResource(R.string.goals_default_name), fontWeight = FontWeight.SemiBold)
                    Text(maskedAmount("${formatMoney(current)} / ${formatMoney(goal.targetAmount)} ${currencySymbol(wallet?.currency ?: "UAH")}"), style = MaterialTheme.typography.bodySmall)
                }
                Box(Modifier.fillMaxWidth().height(7.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)) {
                    Box(Modifier.fillMaxWidth(progress.toFloat()).height(7.dp).background(Color(0xFF10B981), CircleShape))
                }
            }
        }
    }
}

@Composable
private fun CryptoDashboardWidget(app: RytmApplication) {
    var quotes by remember { mutableStateOf<List<CryptoQuote>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val cache = app.settingsStore.cryptoCache.first()
        if (cache != null) quotes = parseQuotes(cache.json)
        if (cache == null || System.currentTimeMillis() - cache.at >= 30 * 60 * 1000L) {
            try {
                val json = fetchCryptoJson()
                val parsed = parseQuotes(json)
                if (parsed.isEmpty()) error = true else {
                    quotes = parsed
                    app.settingsStore.setCryptoCache(json, System.currentTimeMillis())
                }
            } catch (_: Exception) { error = quotes.isEmpty() }
        }
        loading = false
    }
    WidgetCard(stringResource(R.string.widget_crypto), Icons.Filled.LocalFireDepartment, Color(0xFFF7931A)) {
        if (loading && quotes.isEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Text(stringResource(R.string.crypto_loading), Modifier.padding(start = 10.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (error) Text(stringResource(R.string.crypto_error), color = MaterialTheme.colorScheme.error)
        quotes.forEach { quote -> CryptoRow(quote) }
    }
}

@Composable
private fun CryptoRow(quote: CryptoQuote) {
    val positive = quote.change >= 0
    val trend = if (positive) Color(0xFF10B981) else Color(0xFFEF4444)
    val locale = Locale.forLanguageTag(LocalConfiguration.current.locales[0].toLanguageTag())
    val changeText = NumberFormat.getNumberInstance(locale).apply { minimumFractionDigits = 1; maximumFractionDigits = 1 }.format(quote.change)
    val priceText = NumberFormat.getCurrencyInstance(locale).apply { currency = Currency.getInstance("USD"); maximumFractionDigits = if (quote.price < 10) 4 else 0 }.format(quote.price)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(34.dp).background(if (quote.symbol == "BTC") Color(0x29F7931A) else Color(0x29627EEA), CircleShape), contentAlignment = Alignment.Center) {
            Text(quote.symbol.first().toString(), color = if (quote.symbol == "BTC") Color(0xFFF7931A) else Color(0xFF627EEA), fontWeight = FontWeight.Bold)
        }
        Column(Modifier.padding(start = 10.dp).weight(1f)) {
            Text(quote.symbol, fontWeight = FontWeight.SemiBold)
            Text("${if (positive) "+" else ""}$changeText%", color = trend, style = MaterialTheme.typography.bodySmall)
        }
        Sparkline(quote.spark, trend, Modifier.size(60.dp, 24.dp))
        Text(priceText, Modifier.padding(start = 10.dp), fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Sparkline(values: List<Double>, color: Color, modifier: Modifier) {
    if (values.size < 2) return
    Canvas(modifier) {
        val min = values.min()
        val span = (values.max() - min).takeIf { it > 0 } ?: 1.0
        val path = Path()
        values.forEachIndexed { i, value ->
            val x = size.width * i / (values.size - 1)
            val y = size.height * (1f - ((value - min) / span).toFloat())
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx()))
    }
}

@Composable
private fun WidgetCard(title: String, icon: ImageVector, color: Color, content: @Composable () -> Unit) {
    Card(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
                Text(title, Modifier.padding(start = 8.dp), fontWeight = FontWeight.Bold)
            }
            content()
        }
    }
}

private suspend fun fetchCryptoJson(): String = withContext(Dispatchers.IO) {
    val connection = URL("https://api.coingecko.com/api/v3/coins/markets?vs_currency=usd&ids=bitcoin,ethereum&sparkline=true&price_change_percentage=24h").openConnection() as HttpURLConnection
    connection.connectTimeout = 10_000
    connection.readTimeout = 10_000
    connection.setRequestProperty("Accept", "application/json")
    try {
        if (connection.responseCode !in 200..299) error("CoinGecko HTTP ${connection.responseCode}")
        connection.inputStream.bufferedReader().use { it.readText() }
    } finally { connection.disconnect() }
}

private fun parseQuotes(json: String): List<CryptoQuote> = runCatching {
    val array = JSONArray(json)
    (0 until array.length()).mapNotNull { i ->
        val item = array.getJSONObject(i)
        val symbol = when (item.optString("id")) { "bitcoin" -> "BTC"; "ethereum" -> "ETH"; else -> return@mapNotNull null }
        val sparkJson = item.optJSONObject("sparkline_in_7d")?.optJSONArray("price")
        val spark = if (sparkJson == null) emptyList() else (0 until sparkJson.length()).map { sparkJson.optDouble(it) }
        CryptoQuote(symbol, item.getDouble("current_price"), item.optDouble("price_change_percentage_24h"), spark)
    }
}.getOrDefault(emptyList())
