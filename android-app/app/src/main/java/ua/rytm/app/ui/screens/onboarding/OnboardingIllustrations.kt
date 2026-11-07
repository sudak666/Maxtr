package ua.rytm.app.ui.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ua.rytm.app.R
import ua.rytm.app.ui.icons.AccountBalanceWallet
import ua.rytm.app.ui.icons.CheckCircle
import ua.rytm.app.ui.icons.LocalCafe
import ua.rytm.app.ui.icons.Lock
import ua.rytm.app.ui.icons.Mobile
import ua.rytm.app.ui.icons.Laptop
import ua.rytm.app.ui.icons.RytmIcons
import ua.rytm.app.ui.icons.Notifications
import ua.rytm.app.ui.icons.ShoppingCart
import ua.rytm.app.ui.localizedDomainText
import ua.rytm.app.ui.screens.finance.formatMoney
import ua.rytm.app.ui.theme.RytmSemantic
import ua.rytm.app.ui.theme.RytmSpacing

// Small drawn previews of the real screens, instead of one icon in a circle
// (owner, 2026-11-03: the pages said what the app has, not what it does).
// Example figures only; decorative, so TalkBack reads the page text instead.

@Composable
private fun PreviewCard(content: @Composable () -> Unit) {
    Column(
        Modifier
            .widthIn(max = 320.dp)
            .fillMaxWidth()
            .clearAndSetSemantics {}
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.shapes.large)
            .padding(RytmSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(RytmSpacing.sm),
    ) { content() }
}

@Composable
private fun PreviewRow(icon: ImageVector, tint: Color, title: String, amount: String, amountColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(32.dp).background(tint.copy(alpha = 0.18f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(RytmSpacing.sm))
        Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(amount, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = amountColor)
    }
}

@Composable
internal fun FinancePreview() = PreviewCard {
    Text(stringResource(R.string.finance_total_balance), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text("${formatMoney(12450.0)} ₴", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(RytmIcons.AccountBalanceWallet, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.onboarding_demo_forecast), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    PreviewRow(RytmIcons.ShoppingCart, Color(0xFF22C55E), localizedDomainText("Продукти"), "−${formatMoney(320.0)} ₴", RytmSemantic.expense)
    PreviewRow(RytmIcons.LocalCafe, Color(0xFFF59E0B), localizedDomainText("Кафе"), "−${formatMoney(65.0)} ₴", RytmSemantic.expense)
}

@Composable
internal fun BankPreview() = PreviewCard {
    // A bank notification turning into a saved operation.
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(RytmIcons.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text("monobank", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Text(stringResource(R.string.onboarding_demo_bank_push, "${formatMoney(245.0)} ₴"), style = MaterialTheme.typography.bodyMedium)
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    PreviewRow(RytmIcons.ShoppingCart, Color(0xFF22C55E), localizedDomainText("Продукти"), "−${formatMoney(245.0)} ₴", RytmSemantic.expense)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(RytmIcons.CheckCircle, contentDescription = null, tint = RytmSemantic.income, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.onboarding_demo_bank_saved), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun ShiftsPreview() = PreviewCard {
    val day = Color(0xFF3B82F6)
    val night = Color(0xFF8B5CF6)
    val off = Color(0xFFF59E0B)
    val week = listOf(
        R.string.onboarding_demo_day to day, R.string.onboarding_demo_day to day, R.string.onboarding_demo_night to night,
        R.string.onboarding_demo_night to night, R.string.onboarding_demo_off to off, R.string.onboarding_demo_off to off,
        R.string.onboarding_demo_day to day,
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        week.forEachIndexed { i, (code, color) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${i + 10}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.size(6.dp))
                Box(Modifier.size(32.dp).background(color.copy(alpha = 0.22f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                    Text(stringResource(code), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = color)
                }
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    Text(stringResource(R.string.onboarding_demo_earned, "${formatMoney(18000.0)} ₴"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = RytmSemantic.income)
}

@Composable
internal fun PrivacyPreview() = PreviewCard {
    PreviewRow(RytmIcons.Mobile, MaterialTheme.colorScheme.primary, stringResource(R.string.onboarding_demo_phone), "", Color.Unspecified)
    PreviewRow(RytmIcons.Laptop, MaterialTheme.colorScheme.primary, stringResource(R.string.onboarding_demo_laptop), "", Color.Unspecified)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(RytmIcons.CheckCircle, contentDescription = null, tint = RytmSemantic.income, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.onboarding_demo_synced), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(RytmIcons.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(RytmSpacing.sm))
        repeat(4) {
            Box(Modifier.padding(horizontal = RytmSpacing.xs).size(10.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
        }
    }
}
