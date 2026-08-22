package ua.rytm.app.work

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import ua.rytm.app.data.recurringOccurrenceId

class DailyMaintenanceWorkerTest {
    private val kyiv = ZoneId.of("Europe/Kyiv")

    @Test fun springDstUsesLocalCalendarDate() {
        assertEquals(LocalDate.of(2026, 3, 29), localMaintenanceDate(Instant.parse("2026-03-28T22:30:00Z"), kyiv))
    }

    @Test fun autumnDstUsesLocalCalendarDate() {
        assertEquals(LocalDate.of(2026, 10, 25), localMaintenanceDate(Instant.parse("2026-10-24T21:30:00Z"), kyiv))
    }

    @Test fun recurringOccurrenceIdentityIsStableAndDateScoped() {
        assertEquals(recurringOccurrenceId("salary", "2026-08-22"), recurringOccurrenceId("salary", "2026-08-22"))
        assertNotEquals(recurringOccurrenceId("salary", "2026-08-22"), recurringOccurrenceId("salary", "2026-09-22"))
    }
}
