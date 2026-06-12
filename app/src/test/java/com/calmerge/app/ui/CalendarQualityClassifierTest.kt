package com.calmerge.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarQualityClassifierTest {

    @Test
    fun `empty event list returns no upcoming events`() {
        assertEquals("No upcoming events found", classifyCalendarQuality(emptyList(), 0))
    }

    @Test
    fun `count zero returns no upcoming regardless of title list`() {
        assertEquals("No upcoming events found", classifyCalendarQuality(listOf("Meeting"), 0))
    }

    @Test
    fun `meaningful titles returns details available`() {
        val result = classifyCalendarQuality(listOf("Team sync", "Dentist"), 2)
        assertTrue(result.contains("Details available"))
        assertTrue(result.startsWith("2 upcoming"))
    }

    @Test
    fun `all blank titles returns busy only`() {
        val result = classifyCalendarQuality(listOf("", null, ""), 3)
        assertTrue(result.contains("Busy only"))
        assertTrue(result.startsWith("3 upcoming"))
    }

    @Test
    fun `generic busy title returns busy only`() {
        val result = classifyCalendarQuality(listOf("Busy", "busy", "BUSY"), 3)
        assertTrue(result.contains("Busy only"))
    }

    @Test
    fun `mixed generic and real titles returns details available`() {
        val result = classifyCalendarQuality(listOf("Busy", "Team sync"), 2)
        assertTrue(result.contains("Details available"))
    }

    @Test
    fun `tentative and free generic titles count as busy only`() {
        val result = classifyCalendarQuality(listOf("Tentative", "free", "Out of Office"), 3)
        assertTrue(result.contains("Busy only"))
    }

    @Test
    fun `null titles list with non-zero count returns busy only`() {
        val result = classifyCalendarQuality(listOf(null, null), 2)
        assertTrue(result.contains("Busy only"))
    }
}
