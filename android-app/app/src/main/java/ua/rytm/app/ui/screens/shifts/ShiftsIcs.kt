package ua.rytm.app.ui.screens.shifts

import java.time.YearMonth

/**
 * The month's shifts as an iCalendar file: one all-day event per working
 * shift (days off are skipped), importable into Google/Samsung/Apple
 * Calendar. UIDs are stable per day+type, so re-importing updates instead of
 * duplicating in calendars that honour UID.
 */
object ShiftsIcs {
    fun build(month: YearMonth, shiftsByDate: Map<String, List<String>>, types: List<ShiftType>, now: java.time.Instant = java.time.Instant.now()): String {
        val byId = types.associateBy { it.id }
        val stamp = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(java.time.ZoneOffset.UTC).format(now)
        val sb = StringBuilder()
        sb.append("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//Rytm//Shifts//UK\r\nCALSCALE:GREGORIAN\r\nMETHOD:PUBLISH\r\n")
        var day = month.atDay(1)
        while (!day.isAfter(month.atEndOfMonth())) {
            shiftsByDate[day.toString()].orEmpty().mapNotNull { byId[it] }.filter { !it.isOff }.forEach { t ->
                val d = day.toString().replace("-", "")
                val next = day.plusDays(1).toString().replace("-", "")
                val hours = if (t.hours > 0) " · ${formatHours(t.hours)} год" else ""
                sb.append("BEGIN:VEVENT\r\n")
                sb.append("UID:rytm-$d-${t.id}@rytm.app\r\n")
                sb.append("DTSTAMP:$stamp\r\n")
                sb.append("DTSTART;VALUE=DATE:$d\r\nDTEND;VALUE=DATE:$next\r\n")
                sb.append("SUMMARY:${escape(t.name + hours)}\r\n")
                sb.append("TRANSP:TRANSPARENT\r\n")
                sb.append("END:VEVENT\r\n")
            }
            day = day.plusDays(1)
        }
        sb.append("END:VCALENDAR\r\n")
        return sb.toString()
    }

    private fun formatHours(h: Double) = if (h == Math.floor(h)) h.toInt().toString() else h.toString()

    /** RFC 5545 TEXT escaping. */
    private fun escape(s: String) = s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\n", "\\n")

}
