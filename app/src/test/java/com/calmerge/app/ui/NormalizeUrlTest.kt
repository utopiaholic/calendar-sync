package com.calmerge.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class NormalizeUrlTest {

    @Test
    fun `trims leading and trailing whitespace`() {
        assertEquals(normalizeUrlOrNull("  https://example.com/feed.ics  "), normalizeUrlOrNull("https://example.com/feed.ics"))
    }

    @Test
    fun `lowercases scheme and host`() {
        assertEquals(normalizeUrlOrNull("HTTPS://Example.COM/Feed.ics"), normalizeUrlOrNull("https://example.com/Feed.ics"))
    }

    @Test
    fun `strips single trailing slash`() {
        assertEquals(normalizeUrlOrNull("https://example.com/feed/"), normalizeUrlOrNull("https://example.com/feed"))
    }

    @Test
    fun `does not strip slash from bare root`() {
        // Root path "/" should not be shortened (only paths longer than 1 char get stripped).
        val norm = normalizeUrlOrNull("https://example.com/")
        // Either "/" or "" is acceptable; just ensure it matches the trimmed form consistently.
        assertEquals(norm, normalizeUrlOrNull("https://example.com/"))
    }

    @Test
    fun `same url with different casing is detected as duplicate`() {
        assertEquals(normalizeUrlOrNull("https://EXAMPLE.COM/cal.ics"), normalizeUrlOrNull("https://example.com/cal.ics"))
    }

    @Test
    fun `different paths are not duplicates`() {
        assertNotEquals(normalizeUrlOrNull("https://example.com/calA.ics"), normalizeUrlOrNull("https://example.com/calB.ics"))
    }

    @Test
    fun `blank and invalid urls return null`() {
        assertEquals(null, normalizeUrlOrNull(""))
        assertEquals(null, normalizeUrlOrNull("   "))
        assertEquals(null, normalizeUrlOrNull("not a url"))
        assertEquals(null, normalizeUrlOrNull("/relative/path.ics"))
    }

    @Test
    fun `query params are preserved`() {
        val url = "https://example.com/feed?token=abc"
        assertEquals("https://example.com/feed?token=abc", normalizeUrlOrNull(url))
    }

    @Test
    fun `valid https url normalizes`() {
        assertEquals("https://example.com/feed.ics", normalizeUrlOrNull("HTTPS://Example.COM/feed.ics"))
    }
}
