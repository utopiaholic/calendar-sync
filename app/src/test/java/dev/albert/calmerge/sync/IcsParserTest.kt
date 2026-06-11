package dev.albert.calmerge.sync

import dev.albert.calmerge.data.db.ShowAs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class IcsParserTest {

    private val window = SyncWindow(
        startUtc = Instant.parse("2026-06-01T00:00:00Z"),
        endUtc = Instant.parse("2026-07-01T00:00:00Z"),
    )

    private fun ics(vararg events: String) = buildString {
        appendLine("BEGIN:VCALENDAR")
        appendLine("VERSION:2.0")
        appendLine("PRODID:-//Test//Test//EN")
        events.forEach { appendLine(it.trimIndent()) }
        appendLine("END:VCALENDAR")
    }

    @Test
    fun `simple timed event parses with UTC times`() {
        val events = IcsParser.parse(
            ics(
                """
                BEGIN:VEVENT
                UID:simple-1
                DTSTART:20260615T090000Z
                DTEND:20260615T100000Z
                SUMMARY:Planning
                END:VEVENT
                """,
            ),
            "src", window,
        )
        assertEquals(1, events.size)
        assertEquals(Instant.parse("2026-06-15T09:00:00Z").toEpochMilli(), events[0].startUtc)
        assertEquals(Instant.parse("2026-06-15T10:00:00Z").toEpochMilli(), events[0].endUtc)
        assertEquals("Planning", events[0].title)
        assertEquals(ShowAs.BUSY, events[0].showAs)
    }

    @Test
    fun `missing DTEND defaults to zero duration for timed events`() {
        // Spec §8 case 7.
        val events = IcsParser.parse(
            ics(
                """
                BEGIN:VEVENT
                UID:nodtend-1
                DTSTART:20260615T090000Z
                SUMMARY:Ping
                END:VEVENT
                """,
            ),
            "src", window,
        )
        assertEquals(1, events.size)
        assertEquals(events[0].startUtc, events[0].endUtc)
    }

    @Test
    fun `DURATION is honored when DTEND is absent`() {
        val events = IcsParser.parse(
            ics(
                """
                BEGIN:VEVENT
                UID:duration-1
                DTSTART:20260615T090000Z
                DURATION:PT45M
                SUMMARY:Workshop
                END:VEVENT
                """,
            ),
            "src", window,
        )
        assertEquals(1, events.size)
        assertEquals(45L * 60_000, events[0].endUtc!! - events[0].startUtc!!)
    }

    @Test
    fun `weekly recurrence expands to instances inside the window only`() {
        val events = IcsParser.parse(
            ics(
                """
                BEGIN:VEVENT
                UID:recur-1
                DTSTART:20260601T120000Z
                DTEND:20260601T123000Z
                RRULE:FREQ=WEEKLY;COUNT=10
                SUMMARY:Weekly sync
                END:VEVENT
                """,
            ),
            "src", window,
        )
        // Mondays Jun 1..29 fall in the window: 5 instances (Jul+ excluded by window).
        assertEquals(5, events.size)
        assertTrue(events.all { it.title == "Weekly sync" })
        assertEquals(
            Instant.parse("2026-06-08T12:00:00Z").toEpochMilli(),
            events.sortedBy { it.startUtc }[1].startUtc,
        )
    }

    @Test
    fun `moved recurrence instance shows new time with no phantom at the old slot`() {
        // Spec §8 case 2 / acceptance criterion 4.
        val events = IcsParser.parse(
            ics(
                """
                BEGIN:VEVENT
                UID:recur-2
                DTSTART:20260601T120000Z
                DTEND:20260601T123000Z
                RRULE:FREQ=WEEKLY;COUNT=4
                SUMMARY:Weekly sync
                END:VEVENT
                """,
                """
                BEGIN:VEVENT
                UID:recur-2
                RECURRENCE-ID:20260608T120000Z
                DTSTART:20260608T150000Z
                DTEND:20260608T153000Z
                SUMMARY:Weekly sync (moved)
                END:VEVENT
                """,
            ),
            "src", window,
        )
        assertEquals(4, events.size)
        val starts = events.map { it.startUtc }
        assertTrue(starts.contains(Instant.parse("2026-06-08T15:00:00Z").toEpochMilli()))
        // The original Jun 8 12:00 slot must not appear.
        assertTrue(!starts.contains(Instant.parse("2026-06-08T12:00:00Z").toEpochMilli()))
    }

    @Test
    fun `all-day event stores date-only fields`() {
        val events = IcsParser.parse(
            ics(
                """
                BEGIN:VEVENT
                UID:allday-1
                DTSTART;VALUE=DATE:20260620
                DTEND;VALUE=DATE:20260621
                SUMMARY:Conference
                END:VEVENT
                """,
            ),
            "src", window,
        )
        assertEquals(1, events.size)
        assertTrue(events[0].isAllDay)
        assertEquals("2026-06-20", events[0].startDate)
        assertEquals(null, events[0].startUtc)
    }

    @Test
    fun `outlook busy status property maps to showAs`() {
        val events = IcsParser.parse(
            ics(
                """
                BEGIN:VEVENT
                UID:busystatus-1
                DTSTART:20260615T090000Z
                DTEND:20260615T100000Z
                SUMMARY:Focus time
                X-MICROSOFT-CDO-BUSYSTATUS:OOF
                END:VEVENT
                """,
            ),
            "src", window,
        )
        assertEquals(ShowAs.OOF, events[0].showAs)
    }

    @Test
    fun `transparent event maps to FREE`() {
        val events = IcsParser.parse(
            ics(
                """
                BEGIN:VEVENT
                UID:transp-1
                DTSTART:20260615T090000Z
                DTEND:20260615T100000Z
                TRANSP:TRANSPARENT
                SUMMARY:FYI block
                END:VEVENT
                """,
            ),
            "src", window,
        )
        assertEquals(ShowAs.FREE, events[0].showAs)
    }

    @Test
    fun `cancelled events are skipped`() {
        val events = IcsParser.parse(
            ics(
                """
                BEGIN:VEVENT
                UID:cancelled-1
                DTSTART:20260615T090000Z
                DTEND:20260615T100000Z
                STATUS:CANCELLED
                SUMMARY:Old meeting
                END:VEVENT
                """,
            ),
            "src", window,
        )
        assertTrue(events.isEmpty())
    }

    @Test
    fun `events outside the window are excluded`() {
        val events = IcsParser.parse(
            ics(
                """
                BEGIN:VEVENT
                UID:outside-1
                DTSTART:20261215T090000Z
                DTEND:20261215T100000Z
                SUMMARY:Far future
                END:VEVENT
                """,
            ),
            "src", window,
        )
        assertTrue(events.isEmpty())
    }
}
