package ua.rytm.app.ui.components

import android.os.SystemClock

// Guards "add"-style actions against a double tap creating two records.
// Manager ViewModels serialize writes behind a Mutex, which prevents races
// but not duplicates: two quick taps still queue two separate inserts.
class TapThrottle(private val windowMs: Long = 700L) {
    private var lastAt = Long.MIN_VALUE / 2 // first tap always allowed, whatever the clock says

    fun allow(): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now - lastAt < windowMs) return false
        lastAt = now
        return true
    }
}
