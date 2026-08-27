package ua.rytm.app.ui.theme

import androidx.compose.ui.graphics.Color

// Ported 1:1 from index.html's `:root` / `[data-theme="light"]` CSS custom
// properties — see ANDROID_MIGRATION.md §4. Keep in sync if the PWA palette
// changes; don't invent new brand colors here.

// --- Brand (purple), same across themes ---
val PurpleDark = Color(0xFF8B5CF6) // --purple (dark theme accent)
val PurpleDark2 = Color(0xFFA78BFA) // --purple2 (dark)
val Purple3 = Color(0xFF6D28D9) // --purple3
val PurpleLight2 = Color(0xFF7C3AED) // --purple2 (light theme)

// --- Semantic colors ---
val GreenDark = Color(0xFF10B981)
val GreenDark2 = Color(0xFF34D399)
val GreenLight2 = Color(0xFF059669)
// Accessible light-theme money tones. #059669/#DC2626 measure 3.77:1/4.83:1
// on white and far less on the tinted mini-stat wash, so semantic money text
// in the light theme uses these deeper tones instead (7.8:1 / 8.3:1 on white,
// >5:1 on the wash). See ui/theme/SemanticColors.kt.
val GreenText = Color(0xFF065F46)
// Gradient end for the green FAB: white-on-#10B981 was 2.54:1 even as large
// bold text, #059669→#047857 keeps it at 3.77:1 minimum.
val GreenDeep = Color(0xFF047857)

val RedDark = Color(0xFFEF4444)
val RedDark2 = Color(0xFFF87171)
val RedLight2 = Color(0xFFDC2626)
val RedText = Color(0xFF991B1B)

val BlueDark = Color(0xFF3B82F6)
val BlueDark2 = Color(0xFF60A5FA)
val BlueLight2 = Color(0xFF2563EB)

val OrangeDark = Color(0xFFF59E0B)
val OrangeDark2 = Color(0xFFFBBF24)
val OrangeLight2 = Color(0xFFC2760A)

val Pink = Color(0xFFEC4899)
val Cyan = Color(0xFF06B6D4)

// --- Dark theme surfaces (--bg / --bg1 / --bg2 / --bg3) ---
val DarkBg = Color(0xFF1C1C1F)
val DarkBg1 = Color(0xFF242327)
val DarkBg2 = Color(0xFF2C2B30)
val DarkBg3 = Color(0xFF38373D)
val DarkBorder = Color(0xFF403F45)
val DarkBorder2 = Color(0xFF525158)
val DarkText = Color(0xFFE9E8EA)
val DarkTextStrong = Color(0xFFFFFFFF)
val DarkMuted = Color(0xFF96959C)
val DarkMuted2 = Color(0xFF98979E)

// --- Light theme surfaces ---
val LightBg = Color(0xFFF4F3F1)
val LightBg1 = Color(0xFFFFFFFF)
val LightBg2 = Color(0xFFECECEA)
val LightBg3 = Color(0xFFE2E0DD)
val LightBorder = Color(0xFFE4E4E9)
val LightBorder2 = Color(0xFFD1D1D6)
val LightText = Color(0xFF1C1C1E)
val LightTextStrong = Color(0xFF0B0B0D)
val LightMuted = Color(0xFF6B6B70)
val LightMuted2 = Color(0xFF626269)

// --- Extended palette ---
// Colors the UI layer was already using as raw hex literals. Kept as named
// tokens instead of being force-fitted onto the PWA-ported set above, but
// declared here so nothing outside ui/theme/ names a hex value any more.
val Teal = Color(0xFF14B8A6)
val TealDeep = Color(0xFF0F766E)
val Slate = Color(0xFF64748B)
val SlateDeep = Color(0xFF334155)
val Gray = Color(0xFF6B7280)
val AmberDeep = Color(0xFFB45309)
val Amber600 = Color(0xFFD97706)
val PinkDeep = Color(0xFFDB2777)

// External brand marks (BTC/ETH), used only as badge fills with a computed
// on-color — never as text on a plain surface, where #F7931A measures
// 2.30:1 against a light background.
val BitcoinOrange = Color(0xFFF7931A)
val EthereumBlue = Color(0xFF627EEA)

// Monobank's own brand black. Drawn on a badge, so it needs a light-theme
// counterpart: on a dark surfaceContainer the pure #111111 badge was
// invisible (~1.5:1) and only the white glyph floated in the void.
val MonobankBrand = Color(0xFF111111)
// The original #3A3A3D "fix" above was itself nearly invisible — it's
// almost the same tone as DarkBg3 (#38373D, one of this theme's own darkest
// surface fills), and SettingsIconBadge tints BOTH the 16%-alpha circle AND
// the glyph with this one color, so a too-dark value makes the whole badge
// disappear rather than just look low-contrast. Reported live (screenshot):
// "Прив'язати Monobank" had no visible icon at all in dark mode. A neutral
// mid-gray (Tailwind's gray-400 family) reads clearly against every dark
// surface tone in this theme while still looking like a monochrome brand
// mark rather than introducing a new hue.
val MonobankBrandDark = Color(0xFF9CA3AF)

// Light halves of the built-in avatar gradients. Their previous values were
// literally Google's Material palette (#A8C7FA/#C4C7C5/#0B57D0/#1A73E8/
// #137333/#5F6368) — another product's brand, not Rytm's.
val AvatarTintBlue = Color(0xFFC7B9F5)
val AvatarTintGray = Color(0xFFC7C6CC)
val AvatarTintGreen = Color(0xFF9FE0C4)
val AvatarTintPurple = Color(0xFFD7C2FB)
val AvatarTintSky = Color(0xFFAECBFA)
val AvatarTintPink = Color(0xFFF8BBD0)
