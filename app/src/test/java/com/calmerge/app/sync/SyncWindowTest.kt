package com.calmerge.app.sync

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class SyncWindowTest {

    @Test
    fun `default window spans 7 days past to 60 days future, day-aligned`() {
        val now = Instant.parse("2026-06-11T15:42:13Z")
        val window = SyncWindow.around(now)
        assertEquals(Instant.parse("2026-06-04T00:00:00Z"), window.startUtc)
        // +60 days future plus the remainder of today → end is exclusive day 61.
        assertEquals(Instant.parse("2026-08-11T00:00:00Z"), window.endUtc)
    }

    @Test
    fun `window is stable within the same day`() {
        val morning = SyncWindow.around(Instant.parse("2026-06-11T01:00:00Z"))
        val evening = SyncWindow.around(Instant.parse("2026-06-11T23:59:59Z"))
        assertEquals(morning, evening)
    }

    @Test
    fun `custom window sizes are honored`() {
        val now = Instant.parse("2026-06-11T12:00:00Z")
        val window = SyncWindow.around(now, daysPast = 1, daysFuture = 1)
        assertEquals(Instant.parse("2026-06-10T00:00:00Z"), window.startUtc)
        assertEquals(Instant.parse("2026-06-13T00:00:00Z"), window.endUtc)
    }
}
