package ua.rytm.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import ua.rytm.app.MainActivity
import ua.rytm.app.R
import ua.rytm.app.RytmApplication
import ua.rytm.app.ui.screens.finance.formatMoney
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Home-screen widget: balance, next shift, month earnings forecast.
 * Reads the local Room cache (the active profile), so it's instant and works
 * offline. The widget lives outside the PIN gate, so amounts are masked when
 * a PIN or "hide amounts" is on.
 */
class RytmWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = withContext(Dispatchers.IO) { loadSnapshot(context) }
        provideContent { GlanceTheme { WidgetContent(snapshot) } }
    }

    companion object {
        suspend fun loadSnapshot(context: Context): WidgetSnapshot {
            val app = context.applicationContext as RytmApplication
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return WidgetSnapshot.SignedOut
            val masked = app.settingsStore.hideAmounts.first() || app.pinStore.hasPin(uid).first()
            return WidgetSnapshot.load(app.financeRepository, app.shiftsRepository, signedIn = true, masked = masked)
        }

        /** Refreshes placed widgets; a no-op (no work) when none are on the home screen. */
        suspend fun refresh(context: Context) {
            if (GlanceAppWidgetManager(context).getGlanceIds(RytmWidget::class.java).isEmpty()) return
            RytmWidget().updateAll(context)
        }
    }
}

class RytmWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = RytmWidget()
}

private const val MASK = "••••••"

@Composable
private fun WidgetContent(s: WidgetSnapshot) {
    val context = LocalContext.current
    val muted = GlanceTheme.colors.onSurfaceVariant
    val main = GlanceTheme.colors.onSurface
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(22.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        if (!s.signedIn) {
            Text(context.getString(R.string.widget_signed_out), style = TextStyle(color = main, fontSize = 15.sp, fontWeight = FontWeight.Medium))
            return@Column
        }
        Text(context.getString(R.string.widget_balance), style = TextStyle(color = muted, fontSize = 12.sp))
        Text(
            if (s.masked) MASK else context.getString(R.string.money_uah, formatMoney(s.balanceUah)),
            style = TextStyle(color = main, fontSize = 24.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
        Spacer(GlanceModifier.height(8.dp))
        Text(context.getString(R.string.widget_next_shift), style = TextStyle(color = muted, fontSize = 12.sp))
        val shift = s.nextShift
        if (shift == null) {
            Text(context.getString(R.string.widget_no_shift), style = TextStyle(color = main, fontSize = 14.sp), maxLines = 1)
        } else {
            Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                Box(GlanceModifier.size(8.dp).cornerRadius(4.dp).background(ColorProvider(Color(shift.colorHex)))) {}
                Spacer(GlanceModifier.width(6.dp))
                Text(
                    "${dayLabel(context, shift.date)} · ${shift.name}",
                    style = TextStyle(color = main, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                    maxLines = 1,
                )
            }
        }
        if (s.monthForecastUah > 0) {
            Spacer(GlanceModifier.height(6.dp))
            Text(
                context.getString(R.string.widget_forecast) + ": " +
                    if (s.masked) MASK else "≈ " + context.getString(R.string.money_uah, formatMoney(s.monthForecastUah)),
                style = TextStyle(color = muted, fontSize = 12.sp),
                maxLines = 1,
            )
        }
    }
}

private fun dayLabel(context: Context, date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> context.getString(R.string.widget_today)
        today.plusDays(1) -> context.getString(R.string.widget_tomorrow)
        else -> date.format(DateTimeFormatter.ofPattern("dd.MM"))
    }
}
