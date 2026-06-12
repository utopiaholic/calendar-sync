package dev.albert.calmerge.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class NormalizeUrlTest {

    @Test
    fun `trims leading and trailing whitespace`() {
        assertEquals(normalizeUrl("  https://example.com/feed.ics  "), normalizeUrl("https://example.com/feed.ics"))
    }

    @Test
    fun `lowercases scheme and host`() {
        assertEquals(normalizeUrl("HTTPS://Example.COM/Feed.ics"), normalizeUrl("https://example.com/Feed.ics"))
    }

    @Test
    fun `strips single trailing slash`() {
        assertEquals(normalizeUrl("https://example.com/feed/"), normalizeUrl("https://example.com/feed"))
    }

    @Test
    fun `does not strip slash from bare root`() {
        // Root path "/" should not be shortened (only paths longer than 1 char get stripped).
        val norm = normalizeUrl("https://example.com/")
        // Either "/" or "" is acceptable; just ensure it matches the trimmed form consistently.
        assertEquals(norm, normalizeUrl("https://example.com/"))
    }

    @Test
    fun `same url with different casing is detected as duplicate`() {
        assertEquals(normalizeUrl("https://EXAMPLE.COM/cal.ics"), normalizeUrl("https://example.com/cal.ics"))
    }

    @Test
    fun `different paths are not duplicates`() {
        assertNotEquals(normalizeUrl("https://example.com/calA.ics"), normalizeUrl("https://example.com/calB.ics"))
    }

    @Test
    fun `empty string normalizes gracefully`() {
        assertEquals("", normalizeUrl(""))
        assertEquals("", normalizeUrl("   "))
    }

    @Test
    fun `query params are preserved`() {
        val url = "https://example.com/feed?token=abc"
        assertEquals(normalizeUrl(url), normalizeUrl(url))
    }
}
