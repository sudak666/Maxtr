package ua.rytm.app.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
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
import androidx.glance.text.TextAlign
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
import java.time.format.TextStyle as JavaTextStyle

/**
 * Home-screen widget: balance, next shift, month earnings forecast vs goal,
 * and a "+" that opens the new-transaction sheet. Reads the local Room cache
 * (active profile), so it's instant and offline. It sits outside the PIN
 * gate, so amounts are masked when a PIN or "hide amounts" is on.
 */
class RytmWidget : GlanceAppWidget() {
    // Exact: the layout adds rows (week strip, recent operations) as height allows.
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = withContext(Dispatchers.IO) { loadSnapshot(context) }
        provideContent { WidgetContent(snapshot) }
    }

    companion object {
        suspend fun loadSnapshot(context: Context): WidgetSnapshot {
            val app = context.applicationContext as RytmApplication
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return WidgetSnapshot.SignedOut
            val masked = app.settingsStore.hideAmounts.first() || app.pinStore.hasPin(uid).first()
            val goal = app.settingsStore.salaryGoal(uid).first()
            return WidgetSnapshot.load(app.financeRepository, app.shiftsRepository, signedIn = true, masked = masked, salaryGoal = goal)
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

// Brand palette (matches the app's dark theme / light theme surfaces).
private val Bg = ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFF242327))
// Same neutral surfaces as the app theme (LightBg/DarkBg2), not a lavender tint.
private val Tile = ColorProvider(day = Color(0xFFF4F3F1), night = Color(0xFF2C2B30))
private val OnBg = ColorProvider(day = Color(0xFF1C1C1E), night = Color(0xFFE9E8EA))
private val Muted = ColorProvider(day = Color(0xFF626269), night = Color(0xFF98979E))
private val Accent = ColorProvider(day = Color(0xFF7C3AED), night = Color(0xFF8B5CF6))
private val Track = ColorProvider(day = Color(0xFFE2E0DD), night = Color(0xFF38373D))
private val Good = ColorProvider(day = Color(0xFF059669), night = Color(0xFF10B981))
private val White = ColorProvider(Color.White)

private const val MASK = "••••••"
private val TILE_HEIGHT = 92.dp
// Vertical budget (dp) used to decide which extra sections fit.
private val BASE_HEIGHT = 220.dp
private val WEEK_HEIGHT = 88.dp
private val RECENT_HEADER = 30.dp
private val RECENT_ROW = 40.dp
// Recent list Column holds header spacer+label + 2 children per row ≤ Glance's 10.
private const val MAX_RECENT_ROWS = 4

@Composable
private fun WidgetContent(s: WidgetSnapshot) {
    val context = LocalContext.current
    val open = actionStartActivity<MainActivity>()
    val newTx = androidx.glance.appwidget.action.actionStartActivity(
        Intent(context, MainActivity::class.java)
            .setData(Uri.parse("rytm://widget/new-transaction")) // distinct PendingIntent
            .putExtra(MainActivity.EXTRA_LAUNCH_ACTION, MainActivity.ACTION_NEW_TRANSACTION),
    )
    val height = LocalSize.current.height
    val compact = height < 160.dp
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(Bg)
            .cornerRadius(24.dp)
            .padding(14.dp)
            .clickable(open),
    ) {
        Header(if (s.signedIn) newTx else null)
        if (!s.signedIn) {
            Spacer(GlanceModifier.defaultWeight())
            Text(context.getString(R.string.widget_signed_out), style = TextStyle(color = OnBg, fontSize = 15.sp, fontWeight = FontWeight.Medium))
            Spacer(GlanceModifier.defaultWeight())
            return@Column
        }
        Spacer(GlanceModifier.height(if (compact) 4.dp else 8.dp))
        Text(context.getString(R.string.widget_balance), style = TextStyle(color = Muted, fontSize = 12.sp))
        Text(
            if (s.masked) MASK else context.getString(R.string.money_uah, formatMoney(s.balanceUah)),
            style = TextStyle(color = OnBg, fontSize = if (compact) 22.sp else 28.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
        if (!compact) {
            // Content stacks from the top; extra height buys more rows instead
            // of a blank gap (v2 pushed the tiles to the bottom of a tall widget).
            Spacer(GlanceModifier.height(12.dp))
            Row(GlanceModifier.fillMaxWidth()) {
                ShiftTile(s.nextShift, GlanceModifier.defaultWeight())
                Spacer(GlanceModifier.width(8.dp))
                ForecastTile(s, GlanceModifier.defaultWeight())
            }
            var used = BASE_HEIGHT
            if (height >= used + WEEK_HEIGHT && s.week.isNotEmpty()) {
                WeekStrip(s.week)
                used += WEEK_HEIGHT
            }
            val rows = ((height - used - RECENT_HEADER) / RECENT_ROW).toInt().coerceIn(0, minOf(s.recent.size, MAX_RECENT_ROWS))
            if (rows > 0) RecentList(s.recent.take(rows), s.masked)
        }
    }
}

@Composable
private fun Header(newTx: Action?) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Image(
            ImageProvider(R.drawable.ic_widget_logo),
            contentDescription = null,
            modifier = GlanceModifier.size(26.dp).cornerRadius(7.dp),
        )
        Spacer(GlanceModifier.width(8.dp))
        Text("Rytm", style = TextStyle(color = OnBg, fontSize = 14.sp, fontWeight = FontWeight.Bold))
        Spacer(GlanceModifier.defaultWeight())
        if (newTx != null) {
            Box(
                modifier = GlanceModifier.size(36.dp).cornerRadius(18.dp).background(Accent).clickable(newTx),
                contentAlignment = Alignment.Center,
            ) {
                Text("+", style = TextStyle(color = White, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center))
            }
        }
    }
}

@Composable
private fun ShiftTile(shift: WidgetSnapshot.NextShift?, modifier: GlanceModifier) {
    val context = LocalContext.current
    Column(modifier.height(TILE_HEIGHT).background(Tile).cornerRadius(16.dp).padding(10.dp)) {
        Text(context.getString(R.string.widget_next_shift), style = TextStyle(color = Muted, fontSize = 11.sp), maxLines = 1)
        Spacer(GlanceModifier.height(6.dp))
        if (shift == null) {
            Text(context.getString(R.string.widget_no_shift), style = TextStyle(color = OnBg, fontSize = 13.sp), maxLines = 2)
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                GlanceModifier.size(30.dp).cornerRadius(9.dp).background(ColorProvider(Color(shift.colorHex))),
                contentAlignment = Alignment.Center,
            ) {
                Text(shift.code, style = TextStyle(color = White, fontSize = 13.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            }
            Spacer(GlanceModifier.width(8.dp))
            Column {
                Text(dayLabel(context, shift.date), style = TextStyle(color = OnBg, fontSize = 13.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                Text(shift.name, style = TextStyle(color = Muted, fontSize = 11.sp), maxLines = 1)
            }
        }
    }
}

@Composable
private fun ForecastTile(s: WidgetSnapshot, modifier: GlanceModifier) {
    val context = LocalContext.current
    val locale = context.resources.configuration.locales[0]
    val month = LocalDate.now().month.getDisplayName(JavaTextStyle.FULL_STANDALONE, locale).replaceFirstChar { it.titlecase(locale) }
    Column(modifier.height(TILE_HEIGHT).background(Tile).cornerRadius(16.dp).padding(10.dp)) {
        Text(month, style = TextStyle(color = Muted, fontSize = 11.sp), maxLines = 1)
        Spacer(GlanceModifier.height(6.dp))
        Text(
            if (s.masked) MASK else "≈ " + formatMoney(s.monthForecastUah),
            style = TextStyle(color = OnBg, fontSize = 15.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
        if (s.salaryGoal > 0) {
            val pct = (s.monthForecastUah / s.salaryGoal).coerceAtLeast(0.0)
            Spacer(GlanceModifier.height(6.dp))
            LinearProgressIndicator(
                progress = pct.coerceAtMost(1.0).toFloat(),
                modifier = GlanceModifier.fillMaxWidth().height(4.dp),
                color = if (pct >= 1.0) Good else Accent,
                backgroundColor = Track,
            )
            if (!s.masked) {
                Spacer(GlanceModifier.height(4.dp))
                Text(context.getString(R.string.widget_goal_pct, (pct * 100).toInt()), style = TextStyle(color = Muted, fontSize = 11.sp), maxLines = 1)
            }
        }
    }
}

@Composable
private fun WeekStrip(week: List<WidgetSnapshot.WeekDay>) {
    val context = LocalContext.current
    val locale = context.resources.configuration.locales[0]
    // Own Column: a Glance Column renders at most 10 children and silently
    // drops the rest — inlined into the root these sections vanished.
    Column(GlanceModifier.fillMaxWidth()) {
    Spacer(GlanceModifier.height(14.dp))
    Text(context.getString(R.string.widget_week), style = TextStyle(color = Muted, fontSize = 12.sp))
    Spacer(GlanceModifier.height(6.dp))
    Row(GlanceModifier.fillMaxWidth()) {
        week.forEachIndexed { i, d ->
            Column(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    d.date.dayOfWeek.getDisplayName(JavaTextStyle.SHORT_STANDALONE, locale).take(2).replaceFirstChar { it.titlecase(locale) },
                    style = TextStyle(color = if (i == 0) Accent else Muted, fontSize = 11.sp, fontWeight = if (i == 0) FontWeight.Bold else FontWeight.Normal),
                )
                Spacer(GlanceModifier.height(4.dp))
                Box(
                    GlanceModifier.size(30.dp).cornerRadius(9.dp).background(if (d.code != null) ColorProvider(Color(d.colorHex)) else Tile),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        d.code ?: d.date.dayOfMonth.toString(),
                        style = TextStyle(color = if (d.code != null) White else Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold),
                        maxLines = 1,
                    )
                }
            }
        }
    }
    }
}

@Composable
private fun RecentList(items: List<WidgetSnapshot.RecentTx>, masked: Boolean) {
    val context = LocalContext.current
    Column(GlanceModifier.fillMaxWidth()) {
    Spacer(GlanceModifier.height(14.dp))
    Text(context.getString(R.string.widget_recent), style = TextStyle(color = Muted, fontSize = 12.sp))
    items.forEach { t ->
        Spacer(GlanceModifier.height(6.dp))
        Row(GlanceModifier.fillMaxWidth().height(34.dp).background(Tile).cornerRadius(12.dp).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.defaultWeight()) {
                Text(t.title, style = TextStyle(color = OnBg, fontSize = 12.sp, fontWeight = FontWeight.Medium), maxLines = 1)
            }
            Spacer(GlanceModifier.width(8.dp))
            Text(
                if (masked) MASK else t.signedAmount,
                style = TextStyle(
                    color = if (!masked && t.signedAmount.startsWith("+")) Good else OnBg,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
        }
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
