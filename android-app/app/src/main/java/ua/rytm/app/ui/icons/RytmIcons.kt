package ua.rytm.app.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * GENERATED FILE — do not hand-edit. Regenerate with the script recorded in
 * ANDROID_MIGRATION.md (design-audit stage 5).
 *
 * Rytm's own icon set: stroke glyphs traced from the vendored Lucide library
 * (`docs/lucide/`, ISC), which is already the app's documented source for new
 * glyphs and matches the PWA's hand-drawn stroke language 1:1 in format
 * (24x24 viewBox, `stroke="currentColor"`, no fill).
 *
 * This replaces 219 `Icons.Filled.*` call sites. Material Filled is a
 * different visual language — solid fills, heavier weight, different geometry
 * — so a user moving between the web app and this one was seeing two
 * different products. Dropping it also removes the `material-icons-extended`
 * dependency (~2000 vectors of which ~10% were used).
 *
 * These are plain top-level lazy vals, deliberately NOT `@Composable`: the
 * category lookup tables in CategoryColor.kt are non-composable on purpose
 * (see design-audit stage 4) and must be able to reference them.
 */
object RytmIcons

private const val StrokeWidth = 2f

private fun icon(name: String, vararg pathData: String): ImageVector {
    val builder = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )
    pathData.forEach { data ->
        builder.addPath(
            pathData = PathParser().parsePathString(data).toNodes(),
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = StrokeWidth,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }
    return builder.build()
}

private val _accountBalance by lazy { icon("AccountBalance",
        "M10 18v-7",
        "M11.119 2.205a2 2 0 0 1 1.762 0l7.84 3.846A.5.5 0 0 1 20.5 7h-17a.5.5 0 0 1-.22-.949z",
        "M14 18v-7",
        "M18 18v-7",
        "M3 22h18",
        "M6 18v-7",
) }

/** Lucide `landmark`. */
val RytmIcons.AccountBalance: ImageVector get() = _accountBalance

private val _accountBalanceWallet by lazy { icon("AccountBalanceWallet",
        "M19 7V4a1 1 0 0 0-1-1H5a2 2 0 0 0 0 4h15a1 1 0 0 1 1 1v4h-3a2 2 0 0 0 0 4h3a1 1 0 0 0 1-1v-2a1 1 0 0 0-1-1",
        "M3 5v14a2 2 0 0 0 2 2h15a1 1 0 0 0 1-1v-4",
) }

/** Lucide `wallet`. */
val RytmIcons.AccountBalanceWallet: ImageVector get() = _accountBalanceWallet

private val _add by lazy { icon("Add",
        "M5 12h14",
        "M12 5v14",
) }

/** Lucide `plus`. */
val RytmIcons.Add: ImageVector get() = _add

private val _arrowBack by lazy { icon("ArrowBack",
        "m12 19-7-7 7-7",
        "M19 12H5",
) }

/** Lucide `arrow-left`. */
val RytmIcons.ArrowBack: ImageVector get() = _arrowBack

private val _arrowDownward by lazy { icon("ArrowDownward",
        "M12 5v14",
        "m19 12-7 7-7-7",
) }

/** Lucide `arrow-down`. */
val RytmIcons.ArrowDownward: ImageVector get() = _arrowDownward

private val _arrowUpward by lazy { icon("ArrowUpward",
        "m5 12 7-7 7 7",
        "M12 19V5",
) }

/** Lucide `arrow-up`. */
val RytmIcons.ArrowUpward: ImageVector get() = _arrowUpward

private val _attachMoney by lazy { icon("AttachMoney",
        "M4,6 H20 A2,2 0 0 1 22,8 V16 A2,2 0 0 1 20,18 H4 A2,2 0 0 1 2,16 V8 A2,2 0 0 1 4,6 Z",
        "M10,12 a2,2 0 1 0 4,0 a2,2 0 1 0 -4,0",
        "M6 12h.01M18 12h.01",
) }

/** Lucide `banknote`. */
val RytmIcons.AttachMoney: ImageVector get() = _attachMoney

private val _autoAwesome by lazy { icon("AutoAwesome",
        "M11.017 2.814a1 1 0 0 1 1.966 0l1.051 5.558a2 2 0 0 0 1.594 1.594l5.558 1.051a1 1 0 0 1 0 1.966l-5.558 1.051a2 2 0 0 0-1.594 1.594l-1.051 5.558a1 1 0 0 1-1.966 0l-1.051-5.558a2 2 0 0 0-1.594-1.594l-5.558-1.051a1 1 0 0 1 0-1.966l5.558-1.051a2 2 0 0 0 1.594-1.594z",
        "M20 2v4",
        "M22 4h-4",
        "M2,20 a2,2 0 1 0 4,0 a2,2 0 1 0 -4,0",
) }

/** Lucide `sparkles`. */
val RytmIcons.AutoAwesome: ImageVector get() = _autoAwesome

private val _backspace by lazy { icon("Backspace",
        "M10 5a2 2 0 0 0-1.344.519l-6.328 5.74a1 1 0 0 0 0 1.481l6.328 5.741A2 2 0 0 0 10 19h10a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2z",
        "m12 9 6 6",
        "m18 9-6 6",
) }

/** Lucide `delete`. */
val RytmIcons.Backspace: ImageVector get() = _backspace

private val _badge by lazy { icon("Badge",
        "M16 10h2",
        "M16 14h2",
        "M6.17 15a3 3 0 0 1 5.66 0",
        "M7,11 a2,2 0 1 0 4,0 a2,2 0 1 0 -4,0",
        "M4,5 H20 A2,2 0 0 1 22,7 V17 A2,2 0 0 1 20,19 H4 A2,2 0 0 1 2,17 V7 A2,2 0 0 1 4,5 Z",
) }

/** Lucide `id-card`. */
val RytmIcons.Badge: ImageVector get() = _badge

private val _barChart by lazy { icon("BarChart",
        "M3 3v16a2 2 0 0 0 2 2h16",
        "M18 17V9",
        "M13 17V5",
        "M8 17v-3",
) }

/** Lucide `bar-chart-3`. */
val RytmIcons.BarChart: ImageVector get() = _barChart

private val _beachAccess by lazy { icon("BeachAccess",
        "M12 13v7a2 2 0 0 0 4 0",
        "M12 2v2",
        "M20.992 13a1 1 0 0 0 .97-1.274 10.284 10.284 0 0 0-19.923 0A1 1 0 0 0 3 13z",
) }

/** Lucide `umbrella`. */
val RytmIcons.BeachAccess: ImageVector get() = _beachAccess

private val _bento by lazy { icon("Bento",
        "M11 21.73a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73z",
        "M12 22V12",
        "M3.29,7 L12,12 L20.71,7",
        "m7.5 4.27 9 5.15",
) }

/** Lucide `package`. */
val RytmIcons.Bento: ImageVector get() = _bento

private val _bolt by lazy { icon("Bolt",
        "M4 14a1 1 0 0 1-.78-1.63l9.9-10.2a.5.5 0 0 1 .86.46l-1.92 6.02A1 1 0 0 0 13 10h7a1 1 0 0 1 .78 1.63l-9.9 10.2a.5.5 0 0 1-.86-.46l1.92-6.02A1 1 0 0 0 11 14z",
) }

/** Lucide `zap`. */
val RytmIcons.Bolt: ImageVector get() = _bolt

private val _brightnessAuto by lazy { icon("BrightnessAuto",
        "M12 2v2",
        "M14.837 16.385a6 6 0 1 1-7.223-7.222c.624-.147.97.66.715 1.248a4 4 0 0 0 5.26 5.259c.589-.255 1.396.09 1.248.715",
        "M16 12a4 4 0 0 0-4-4",
        "m19 5-1.256 1.256",
        "M20 12h2",
) }

/** Lucide `sun-moon`. */
val RytmIcons.BrightnessAuto: ImageVector get() = _brightnessAuto

private val _build by lazy { icon("Build",
        "M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.106-3.105c.32-.322.863-.22.983.218a6 6 0 0 1-8.259 7.057l-7.91 7.91a1 1 0 0 1-2.999-3l7.91-7.91a6 6 0 0 1 7.057-8.259c.438.12.54.662.219.984z",
) }

/** Lucide `wrench`. */
val RytmIcons.Build: ImageVector get() = _build

private val _calculate by lazy { icon("Calculate",
        "M6,2 H18 A2,2 0 0 1 20,4 V20 A2,2 0 0 1 18,22 H6 A2,2 0 0 1 4,20 V4 A2,2 0 0 1 6,2 Z",
        "M8,6 L16,6",
        "M16,14 L16,18",
        "M16 10h.01",
        "M12 10h.01",
        "M8 10h.01",
        "M12 14h.01",
        "M8 14h.01",
        "M12 18h.01",
        "M8 18h.01",
) }

/** Lucide `calculator`. */
val RytmIcons.Calculate: ImageVector get() = _calculate

private val _calendarMonth by lazy { icon("CalendarMonth",
        "M8 2v4",
        "M16 2v4",
        "M5,4 H19 A2,2 0 0 1 21,6 V20 A2,2 0 0 1 19,22 H5 A2,2 0 0 1 3,20 V6 A2,2 0 0 1 5,4 Z",
        "M3 10h18",
) }

/** Lucide `calendar`. */
val RytmIcons.CalendarMonth: ImageVector get() = _calendarMonth

private val _cameraAlt by lazy { icon("CameraAlt",
        "M13.997 4a2 2 0 0 1 1.76 1.05l.486.9A2 2 0 0 0 18.003 7H20a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2h1.997a2 2 0 0 0 1.759-1.048l.489-.904A2 2 0 0 1 10.004 4z",
        "M9,13 a3,3 0 1 0 6,0 a3,3 0 1 0 -6,0",
) }

/** Lucide `camera`. */
val RytmIcons.CameraAlt: ImageVector get() = _cameraAlt

private val _cardGiftcard by lazy { icon("CardGiftcard",
        "M12 7v14",
        "M20 11v8a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2v-8",
        "M7.5 7a1 1 0 0 1 0-5A4.8 8 0 0 1 12 7a4.8 8 0 0 1 4.5-5 1 1 0 0 1 0 5",
        "M4,7 H20 A1,1 0 0 1 21,8 V10 A1,1 0 0 1 20,11 H4 A1,1 0 0 1 3,10 V8 A1,1 0 0 1 4,7 Z",
) }

/** Lucide `gift`. */
val RytmIcons.CardGiftcard: ImageVector get() = _cardGiftcard

private val _category by lazy { icon("Category",
        "M8.3 10a.7.7 0 0 1-.626-1.079L11.4 3a.7.7 0 0 1 1.198-.043L16.3 8.9a.7.7 0 0 1-.572 1.1Z",
        "M4,14 H9 A1,1 0 0 1 10,15 V20 A1,1 0 0 1 9,21 H4 A1,1 0 0 1 3,20 V15 A1,1 0 0 1 4,14 Z",
        "M14,17.5 a3.5,3.5 0 1 0 7,0 a3.5,3.5 0 1 0 -7,0",
) }

/** Lucide `shapes`. */
val RytmIcons.Category: ImageVector get() = _category

private val _check by lazy { icon("Check",
        "M20 6 9 17l-5-5",
) }

/** Lucide `check`. */
val RytmIcons.Check: ImageVector get() = _check

private val _checkCircle by lazy { icon("CheckCircle",
        "M2,12 a10,10 0 1 0 20,0 a10,10 0 1 0 -20,0",
        "m9 12 2 2 4-4",
) }

/** Lucide `circle-check`. */
val RytmIcons.CheckCircle: ImageVector get() = _checkCircle

private val _checklist by lazy { icon("Checklist",
        "M13 5h8",
        "M13 12h8",
        "M13 19h8",
        "m3 17 2 2 4-4",
        "m3 7 2 2 4-4",
) }

/** Lucide `list-checks`. */
val RytmIcons.Checklist: ImageVector get() = _checklist

private val _chevronLeft by lazy { icon("ChevronLeft",
        "m15 18-6-6 6-6",
) }

/** Lucide `chevron-left`. */
val RytmIcons.ChevronLeft: ImageVector get() = _chevronLeft

private val _chevronRight by lazy { icon("ChevronRight",
        "m9 18 6-6-6-6",
) }

/** Lucide `chevron-right`. */
val RytmIcons.ChevronRight: ImageVector get() = _chevronRight

private val _clear by lazy { icon("Clear",
        "M18 6 6 18",
        "m6 6 12 12",
) }

/** Lucide `x`. */
val RytmIcons.Clear: ImageVector get() = _clear

private val _close by lazy { icon("Close",
        "M18 6 6 18",
        "m6 6 12 12",
) }

/** Lucide `x`. */
val RytmIcons.Close: ImageVector get() = _close

private val _cloudDone by lazy { icon("CloudDone",
        "m17 15-5.5 5.5L9 18",
        "M5.516 16.07A7 7 0 1 1 15.71 8h1.79a4.5 4.5 0 0 1 3.501 7.327",
) }

/** Lucide `cloud-check`. */
val RytmIcons.CloudDone: ImageVector get() = _cloudDone

private val _cloudOff by lazy { icon("CloudOff",
        "M10.94 5.274A7 7 0 0 1 15.71 10h1.79a4.5 4.5 0 0 1 4.222 6.057",
        "M18.796 18.81A4.5 4.5 0 0 1 17.5 19H9A7 7 0 0 1 5.79 5.78",
        "m2 2 20 20",
) }

/** Lucide `cloud-off`. */
val RytmIcons.CloudOff: ImageVector get() = _cloudOff

private val _compareArrows by lazy { icon("CompareArrows",
        "m16 3 4 4-4 4",
        "M20 7H4",
        "m8 21-4-4 4-4",
        "M4 17h16",
) }

/** Lucide `arrow-right-left`. */
val RytmIcons.CompareArrows: ImageVector get() = _compareArrows

private val _creditCard by lazy { icon("CreditCard",
        "M4,5 H20 A2,2 0 0 1 22,7 V17 A2,2 0 0 1 20,19 H4 A2,2 0 0 1 2,17 V7 A2,2 0 0 1 4,5 Z",
        "M2,10 L22,10",
) }

/** Lucide `credit-card`. */
val RytmIcons.CreditCard: ImageVector get() = _creditCard

private val _currencyExchange by lazy { icon("CurrencyExchange",
        "m16 3 4 4-4 4",
        "M20 7H4",
        "m8 21-4-4 4-4",
        "M4 17h16",
) }

/** Lucide `arrow-right-left`. */
val RytmIcons.CurrencyExchange: ImageVector get() = _currencyExchange

private val _darkMode by lazy { icon("DarkMode",
        "M20.985 12.486a9 9 0 1 1-9.473-9.472c.405-.022.617.46.402.803a6 6 0 0 0 8.268 8.268c.344-.215.825-.004.803.401",
) }

/** Lucide `moon`. */
val RytmIcons.DarkMode: ImageVector get() = _darkMode

private val _delete by lazy { icon("Delete",
        "M10 11v6",
        "M14 11v6",
        "M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6",
        "M3 6h18",
        "M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2",
) }

/** Lucide `trash-2`. */
val RytmIcons.Delete: ImageVector get() = _delete

private val _deleteForever by lazy { icon("DeleteForever",
        "M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6",
        "M3 6h18",
        "M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2",
) }

/** Lucide `trash`. */
val RytmIcons.DeleteForever: ImageVector get() = _deleteForever

private val _deleteOutline by lazy { icon("DeleteOutline",
        "M10 11v6",
        "M14 11v6",
        "M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6",
        "M3 6h18",
        "M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2",
) }

/** Lucide `trash-2`. */
val RytmIcons.DeleteOutline: ImageVector get() = _deleteOutline

private val _description by lazy { icon("Description",
        "M6 22a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h8a2.4 2.4 0 0 1 1.704.706l3.588 3.588A2.4 2.4 0 0 1 20 8v12a2 2 0 0 1-2 2z",
        "M14 2v5a1 1 0 0 0 1 1h5",
        "M10 9H8",
        "M16 13H8",
        "M16 17H8",
) }

/** Lucide `file-text`. */
val RytmIcons.Description: ImageVector get() = _description

private val _diamond by lazy { icon("Diamond",
        "M10.5 3 8 9l4 13 4-13-2.5-6",
        "M17 3a2 2 0 0 1 1.6.8l3 4a2 2 0 0 1 .013 2.382l-7.99 10.986a2 2 0 0 1-3.247 0l-7.99-10.986A2 2 0 0 1 2.4 7.8l2.998-3.997A2 2 0 0 1 7 3z",
        "M2 9h20",
) }

/** Lucide `gem`. */
val RytmIcons.Diamond: ImageVector get() = _diamond

private val _directionsCar by lazy { icon("DirectionsCar",
        "M19 17h2c.6 0 1-.4 1-1v-3c0-.9-.7-1.7-1.5-1.9C18.7 10.6 16 10 16 10s-1.3-1.4-2.2-2.3c-.5-.4-1.1-.7-1.8-.7H5c-.6 0-1.1.4-1.4.9l-1.4 2.9A3.7 3.7 0 0 0 2 12v4c0 .6.4 1 1 1h2",
        "M5,17 a2,2 0 1 0 4,0 a2,2 0 1 0 -4,0",
        "M9 17h6",
        "M15,17 a2,2 0 1 0 4,0 a2,2 0 1 0 -4,0",
) }

/** Lucide `car`. */
val RytmIcons.DirectionsCar: ImageVector get() = _directionsCar

private val _documentScanner by lazy { icon("DocumentScanner",
        "M3 7V5a2 2 0 0 1 2-2h2",
        "M17 3h2a2 2 0 0 1 2 2v2",
        "M21 17v2a2 2 0 0 1-2 2h-2",
        "M7 21H5a2 2 0 0 1-2-2v-2",
        "M7 12h10",
) }

/** Lucide `scan-line`. */
val RytmIcons.DocumentScanner: ImageVector get() = _documentScanner

private val _download by lazy { icon("Download",
        "M12 15V3",
        "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4",
        "m7 10 5 5 5-5",
) }

/** Lucide `download`. */
val RytmIcons.Download: ImageVector get() = _download

private val _edit by lazy { icon("Edit",
        "M21.174 6.812a1 1 0 0 0-3.986-3.987L3.842 16.174a2 2 0 0 0-.5.83l-1.321 4.352a.5.5 0 0 0 .623.622l4.353-1.32a2 2 0 0 0 .83-.497z",
        "m15 5 4 4",
) }

/** Lucide `pencil`. */
val RytmIcons.Edit: ImageVector get() = _edit

private val _event by lazy { icon("Event",
        "M8 2v4",
        "M16 2v4",
        "M5,4 H19 A2,2 0 0 1 21,6 V20 A2,2 0 0 1 19,22 H5 A2,2 0 0 1 3,20 V6 A2,2 0 0 1 5,4 Z",
        "M3 10h18",
        "M8 14h.01",
        "M12 14h.01",
        "M16 14h.01",
        "M8 18h.01",
        "M12 18h.01",
        "M16 18h.01",
) }

/** Lucide `calendar-days`. */
val RytmIcons.Event: ImageVector get() = _event

private val _eventAvailable by lazy { icon("EventAvailable",
        "M8 2v4",
        "M16 2v4",
        "M5,4 H19 A2,2 0 0 1 21,6 V20 A2,2 0 0 1 19,22 H5 A2,2 0 0 1 3,20 V6 A2,2 0 0 1 5,4 Z",
        "M3 10h18",
        "m9 16 2 2 4-4",
) }

/** Lucide `calendar-check`. */
val RytmIcons.EventAvailable: ImageVector get() = _eventAvailable

private val _exitToApp by lazy { icon("ExitToApp",
        "m16 17 5-5-5-5",
        "M21 12H9",
        "M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4",
) }

/** Lucide `log-out`. */
val RytmIcons.ExitToApp: ImageVector get() = _exitToApp

private val _expandLess by lazy { icon("ExpandLess",
        "m18 15-6-6-6 6",
) }

/** Lucide `chevron-up`. */
val RytmIcons.ExpandLess: ImageVector get() = _expandLess

private val _expandMore by lazy { icon("ExpandMore",
        "m6 9 6 6 6-6",
) }

/** Lucide `chevron-down`. */
val RytmIcons.ExpandMore: ImageVector get() = _expandMore

private val _fastfood by lazy { icon("Fastfood",
        "M12 16H4a2 2 0 1 1 0-4h16a2 2 0 1 1 0 4h-4.25",
        "M5 12a2 2 0 0 1-2-2 9 7 0 0 1 18 0 2 2 0 0 1-2 2",
        "M5 16a2 2 0 0 0-2 2 3 3 0 0 0 3 3h12a3 3 0 0 0 3-3 2 2 0 0 0-2-2q0 0 0 0",
        "m6.67 12 6.13 4.6a2 2 0 0 0 2.8-.4l3.15-4.2",
) }

/** Lucide `hamburger`. */
val RytmIcons.Fastfood: ImageVector get() = _fastfood

private val _favorite by lazy { icon("Favorite",
        "M2 9.5a5.5 5.5 0 0 1 9.591-3.676.56.56 0 0 0 .818 0A5.49 5.49 0 0 1 22 9.5c0 2.29-1.5 4-3 5.5l-5.492 5.313a2 2 0 0 1-3 .019L5 15c-1.5-1.5-3-3.2-3-5.5",
) }

/** Lucide `heart`. */
val RytmIcons.Favorite: ImageVector get() = _favorite

private val _fingerprint by lazy { icon("Fingerprint",
        "M12 10a2 2 0 0 0-2 2c0 1.02-.1 2.51-.26 4",
        "M14 13.12c0 2.38 0 6.38-1 8.88",
        "M17.29 21.02c.12-.6.43-2.3.5-3.02",
        "M2 12a10 10 0 0 1 18-6",
        "M2 16h.01",
        "M21.8 16c.2-2 .131-5.354 0-6",
        "M5 19.5C5.5 18 6 15 6 12a6 6 0 0 1 .34-2",
        "M8.65 22c.21-.66.45-1.32.57-2",
        "M9 6.8a6 6 0 0 1 9 5.2v2",
) }

/** Lucide `fingerprint`. */
val RytmIcons.Fingerprint: ImageVector get() = _fingerprint

private val _flag by lazy { icon("Flag",
        "M4 22V4a1 1 0 0 1 .4-.8A6 6 0 0 1 8 2c3 0 5 2 7.333 2q2 0 3.067-.8A1 1 0 0 1 20 4v10a1 1 0 0 1-.4.8A6 6 0 0 1 16 16c-3 0-5-2-8-2a6 6 0 0 0-4 1.528",
) }

/** Lucide `flag`. */
val RytmIcons.Flag: ImageVector get() = _flag

private val _gpsFixed by lazy { icon("GpsFixed",
        "M2,12 a10,10 0 1 0 20,0 a10,10 0 1 0 -20,0",
        "M22,12 L18,12",
        "M6,12 L2,12",
        "M12,6 L12,2",
        "M12,22 L12,18",
) }

/** Lucide `crosshair`. */
val RytmIcons.GpsFixed: ImageVector get() = _gpsFixed

private val _gridView by lazy { icon("GridView",
        "M4,3 H9 A1,1 0 0 1 10,4 V9 A1,1 0 0 1 9,10 H4 A1,1 0 0 1 3,9 V4 A1,1 0 0 1 4,3 Z",
        "M15,3 H20 A1,1 0 0 1 21,4 V9 A1,1 0 0 1 20,10 H15 A1,1 0 0 1 14,9 V4 A1,1 0 0 1 15,3 Z",
        "M15,14 H20 A1,1 0 0 1 21,15 V20 A1,1 0 0 1 20,21 H15 A1,1 0 0 1 14,20 V15 A1,1 0 0 1 15,14 Z",
        "M4,14 H9 A1,1 0 0 1 10,15 V20 A1,1 0 0 1 9,21 H4 A1,1 0 0 1 3,20 V15 A1,1 0 0 1 4,14 Z",
) }

/** Lucide `layout-grid`. */
val RytmIcons.GridView: ImageVector get() = _gridView

private val _group by lazy { icon("Group",
        "M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2",
        "M16 3.128a4 4 0 0 1 0 7.744",
        "M22 21v-2a4 4 0 0 0-3-3.87",
        "M5,7 a4,4 0 1 0 8,0 a4,4 0 1 0 -8,0",
) }

/** Lucide `users`. */
val RytmIcons.Group: ImageVector get() = _group

private val _groups by lazy { icon("Groups",
        "M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2",
        "M16 3.128a4 4 0 0 1 0 7.744",
        "M22 21v-2a4 4 0 0 0-3-3.87",
        "M5,7 a4,4 0 1 0 8,0 a4,4 0 1 0 -8,0",
) }

/** Lucide `users`. */
val RytmIcons.Groups: ImageVector get() = _groups

private val _home by lazy { icon("Home",
        "M15 21v-8a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v8",
        "M3 10a2 2 0 0 1 .709-1.528l7-6a2 2 0 0 1 2.582 0l7 6A2 2 0 0 1 21 10v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z",
) }

/** Lucide `house`. */
val RytmIcons.Home: ImageVector get() = _home

private val _inbox by lazy { icon("Inbox",
        "M22,12 L16,12 L14,15 L10,15 L8,12 L2,12",
        "M5.45 5.11 2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.45-6.89A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11z",
) }

/** Lucide `inbox`. */
val RytmIcons.Inbox: ImageVector get() = _inbox

private val _info by lazy { icon("Info",
        "M2,12 a10,10 0 1 0 20,0 a10,10 0 1 0 -20,0",
        "M12 16v-4",
        "M12 8h.01",
) }

/** Lucide `info`. */
val RytmIcons.Info: ImageVector get() = _info

private val _language by lazy { icon("Language",
        "m5 8 6 6",
        "m4 14 6-6 2-3",
        "M2 5h12",
        "M7 2h1",
        "m22 22-5-10-5 10",
        "M14 18h6",
) }

/** Lucide `languages`. */
val RytmIcons.Language: ImageVector get() = _language

private val _lightMode by lazy { icon("LightMode",
        "M8,12 a4,4 0 1 0 8,0 a4,4 0 1 0 -8,0",
        "M12 2v2",
        "M12 20v2",
        "m4.93 4.93 1.41 1.41",
        "m17.66 17.66 1.41 1.41",
        "M2 12h2",
        "M20 12h2",
        "m6.34 17.66-1.41 1.41",
        "m19.07 4.93-1.41 1.41",
) }

/** Lucide `sun`. */
val RytmIcons.LightMode: ImageVector get() = _lightMode

private val _localCafe by lazy { icon("LocalCafe",
        "M10 2v2",
        "M14 2v2",
        "M16 8a1 1 0 0 1 1 1v8a4 4 0 0 1-4 4H7a4 4 0 0 1-4-4V9a1 1 0 0 1 1-1h14a4 4 0 1 1 0 8h-1",
        "M6 2v2",
) }

/** Lucide `coffee`. */
val RytmIcons.LocalCafe: ImageVector get() = _localCafe

private val _localFireDepartment by lazy { icon("LocalFireDepartment",
        "M12 3q1 4 4 6.5t3 5.5a1 1 0 0 1-14 0 5 5 0 0 1 1-3 1 1 0 0 0 5 0c0-2-1.5-3-1.5-5q0-2 2.5-4",
) }

/** Lucide `flame`. */
val RytmIcons.LocalFireDepartment: ImageVector get() = _localFireDepartment

private val _localPharmacy by lazy { icon("LocalPharmacy",
        "m10.5 20.5 10-10a4.95 4.95 0 1 0-7-7l-10 10a4.95 4.95 0 1 0 7 7Z",
        "m8.5 8.5 7 7",
) }

/** Lucide `pill`. */
val RytmIcons.LocalPharmacy: ImageVector get() = _localPharmacy

private val _lock by lazy { icon("Lock",
        "M5,11 H19 A2,2 0 0 1 21,13 V20 A2,2 0 0 1 19,22 H5 A2,2 0 0 1 3,20 V13 A2,2 0 0 1 5,11 Z",
        "M7 11V7a5 5 0 0 1 10 0v4",
) }

/** Lucide `lock`. */
val RytmIcons.Lock: ImageVector get() = _lock

private val _lockClock by lazy { icon("LockClock",
        "M12 6v6l4 2",
        "M20 12v5",
        "M20 21h.01",
        "M21.25 8.2A10 10 0 1 0 16 21.16",
) }

/** Lucide `clock-alert`. */
val RytmIcons.LockClock: ImageVector get() = _lockClock

private val _logout by lazy { icon("Logout",
        "m16 17 5-5-5-5",
        "M21 12H9",
        "M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4",
) }

/** Lucide `log-out`. */
val RytmIcons.Logout: ImageVector get() = _logout

private val _monetizationOn by lazy { icon("MonetizationOn",
        "M2,12 a10,10 0 1 0 20,0 a10,10 0 1 0 -20,0",
        "M16 8h-6a2 2 0 1 0 0 4h4a2 2 0 1 1 0 4H8",
        "M12 18V6",
) }

/** Lucide `circle-dollar-sign`. */
val RytmIcons.MonetizationOn: ImageVector get() = _monetizationOn

private val _monitorHeart by lazy { icon("MonitorHeart",
        "M2 9.5a5.5 5.5 0 0 1 9.591-3.676.56.56 0 0 0 .818 0A5.49 5.49 0 0 1 22 9.5c0 2.29-1.5 4-3 5.5l-5.492 5.313a2 2 0 0 1-3 .019L5 15c-1.5-1.5-3-3.2-3-5.5",
        "M3.22 13H9.5l.5-1 2 4.5 2-7 1.5 3.5h5.27",
) }

/** Lucide `heart-pulse`. */
val RytmIcons.MonitorHeart: ImageVector get() = _monitorHeart

private val _moreVert by lazy { icon("MoreVert",
        "M11,12 a1,1 0 1 0 2,0 a1,1 0 1 0 -2,0",
        "M11,5 a1,1 0 1 0 2,0 a1,1 0 1 0 -2,0",
        "M11,19 a1,1 0 1 0 2,0 a1,1 0 1 0 -2,0",
) }

/** Lucide `ellipsis-vertical`. */
val RytmIcons.MoreVert: ImageVector get() = _moreVert

private val _notifications by lazy { icon("Notifications",
        "M10.268 21a2 2 0 0 0 3.464 0",
        "M3.262 15.326A1 1 0 0 0 4 17h16a1 1 0 0 0 .74-1.673C19.41 13.956 18 12.499 18 8A6 6 0 0 0 6 8c0 4.499-1.411 5.956-2.738 7.326",
) }

/** Lucide `bell`. */
val RytmIcons.Notifications: ImageVector get() = _notifications

private val _notificationsActive by lazy { icon("NotificationsActive",
        "M10.268 21a2 2 0 0 0 3.464 0",
        "M22 8c0-2.3-.8-4.3-2-6",
        "M3.262 15.326A1 1 0 0 0 4 17h16a1 1 0 0 0 .74-1.673C19.41 13.956 18 12.499 18 8A6 6 0 0 0 6 8c0 4.499-1.411 5.956-2.738 7.326",
        "M4 2C2.8 3.7 2 5.7 2 8",
) }

/** Lucide `bell-ring`. */
val RytmIcons.NotificationsActive: ImageVector get() = _notificationsActive

private val _payments by lazy { icon("Payments",
        "M11 15h2a2 2 0 1 0 0-4h-3c-.6 0-1.1.2-1.4.6L3 17",
        "m7 21 1.6-1.4c.3-.4.8-.6 1.4-.6h4c1.1 0 2.1-.4 2.8-1.2l4.6-4.4a2 2 0 0 0-2.75-2.91l-4.2 3.9",
        "m2 16 6 6",
        "M13.1,9 a2.9,2.9 0 1 0 5.8,0 a2.9,2.9 0 1 0 -5.8,0",
        "M3,5 a3,3 0 1 0 6,0 a3,3 0 1 0 -6,0",
) }

/** Lucide `hand-coins`. */
val RytmIcons.Payments: ImageVector get() = _payments

private val _person by lazy { icon("Person",
        "M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2",
        "M8,7 a4,4 0 1 0 8,0 a4,4 0 1 0 -8,0",
) }

/** Lucide `user`. */
val RytmIcons.Person: ImageVector get() = _person

private val _phone by lazy { icon("Phone",
        "M13.832 16.568a1 1 0 0 0 1.213-.303l.355-.465A2 2 0 0 1 17 15h3a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2A18 18 0 0 1 2 4a2 2 0 0 1 2-2h3a2 2 0 0 1 2 2v3a2 2 0 0 1-.8 1.6l-.468.351a1 1 0 0 0-.292 1.233 14 14 0 0 0 6.392 6.384",
) }

/** Lucide `phone`. */
val RytmIcons.Phone: ImageVector get() = _phone

private val _photoCamera by lazy { icon("PhotoCamera",
        "M13.997 4a2 2 0 0 1 1.76 1.05l.486.9A2 2 0 0 0 18.003 7H20a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2h1.997a2 2 0 0 0 1.759-1.048l.489-.904A2 2 0 0 1 10.004 4z",
        "M9,13 a3,3 0 1 0 6,0 a3,3 0 1 0 -6,0",
) }

/** Lucide `camera`. */
val RytmIcons.PhotoCamera: ImageVector get() = _photoCamera

private val _photoLibrary by lazy { icon("PhotoLibrary",
        "m22 11-1.296-1.296a2.4 2.4 0 0 0-3.408 0L11 16",
        "M4 8a2 2 0 0 0-2 2v10a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2",
        "M12,7 a1,1 0 1 0 2,0 a1,1 0 1 0 -2,0",
        "M10,2 H20 A2,2 0 0 1 22,4 V14 A2,2 0 0 1 20,16 H10 A2,2 0 0 1 8,14 V4 A2,2 0 0 1 10,2 Z",
) }

/** Lucide `images`. */
val RytmIcons.PhotoLibrary: ImageVector get() = _photoLibrary

private val _pieChart by lazy { icon("PieChart",
        "M21 12c.552 0 1.005-.449.95-.998a10 10 0 0 0-8.953-8.951c-.55-.055-.998.398-.998.95v8a1 1 0 0 0 1 1z",
        "M21.21 15.89A10 10 0 1 1 8 2.83",
) }

/** Lucide `pie-chart`. */
val RytmIcons.PieChart: ImageVector get() = _pieChart

private val _privacyTip by lazy { icon("PrivacyTip",
        "M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z",
        "M12 8v4",
        "M12 16h.01",
) }

/** Lucide `shield-alert`. */
val RytmIcons.PrivacyTip: ImageVector get() = _privacyTip

private val _public by lazy { icon("Public",
        "M2,12 a10,10 0 1 0 20,0 a10,10 0 1 0 -20,0",
        "M12 2a14.5 14.5 0 0 0 0 20 14.5 14.5 0 0 0 0-20",
        "M2 12h20",
) }

/** Lucide `globe`. */
val RytmIcons.Public: ImageVector get() = _public

private val _receipt by lazy { icon("Receipt",
        "M12 17V7",
        "M16 8h-6a2 2 0 0 0 0 4h4a2 2 0 0 1 0 4H8",
        "M4 3a1 1 0 0 1 1-1 1.3 1.3 0 0 1 .7.2l.933.6a1.3 1.3 0 0 0 1.4 0l.934-.6a1.3 1.3 0 0 1 1.4 0l.933.6a1.3 1.3 0 0 0 1.4 0l.933-.6a1.3 1.3 0 0 1 1.4 0l.934.6a1.3 1.3 0 0 0 1.4 0l.933-.6A1.3 1.3 0 0 1 19 2a1 1 0 0 1 1 1v18a1 1 0 0 1-1 1 1.3 1.3 0 0 1-.7-.2l-.933-.6a1.3 1.3 0 0 0-1.4 0l-.934.6a1.3 1.3 0 0 1-1.4 0l-.933-.6a1.3 1.3 0 0 0-1.4 0l-.933.6a1.3 1.3 0 0 1-1.4 0l-.934-.6a1.3 1.3 0 0 0-1.4 0l-.933.6a1.3 1.3 0 0 1-.7.2 1 1 0 0 1-1-1z",
) }

/** Lucide `receipt`. */
val RytmIcons.Receipt: ImageVector get() = _receipt

private val _refresh by lazy { icon("Refresh",
        "M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8",
        "M21 3v5h-5",
        "M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16",
        "M8 16H3v5",
) }

/** Lucide `refresh-cw`. */
val RytmIcons.Refresh: ImageVector get() = _refresh

private val _repeat by lazy { icon("Repeat",
        "m17 2 4 4-4 4",
        "M3 11v-1a4 4 0 0 1 4-4h14",
        "m7 22-4-4 4-4",
        "M21 13v1a4 4 0 0 1-4 4H3",
) }

/** Lucide `repeat`. */
val RytmIcons.Repeat: ImageVector get() = _repeat

private val _requestQuote by lazy { icon("RequestQuote",
        "M13 16H8",
        "M14 8H8",
        "M16 12H8",
        "M4 3a1 1 0 0 1 1-1 1.3 1.3 0 0 1 .7.2l.933.6a1.3 1.3 0 0 0 1.4 0l.934-.6a1.3 1.3 0 0 1 1.4 0l.933.6a1.3 1.3 0 0 0 1.4 0l.933-.6a1.3 1.3 0 0 1 1.4 0l.934.6a1.3 1.3 0 0 0 1.4 0l.933-.6A1.3 1.3 0 0 1 19 2a1 1 0 0 1 1 1v18a1 1 0 0 1-1 1 1.3 1.3 0 0 1-.7-.2l-.933-.6a1.3 1.3 0 0 0-1.4 0l-.934.6a1.3 1.3 0 0 1-1.4 0l-.933-.6a1.3 1.3 0 0 0-1.4 0l-.933.6a1.3 1.3 0 0 1-1.4 0l-.934-.6a1.3 1.3 0 0 0-1.4 0l-.933.6a1.3 1.3 0 0 1-.7.2 1 1 0 0 1-1-1z",
) }

/** Lucide `receipt-text`. */
val RytmIcons.RequestQuote: ImageVector get() = _requestQuote

private val _restartAlt by lazy { icon("RestartAlt",
        "M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8",
        "M3 3v5h5",
) }

/** Lucide `rotate-ccw`. */
val RytmIcons.RestartAlt: ImageVector get() = _restartAlt

private val _schedule by lazy { icon("Schedule",
        "M2,12 a10,10 0 1 0 20,0 a10,10 0 1 0 -20,0",
        "M12 6v6l4 2",
) }

/** Lucide `clock`. */
val RytmIcons.Schedule: ImageVector get() = _schedule

private val _search by lazy { icon("Search",
        "m21 21-4.34-4.34",
        "M3,11 a8,8 0 1 0 16,0 a8,8 0 1 0 -16,0",
) }

/** Lucide `search`. */
val RytmIcons.Search: ImageVector get() = _search

private val _security by lazy { icon("Security",
        "M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z",
        "m9 12 2 2 4-4",
) }

/** Lucide `shield-check`. */
val RytmIcons.Security: ImageVector get() = _security

private val _sell by lazy { icon("Sell",
        "M12.586 2.586A2 2 0 0 0 11.172 2H4a2 2 0 0 0-2 2v7.172a2 2 0 0 0 .586 1.414l8.704 8.704a2.426 2.426 0 0 0 3.42 0l6.58-6.58a2.426 2.426 0 0 0 0-3.42z",
        "M7,7.5 a0.5,0.5 0 1 0 1,0 a0.5,0.5 0 1 0 -1,0",
) }

/** Lucide `tag`. */
val RytmIcons.Sell: ImageVector get() = _sell

private val _settings by lazy { icon("Settings",
        "M9.671 4.136a2.34 2.34 0 0 1 4.659 0 2.34 2.34 0 0 0 3.319 1.915 2.34 2.34 0 0 1 2.33 4.033 2.34 2.34 0 0 0 0 3.831 2.34 2.34 0 0 1-2.33 4.033 2.34 2.34 0 0 0-3.319 1.915 2.34 2.34 0 0 1-4.659 0 2.34 2.34 0 0 0-3.32-1.915 2.34 2.34 0 0 1-2.33-4.033 2.34 2.34 0 0 0 0-3.831A2.34 2.34 0 0 1 6.35 6.051a2.34 2.34 0 0 0 3.319-1.915",
        "M9,12 a3,3 0 1 0 6,0 a3,3 0 1 0 -6,0",
) }

/** Lucide `settings`. */
val RytmIcons.Settings: ImageVector get() = _settings

private val _share by lazy { icon("Share",
        "M15,5 a3,3 0 1 0 6,0 a3,3 0 1 0 -6,0",
        "M3,12 a3,3 0 1 0 6,0 a3,3 0 1 0 -6,0",
        "M15,19 a3,3 0 1 0 6,0 a3,3 0 1 0 -6,0",
        "M8.59,13.51 L15.42,17.49",
        "M15.41,6.51 L8.59,10.49",
) }

/** Lucide `share-2`. */
val RytmIcons.Share: ImageVector get() = _share

private val _shoppingBag by lazy { icon("ShoppingBag",
        "M16 10a4 4 0 0 1-8 0",
        "M3.103 6.034h17.794",
        "M3.4 5.467a2 2 0 0 0-.4 1.2V20a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6.667a2 2 0 0 0-.4-1.2l-2-2.667A2 2 0 0 0 17 2H7a2 2 0 0 0-1.6.8z",
) }

/** Lucide `shopping-bag`. */
val RytmIcons.ShoppingBag: ImageVector get() = _shoppingBag

private val _shoppingCart by lazy { icon("ShoppingCart",
        "M7,21 a1,1 0 1 0 2,0 a1,1 0 1 0 -2,0",
        "M18,21 a1,1 0 1 0 2,0 a1,1 0 1 0 -2,0",
        "M2.05 2.05h2l2.66 12.42a2 2 0 0 0 2 1.58h9.78a2 2 0 0 0 1.95-1.57l1.65-7.43H5.12",
) }

/** Lucide `shopping-cart`. */
val RytmIcons.ShoppingCart: ImageVector get() = _shoppingCart

private val _smokingRooms by lazy { icon("SmokingRooms",
        "M17 12H3a1 1 0 0 0-1 1v2a1 1 0 0 0 1 1h14",
        "M18 8c0-2.5-2-2.5-2-5",
        "M21 16a1 1 0 0 0 1-1v-2a1 1 0 0 0-1-1",
        "M22 8c0-2.5-2-2.5-2-5",
        "M7 12v4",
) }

/** Lucide `cigarette`. */
val RytmIcons.SmokingRooms: ImageVector get() = _smokingRooms

private val _star by lazy { icon("Star",
        "M11.525 2.295a.53.53 0 0 1 .95 0l2.31 4.679a2.123 2.123 0 0 0 1.595 1.16l5.166.756a.53.53 0 0 1 .294.904l-3.736 3.638a2.123 2.123 0 0 0-.611 1.878l.882 5.14a.53.53 0 0 1-.771.56l-4.618-2.428a2.122 2.122 0 0 0-1.973 0L6.396 21.01a.53.53 0 0 1-.77-.56l.881-5.139a2.122 2.122 0 0 0-.611-1.879L2.16 9.795a.53.53 0 0 1 .294-.906l5.165-.755a2.122 2.122 0 0 0 1.597-1.16z",
) }

/** Lucide `star`. */
val RytmIcons.Star: ImageVector get() = _star

private val _style by lazy { icon("Style",
        "M12.83 2.18a2 2 0 0 0-1.66 0L2.6 6.08a1 1 0 0 0 0 1.83l8.58 3.91a2 2 0 0 0 1.66 0l8.58-3.9a1 1 0 0 0 0-1.83z",
        "M2 12a1 1 0 0 0 .58.91l8.6 3.91a2 2 0 0 0 1.65 0l8.58-3.9A1 1 0 0 0 22 12",
        "M2 17a1 1 0 0 0 .58.91l8.6 3.91a2 2 0 0 0 1.65 0l8.58-3.9A1 1 0 0 0 22 17",
) }

/** Lucide `layers`. */
val RytmIcons.Style: ImageVector get() = _style

private val _swapHoriz by lazy { icon("SwapHoriz",
        "m16 3 4 4-4 4",
        "M20 7H4",
        "m8 21-4-4 4-4",
        "M4 17h16",
) }

/** Lucide `arrow-right-left`. */
val RytmIcons.SwapHoriz: ImageVector get() = _swapHoriz

private val _sync by lazy { icon("Sync",
        "M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8",
        "M21 3v5h-5",
        "M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16",
        "M8 16H3v5",
) }

/** Lucide `refresh-cw`. */
val RytmIcons.Sync: ImageVector get() = _sync

private val _tipsAndUpdates by lazy { icon("TipsAndUpdates",
        "M15 14c.2-1 .7-1.7 1.5-2.5 1-.9 1.5-2.2 1.5-3.5A6 6 0 0 0 6 8c0 1 .2 2.2 1.5 3.5.7.7 1.3 1.5 1.5 2.5",
        "M9 18h6",
        "M10 22h4",
) }

/** Lucide `lightbulb`. */
val RytmIcons.TipsAndUpdates: ImageVector get() = _tipsAndUpdates

private val _trackChanges by lazy { icon("TrackChanges",
        "M2,12 a10,10 0 1 0 20,0 a10,10 0 1 0 -20,0",
        "M6,12 a6,6 0 1 0 12,0 a6,6 0 1 0 -12,0",
        "M10,12 a2,2 0 1 0 4,0 a2,2 0 1 0 -4,0",
) }

/** Lucide `target`. */
val RytmIcons.TrackChanges: ImageVector get() = _trackChanges

private val _trendingDown by lazy { icon("TrendingDown",
        "M16 17h6v-6",
        "m22 17-8.5-8.5-5 5L2 7",
) }

/** Lucide `trending-down`. */
val RytmIcons.TrendingDown: ImageVector get() = _trendingDown

private val _trendingUp by lazy { icon("TrendingUp",
        "M16 7h6v6",
        "m22 7-8.5 8.5-5-5L2 17",
) }

/** Lucide `trending-up`. */
val RytmIcons.TrendingUp: ImageVector get() = _trendingUp

private val _tune by lazy { icon("Tune",
        "M10 5H3",
        "M12 19H3",
        "M14 3v4",
        "M16 17v4",
        "M21 12h-9",
        "M21 19h-5",
        "M21 5h-7",
        "M8 10v4",
        "M8 12H3",
) }

/** Lucide `sliders-horizontal`. */
val RytmIcons.Tune: ImageVector get() = _tune

private val _upload by lazy { icon("Upload",
        "M12 3v12",
        "m17 8-5-5-5 5",
        "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4",
) }

/** Lucide `upload`. */
val RytmIcons.Upload: ImageVector get() = _upload

private val _uploadFile by lazy { icon("UploadFile",
        "M6 22a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h8a2.4 2.4 0 0 1 1.704.706l3.588 3.588A2.4 2.4 0 0 1 20 8v12a2 2 0 0 1-2 2z",
        "M14 2v5a1 1 0 0 0 1 1h5",
        "M12 12v6",
        "m15 15-3-3-3 3",
) }

/** Lucide `file-up`. */
val RytmIcons.UploadFile: ImageVector get() = _uploadFile

private val _visibility by lazy { icon("Visibility",
        "M2.062 12.348a1 1 0 0 1 0-.696 10.75 10.75 0 0 1 19.876 0 1 1 0 0 1 0 .696 10.75 10.75 0 0 1-19.876 0",
        "M9,12 a3,3 0 1 0 6,0 a3,3 0 1 0 -6,0",
) }

/** Lucide `eye`. */
val RytmIcons.Visibility: ImageVector get() = _visibility

private val _visibilityOff by lazy { icon("VisibilityOff",
        "M10.733 5.076a10.744 10.744 0 0 1 11.205 6.575 1 1 0 0 1 0 .696 10.747 10.747 0 0 1-1.444 2.49",
        "M14.084 14.158a3 3 0 0 1-4.242-4.242",
        "M17.479 17.499a10.75 10.75 0 0 1-15.417-5.151 1 1 0 0 1 0-.696 10.75 10.75 0 0 1 4.446-5.143",
        "m2 2 20 20",
) }

/** Lucide `eye-off`. */
val RytmIcons.VisibilityOff: ImageVector get() = _visibilityOff

private val _warningAmber by lazy { icon("WarningAmber",
        "m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3",
        "M12 9v4",
        "M12 17h.01",
) }

/** Lucide `triangle-alert`. */
val RytmIcons.WarningAmber: ImageVector get() = _warningAmber

private val _work by lazy { icon("Work",
        "M16 20V4a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16",
        "M4,6 H20 A2,2 0 0 1 22,8 V18 A2,2 0 0 1 20,20 H4 A2,2 0 0 1 2,18 V8 A2,2 0 0 1 4,6 Z",
) }

/** Lucide `briefcase`. */
val RytmIcons.Work: ImageVector get() = _work

// Added on request (account owner, 2026-08-25) for expense categories the
// curated PICKER_ICONS list didn't cover — games, PC, Google/cloud services,
// mobile, internet, pets. Same js/classic-globals.js ICON_PATHS additions
// mirror these under the same string keys (gamepad/monitor/cloud/mobile/
// wifi/pets) so a category icon chosen on either platform round-trips.
private val _gamepad by lazy { icon("Gamepad",
        "M6,11L10,11",
        "M8,9L8,13",
        "M15,12L15.01,12",
        "M18,10L18.01,10",
        "M17.32 5H6.68a4 4 0 0 0-3.978 3.59c-.006.052-.01.101-.017.152C2.604 9.416 2 14.456 2 16a3 3 0 0 0 3 3c1 0 1.5-.5 2-1l1.414-1.414A2 2 0 0 1 9.828 16h4.344a2 2 0 0 1 1.414.586L17 18c.5.5 1 1 2 1a3 3 0 0 0 3-3c0-1.545-.604-6.584-.685-7.258-.007-.05-.011-.1-.017-.151A4 4 0 0 0 17.32 5z",
) }

/** Lucide `gamepad-2`. */
val RytmIcons.Gamepad: ImageVector get() = _gamepad

private val _monitor by lazy { icon("Monitor",
        "M4,3 H20 A2,2 0 0 1 22,5 V15 A2,2 0 0 1 20,17 H4 A2,2 0 0 1 2,15 V5 A2,2 0 0 1 4,3 Z",
        "M8,21L16,21",
        "M12,17L12,21",
) }

/** Lucide `monitor`. */
val RytmIcons.Monitor: ImageVector get() = _monitor

private val _cloud by lazy { icon("Cloud",
        "M17.5 19H9a7 7 0 1 1 6.71-9h1.79a4.5 4.5 0 1 1 0 9Z",
) }

/** Lucide `cloud`. */
val RytmIcons.Cloud: ImageVector get() = _cloud

private val _mobile by lazy { icon("Mobile",
        "M7,2 H17 A2,2 0 0 1 19,4 V20 A2,2 0 0 1 17,22 H7 A2,2 0 0 1 5,20 V4 A2,2 0 0 1 7,2 Z",
        "M12 18h.01",
) }

/** Lucide `smartphone`. */
val RytmIcons.Mobile: ImageVector get() = _mobile

private val _wifi by lazy { icon("Wifi",
        "M12 20h.01",
        "M2 8.82a15 15 0 0 1 20 0",
        "M5 12.859a10 10 0 0 1 14 0",
        "M8.5 16.429a5 5 0 0 1 7 0",
) }

/** Lucide `wifi`. */
val RytmIcons.Wifi: ImageVector get() = _wifi

private val _pets by lazy { icon("Pets",
        "M9,4 a2,2 0 1 0 4,0 a2,2 0 1 0 -4,0",
        "M16,8 a2,2 0 1 0 4,0 a2,2 0 1 0 -4,0",
        "M18,16 a2,2 0 1 0 4,0 a2,2 0 1 0 -4,0",
        "M9 10a5 5 0 0 1 5 5v3.5a3.5 3.5 0 0 1-6.84 1.045Q6.52 17.48 4.46 16.84A3.5 3.5 0 0 1 5.5 10Z",
) }

/** Lucide `paw-print`. */
val RytmIcons.Pets: ImageVector get() = _pets
