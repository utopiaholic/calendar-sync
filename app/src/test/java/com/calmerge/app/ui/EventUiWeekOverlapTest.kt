package com.calmerge.app.ui

import com.calmerge.app.data.db.AccountType
import com.calmerge.app.data.db.EventInstanceEntity
import com.calmerge.app.data.db.MergedEvent
import com.calmerge.app.data.db.ResponseStatus
import com.calmerge.app.data.db.ShowAs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class EventUiWeekOverlapTest {

    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 6, 12)
    private val startOfToday = today.atStartOfDay(zone).toInstant().toEpochMilli()

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

    // ---- startOfTodayMs ----

    @Test
    fun `startOfTodayMs returns midnight UTC`() {
        val ms = EventUi.startOfTodayMs(zone, today)
        assertEquals(startOfToday, ms)
    }

    // ---- eventEndMs for timed events ----

    @Test
    fun `eventEndMs returns endUtc for timed event`() {
        val start = startOfToday
        val end = startOfToday + 3_600_000L
        assertEquals(end, EventUi.eventEndMs(timedEvent(start, end), zone))
    }

    @Test
    fun `eventEndMs falls back to startUtc when endUtc absent`() {
        val e = EventInstanceEntity(
            id = "e", calendarSourceId = "s", providerEventId = "p", iCalUId = null,
            title = "T", startUtc = startOfToday, endUtc = null, isAllDay = false,
            startDate = null, endDate = null, showAs = ShowAs.BUSY,
            responseStatus = ResponseStatus.ACCEPTED, location = null,
            organizer = null, lastModifiedUtc = null,
        )
        assertEquals(startOfToday, EventUi.eventEndMs(e, zone))
    }

    // ---- eventEndMs for all-day events ----

    @Test
    fun `eventEndMs returns exclusive end date millis for all-day event`() {
        val event = allDayEvent("2026-06-12", "2026-06-13")
        val expected = LocalDate.of(2026, 6, 13).atStartOfDay(zone).toInstant().toEpochMilli()
        assertEquals(expected, EventUi.eventEndMs(event, zone))
    }

    @Test
    fun `eventEndMs defaults to start plus one day when endDate absent`() {
        val e = EventInstanceEntity(
            id = "e", calendarSourceId = "s", providerEventId = "p", iCalUId = null,
            title = "T", startUtc = null, endUtc = null, isAllDay = true,
            startDate = "2026-06-12", endDate = null, showAs = ShowAs.BUSY,
            responseStatus = ResponseStatus.ACCEPTED, location = null,
            organizer = null, lastModifiedUtc = null,
        )
        val expected = LocalDate.of(2026, 6, 13).atStartOfDay(zone).toInstant().toEpochMilli()
        assertEquals(expected, EventUi.eventEndMs(e, zone))
    }

    // ---- isPast ----

    @Test
    fun `event ending yesterday is past`() {
        val yesterday = today.minusDays(1)
        val end = yesterday.atStartOfDay(zone).toInstant().toEpochMilli() + 3_600_000L
        val start = end - 3_600_000L
        assertTrue(EventUi.isPast(timedEvent(start, end), zone, startOfToday))
    }

    @Test
    fun `event ending exactly at start of today is past`() {
        // A timed event ending at midnight (startOfToday) is fully over — classified as past.
        assertTrue(EventUi.isPast(timedEvent(startOfToday - 3_600_000L, startOfToday), zone, startOfToday))
    }

    @Test
    fun `event starting and ending today is not past`() {
        assertFalse(EventUi.isPast(timedEvent(startOfToday, startOfToday + 3_600_000L), zone, startOfToday))
    }

    @Test
    fun `event starting tomorrow is not past`() {
        val tomorrowStart = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        assertFalse(EventUi.isPast(timedEvent(tomorrowStart, tomorrowStart + 3_600_000L), zone, startOfToday))
    }

    @Test
    fun `all-day event on yesterday is past`() {
        assertTrue(EventUi.isPast(allDayEvent("2026-06-11", "2026-06-12"), zone, startOfToday))
    }

    @Test
    fun `all-day event on today is not past`() {
        assertFalse(EventUi.isPast(allDayEvent("2026-06-12", "2026-06-13"), zone, startOfToday))
    }

    @Test
    fun `multi-day event spanning today is not past`() {
        // Started yesterday, ends tomorrow — ongoing, should be Upcoming.
        assertFalse(EventUi.isPast(allDayEvent("2026-06-11", "2026-06-14"), zone, startOfToday))
    }
}
