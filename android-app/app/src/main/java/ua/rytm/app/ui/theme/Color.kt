package ua.rytm.app.ui.theme

import androidx.compose.ui.graphics.Color

// Mirrors index.html's `:root` / `[data-theme="light"]` CSS custom
// properties (PwaSourceParityTest checks the brand pair). Keep both in sync.

// --- Brand: petrol on graphite (owner decision 2026-11-18) ---
// Replaced Tailwind's violet-500 (#8B5CF6), the default accent of generated
// UIs. The accent is used sparingly: the "+" button, the selected tab, the
// selected filter, links. Everything else is neutral graphite, so money
// colors (green/red) carry the screen.
// Dark fill: M3 dark convention (a light tone with dark ink on it). Dark ink
// on #3AA7BF is 5.7:1; the fill itself as an icon on the #333536 card is 4.4:1.
val Petrol = Color(0xFF3AA7BF)
val OnPetrol = Color(0xFF062A32)
// Light fill: white on #0E6A80 is 6.2:1.
val PetrolDeep = Color(0xFF0E6A80)
// Petrol as TEXT on dark surfaces (links, text buttons): 6.3:1 on the card.
val PetrolLinkDark = Color(0xFF5CC8DE)

// --- Semantic colors ---
// Greens moved off emerald (hue 160) to a warmer green (hue ~142) so income
// never reads as the petrol accent (hue 191).
val GreenDark = Color(0xFF2BB673)
// Dark-theme income text: 7.1:1 on the #333536 card.
val GreenDark2 = Color(0xFF6EDB8F)
val GreenLight2 = Color(0xFF15803D)
// Light-theme income text: 6.2:1 on white, 4.7:1 on the darkest #DFE2E2
// sheet card. See ui/theme/SemanticColors.kt.
val GreenText = Color(0xFF12703A)
val GreenDeep = Color(0xFF0F6B37)

val RedDark = Color(0xFFEF4444)
val RedDark2 = Color(0xFFF87171)
val RedLight2 = Color(0xFFDC2626)
val RedText = Color(0xFFBE1E1E)
// Dark-theme expense text: #F87171 measured 4.26:1 on the #38373D card —
// below AA for the 16sp amounts. #FA8A8A is 5.09:1 (P5 contrast pass).
val RedDarkText = Color(0xFFFA8A8A)

val BlueDark = Color(0xFF3B82F6)
val BlueDark2 = Color(0xFF60A5FA)
val BlueLight2 = Color(0xFF2563EB)

val OrangeDark = Color(0xFFF59E0B)
val OrangeDark2 = Color(0xFFFBBF24)
val OrangeLight2 = Color(0xFFC2760A)
// Amber body text on light surfaces: OrangeLight2 is only ~3.3:1 on white.
val AmberText = Color(0xFF92400E)

val Pink = Color(0xFFEC4899)
val Cyan = Color(0xFF06B6D4)
// Data colors only (category palette, avatar choices) — never chrome.
val PurpleDark = Color(0xFF8B5CF6)
val PurpleLight2 = Color(0xFF7C3AED)

// --- Dark theme surfaces: graphite with a faint cool bias toward the accent ---
val DarkBg = Color(0xFF17191A)
val DarkBg1 = Color(0xFF1F2122)
val DarkBg2 = Color(0xFF27292A)
val DarkBg3 = Color(0xFF333536)
val DarkBorder = Color(0xFF3D3F40)
val DarkBorder2 = Color(0xFF4E5152)
val DarkText = Color(0xFFE8EAEA)
val DarkTextStrong = Color(0xFFFFFFFF)
val DarkMuted = Color(0xFF94989A)
val DarkMuted2 = Color(0xFF979B9C)
// Dark onSurfaceVariant: 5.3:1 on the #333536 card where most secondary
// text sits.
val DarkMutedText = Color(0xFFA6AAAB)
// Midpoint between DarkBorder2 (outlineVariant) and DarkMuted2 -- see
// Theme.kt's `outline` doc comment for why this exists as its own token.
val DarkHairline = Color(0xFF717576)

// --- Light theme surfaces ---
val LightBg = Color(0xFFF2F3F3)
val LightBg1 = Color(0xFFFFFFFF)
val LightBg2 = Color(0xFFEAECEC)
val LightBg3 = Color(0xFFDFE2E2)
val LightBorder = Color(0xFFE1E4E4)
val LightBorder2 = Color(0xFFCDD1D2)
val LightText = Color(0xFF1A1C1D)
val LightTextStrong = Color(0xFF0A0B0C)
val LightMuted = Color(0xFF676B6D)
// 4.7:1 on the darkest #DFE2E2 sheet card.
val LightMuted2 = Color(0xFF5E6264)
// Midpoint between LightBorder2 (outlineVariant) and LightMuted2 -- see
// Theme.kt's `outline` doc comment.
val LightHairline = Color(0xFF969A9C)

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

// Light halves of the built-in avatar gradients. Their previous values were
// literally Google's Material palette (#A8C7FA/#C4C7C5/#0B57D0/#1A73E8/
// #137333/#5F6368) — another product's brand, not Rytm's.
val AvatarTintBlue = Color(0xFFC7B9F5)
val AvatarTintGray = Color(0xFFC7C6CC)
val AvatarTintGreen = Color(0xFF9FE0C4)
val AvatarTintPurple = Color(0xFFD7C2FB)
val AvatarTintSky = Color(0xFFAECBFA)
val AvatarTintPink = Color(0xFFF8BBD0)

// Container / inverse roles, pinned so nothing falls back to M3's baseline
// pinks and lavenders (banners, snackbars, selected-item pills). Each on-pair
// is checked >= 4.5:1 in ThemeContrastTest.
val DarkPrimaryContainer = Color(0xFF113A44)
val DarkOnPrimaryContainer = Color(0xFFBDEBF5)
val DarkWarnContainer = Color(0xFF3D3424)
val DarkErrorContainer = Color(0xFF4A2326)
val DarkOnErrorContainer = Color(0xFFFFDAD6)
val LightPrimaryContainer = Color(0xFFD9EEF2)
val LightOnPrimaryContainer = Color(0xFF0B4654)
val LightWarnContainer = Color(0xFFFDF0D5)
val LightErrorContainer = Color(0xFFFDE3E1)
val LightOnErrorContainer = Color(0xFF7F1D1D)
val LightInverseSurface = Color(0xFF2C2E2F)
