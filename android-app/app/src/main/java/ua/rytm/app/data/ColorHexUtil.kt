package ua.rytm.app.data

// Shared by every *SyncRepository — 0xAARRGGBB (Android's Compose Color
// convention) <-> "#rrggbb" (the PWA's CSS hex convention). Extracted out of
// FinanceSyncRepository once ShiftsSyncRepository needed the identical
// conversion, rather than duplicating it per-file.

internal fun colorHexToWebString(colorHex: Long): String {
    val rgb = colorHex and 0xFFFFFFL
    return "#" + rgb.toString(16).padStart(6, '0')
}

internal fun webStringToColorHex(hex: String?, fallback: Long = 0x8B5CF6L): Long {
    val rgb = hex?.removePrefix("#")?.toLongOrNull(16) ?: fallback
    return 0xFF000000L or rgb
}
