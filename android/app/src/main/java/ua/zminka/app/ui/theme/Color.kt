package ua.zminka.app.ui.theme

import androidx.compose.ui.graphics.Color

// Mirrors index.html's CSS custom properties (--purple/--bg* etc.) so the
// native app reads as the same product, not a re-skin. Keep these two in
// sync if the web palette ever changes — see CLAUDE.md's UI conventions.
val ZminkaPurple = Color(0xFF8B5CF6)
val ZminkaPurple2 = Color(0xFFA78BFA)
val ZminkaPurple3 = Color(0xFF6D28D9)

val ZminkaBgDark = Color(0xFF1C1C1F)
val ZminkaBg1Dark = Color(0xFF242327)
val ZminkaBg2Dark = Color(0xFF2C2B30)
val ZminkaTextDark = Color(0xFFF2F1F5)
val ZminkaMutedDark = Color(0xFF98979E)

val ZminkaBgLight = Color(0xFFF4F3F1)
val ZminkaBg1Light = Color(0xFFFFFFFF)
val ZminkaBg2Light = Color(0xFFECECEA)
val ZminkaTextLight = Color(0xFF1C1C1F)
val ZminkaMutedLight = Color(0xFF626269)

val ZminkaIncome = Color(0xFF22C55E)
val ZminkaExpense = Color(0xFFEF4444)
