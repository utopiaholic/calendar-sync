package com.calmerge.app.sync

import com.calmerge.app.data.db.ResponseStatus
import com.calmerge.app.data.db.ShowAs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalCalendarMapperTest {

    // 2026-06-12 14:00 UTC = 1749736800000 ms
    private val begin = 1749736800000L
    // 2026-06-12 15:00 UTC
    private val end = 1749740400000L

    private fun entity(
        eventId: Long = 1L,
        begin: Long = this.begin,
        end: Long = this.end,
        allDay: Boolean = false,
        title: String? = "Team sync",
        description: String? = null,
        location: String? = null,
        availability: Int = AVAILABILITY_BUSY,
        attendeeStatus: Int = ATTENDEE_ACCEPTED,
        organizer: String? = null,
        uid: String? = null,
    ) = mapToEntity(
        calendarSourceId = "src-1",
        eventId = eventId,
        begin = begin,
        end = end,
        allDay = allDay,
        title = title,
        description = description,
        location = location,
        availabilityInt = availability,
        attendeeStatusInt = attendeeStatus,
        organizer = organizer,
        uid2445 = uid,
    )

    @Test
    fun `timed event stores UTC millis and no date strings`() {
        val e = entity()
        assertEquals(begin, e.startUtc)
        assertEquals(end, e.endUtc)
        assertNull(e.startDate)
        assertNull(e.endDate)
        assertEquals(false, e.isAllDay)
    }

    @Test
    fun `single-day all-day event stores ISO date strings and null UTC`() {
        // All-day: BEGIN = 2026-06-12T00:00:00Z, END = 2026-06-13T00:00:00Z (exclusive)
        val dayBegin = 1781222400000L  // 2026-06-12T00:00:00Z
        val dayEnd   = 1781308800000L  // 2026-06-13T00:00:00Z
        val e = entity(begin = dayBegin, end = dayEnd, allDay = true)
        assertNull(e.startUtc)
        assertNull(e.endUtc)
        assertEquals("2026-06-12", e.startDate)
        assertEquals("2026-06-13", e.endDate)
        assertEquals(true, e.isAllDay)
    }

    @Test
    fun `multi-day all-day event end is exclusive and not bumped`() {
        val dayBegin = 1781222400000L  // 2026-06-12T00:00:00Z
        val dayEnd   = 1781481600000L  // 2026-06-15T00:00:00Z  (3-day span, end exclusive)
        val e = entity(begin = dayBegin, end = dayEnd, allDay = true)
        assertEquals("2026-06-12", e.startDate)
        assertEquals("2026-06-15", e.endDate)
    }

    @Test
    fun `all-day event with same begin and end gets end bumped by one day`() {
        // Some providers return BEGIN == END for single-day all-day events.
        val day = 1781222400000L  // 2026-06-12T00:00:00Z
        val e = entity(begin = day, end = day, allDay = true)
        assertEquals("2026-06-12", e.startDate)
        assertEquals("2026-06-13", e.endDate)
    }

    @Test
    fun `availability int maps to ShowAs correctly`() {
        assertEquals(ShowAs.BUSY, entity(availability = AVAILABILITY_BUSY).showAs)
        assertEquals(ShowAs.FREE, entity(availability = AVAILABILITY_FREE).showAs)
        assertEquals(ShowAs.TENTATIVE, entity(availability = AVAILABILITY_TENTATIVE).showAs)
        // Unknown int defaults to BUSY
        assertEquals(ShowAs.BUSY, entity(availability = 99).showAs)
    }

    @Test
    fun `attendee status maps to ResponseStatus correctly`() {
        assertEquals(ResponseStatus.ACCEPTED, entity(attendeeStatus = ATTENDEE_ACCEPTED).responseStatus)
        assertEquals(ResponseStatus.DECLINED, entity(attendeeStatus = ATTENDEE_DECLINED).responseStatus)
        assertEquals(ResponseStatus.TENTATIVE, entity(attendeeStatus = ATTENDEE_TENTATIVE).responseStatus)
        assertEquals(ResponseStatus.NOT_RESPONDED, entity(attendeeStatus = ATTENDEE_INVITED).responseStatus)
        assertEquals(ResponseStatus.UNKNOWN, entity(attendeeStatus = ATTENDEE_NONE).responseStatus)
    }

    @Test
    fun `iCalUId is passed through to iCalUId field`() {
        val uid = "meeting-uid-001@example.com"
        val e = entity(uid = uid)
        assertEquals(uid, e.iCalUId)
    }

    @Test
    fun `null iCalUId is preserved as null`() {
        assertNull(entity(uid = null).iCalUId)
    }

    @Test
    fun `recurring occurrence providerEventId is unique per occurrence`() {
        // Two instances of the same event (same EVENT_ID, different BEGIN)
        val begin2 = begin + 7 * 24 * 3600 * 1000L
        val e1 = entity(eventId = 42L, begin = begin)
        val e2 = entity(eventId = 42L, begin = begin2)
        assertNotEquals(e1.providerEventId, e2.providerEventId)
        assertEquals("42/$begin", e1.providerEventId)
        assertEquals("42/$begin2", e2.providerEventId)
    }

    @Test
    fun `entity id is deterministic for same source and occurrence`() {
        val e1 = entity(eventId = 1L, begin = begin)
        val e2 = entity(eventId = 1L, begin = begin)
        assertEquals(e1.id, e2.id)
    }

    @Test
    fun `entity id differs across different calendar sources`() {
        val e1 = mapToEntity(
            calendarSourceId = "src-A", eventId = 1L, begin = begin, end = end,
            allDay = false, title = "T", description = null, location = null,
            availabilityInt = AVAILABILITY_BUSY, attendeeStatusInt = ATTENDEE_ACCEPTED,
            organizer = null, uid2445 = null,
        )
        val e2 = mapToEntity(
            calendarSourceId = "src-B", eventId = 1L, begin = begin, end = end,
            allDay = false, title = "T", description = null, location = null,
            availabilityInt = AVAILABILITY_BUSY, attendeeStatusInt = ATTENDEE_ACCEPTED,
            organizer = null, uid2445 = null,
        )
        assertNotEquals(e1.id, e2.id)
    }
}
