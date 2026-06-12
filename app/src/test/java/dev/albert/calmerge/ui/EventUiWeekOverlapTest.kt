package dev.albert.calmerge.ui

import dev.albert.calmerge.data.db.AccountType
import dev.albert.calmerge.data.db.EventInstanceEntity
import dev.albert.calmerge.data.db.MergedEvent
import dev.albert.calmerge.data.db.ResponseStatus
import dev.albert.calmerge.data.db.ShowAs
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class EventUiWeekOverlapTest {

    private val zone = ZoneId.of("UTC")

    // Monday 2026-06-08 00:00 UTC
    private val weekStart = LocalDate.of(2026, 6, 8).atStartOfDay(zone).toInstant().toEpochMilli()
    // Monday 2026-06-15 00:00 UTC
    private val weekEnd = LocalDate.of(2026, 6, 15).atStartOfDay(zone).toInstant().toEpochMilli()

    private fun timedEvent(startUtc: Long, endUtc: Long): MergedEvent {
        val e = EventInstanceEntity(
            id = "e",
            calendarSourceId = "s",
            providerEventId = "p",
            iCalUId = null,
            title = "T",
            startUtc = startUtc,
            endUtc = endUtc,
            isAllDay = false,
            startDate = null,
            endDate = null,
            showAs = ShowAs.BUSY,
            responseStatus = ResponseStatus.ACCEPTED,
            location = null,
            organizer = null,
            lastModifiedUtc = null,
        )
        return MergedEvent(e, "acc", "Acc", 0, AccountType.ICS)
    }

    private fun allDayEvent(startDate: String, endDate: String): MergedEvent {
        val e = EventInstanceEntity(
            id = "e",
            calendarSourceId = "s",
            providerEventId = "p",
            iCalUId = null,
            title = "T",
            startUtc = null,
            endUtc = null,
            isAllDay = true,
            startDate = startDate,
            endDate = endDate,
            showAs = ShowAs.BUSY,
            responseStatus = ResponseStatus.ACCEPTED,
            location = null,
            organizer = null,
            lastModifiedUtc = null,
        )
        return MergedEvent(e, "acc", "Acc", 0, AccountType.ICS)
    }

    @Test
    fun `event spanning Monday boundary remains visible`() {
        // Started Sunday, ends Monday: should overlap the Mon–Sun week.
        val sunday = LocalDate.of(2026, 6, 7).atStartOfDay(zone).toInstant().toEpochMilli()
        val monday = LocalDate.of(2026, 6, 8).atStartOfDay(zone).toInstant().toEpochMilli() + 3_600_000L
        assertTrue(EventUi.overlapsWeek(timedEvent(sunday, monday), weekStart, weekEnd, zone))
    }

    @Test
    fun `event fully inside week is visible`() {
        val start = LocalDate.of(2026, 6, 10).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = start + 3_600_000L
        assertTrue(EventUi.overlapsWeek(timedEvent(start, end), weekStart, weekEnd, zone))
    }

    @Test
    fun `event before week is not visible`() {
        val end = weekStart - 1
        val start = end - 3_600_000L
        assertFalse(EventUi.overlapsWeek(timedEvent(start, end), weekStart, weekEnd, zone))
    }

    @Test
    fun `event starting exactly at weekEnd is not visible`() {
        // [weekStart, weekEnd) is exclusive at weekEnd.
        assertTrue(EventUi.overlapsWeek(timedEvent(weekEnd - 1, weekEnd + 3_600_000L), weekStart, weekEnd, zone))
        assertFalse(EventUi.overlapsWeek(timedEvent(weekEnd, weekEnd + 3_600_000L), weekStart, weekEnd, zone))
    }

    @Test
    fun `all-day event spanning Monday boundary remains visible`() {
        // 2026-06-07 to 2026-06-09 (exclusive end) spans into the week starting 2026-06-08.
        assertTrue(EventUi.overlapsWeek(allDayEvent("2026-06-07", "2026-06-09"), weekStart, weekEnd, zone))
    }

    @Test
    fun `all-day event fully before week is not visible`() {
        assertFalse(EventUi.overlapsWeek(allDayEvent("2026-06-05", "2026-06-08"), weekStart, weekEnd, zone))
    }
}
