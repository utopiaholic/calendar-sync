package com.calmerge.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarProviderTest {

    @Test
    fun `microsoft provider matches known calendar hosts`() {
        assertTrue(CalendarProvider.MICROSOFT.matchesAny(listOf("outlook.office365.com")))
        assertTrue(CalendarProvider.MICROSOFT.matchesAny(listOf("calendar.microsoft.com")))
    }

    @Test
    fun `google provider matches known calendar hosts`() {
        assertTrue(CalendarProvider.GOOGLE.matchesAny(listOf("calendar.google.com")))
        assertTrue(CalendarProvider.GOOGLE.matchesAny(listOf("www.googleapis.com")))
    }

    @Test
    fun `providers do not match unrelated hosts`() {
        assertFalse(CalendarProvider.MICROSOFT.matchesAny(listOf("example.com")))
        assertFalse(CalendarProvider.GOOGLE.matchesAny(listOf("example.com")))
    }
}
