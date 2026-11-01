package ua.rytm.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// One colour for every currency: it would carry no meaning, and red or green
// would read as loss or gain. A code without a symbol shrinks to fit the circle;
// the text follows the badge's dp size, not the system font scale.
@Composable
fun CurrencyBadge(code: String, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val tint = MaterialTheme.colorScheme.primary
    val mark = currencyMark(code)
    val ratio = when (mark.length) {
        1 -> 0.42f
        2 -> 0.34f
        else -> 0.27f
    }
    Box(modifier.size(size).background(tint.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
        Text(
            mark,
            color = tint,
            fontSize = with(LocalDensity.current) { (size * ratio).toSp() },
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
        )
    }
}

private fun currencyMark(code: String): String = when (code) {
    "UAH" -> "₴"
    "USD" -> "$"
    "EUR" -> "€"
    "GBP" -> "£"
    "PLN" -> "zł"
    "AUD" -> "A$"
    "AZN" -> "₼"
    "BDT" -> "৳"
    "CAD" -> "C$"
    "CHF" -> "Fr"
    "CNY", "JPY" -> "¥"
    "CZK" -> "Kč"
    "DKK", "NOK", "SEK" -> "kr"
    "EGP" -> "E£"
    "GEL" -> "₾"
    "HKD" -> "HK$"
    "HUF" -> "Ft"
    "IDR" -> "Rp"
    "ILS" -> "₪"
    "INR" -> "₹"
    "KRW" -> "₩"
    "KZT" -> "₸"
    "MXN" -> "MX$"
    "MYR" -> "RM"
    "NZD" -> "NZ$"
    "RON" -> "lei"
    "SGD" -> "S$"
    "THB" -> "฿"
    "TRY" -> "₺"
    "VND" -> "₫"
    "ZAR" -> "R"
    else -> code
}
