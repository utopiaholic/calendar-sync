package dev.albert.calmerge.sync

import dev.albert.calmerge.data.db.EventInstanceEntity
import dev.albert.calmerge.data.db.ResponseStatus
import dev.albert.calmerge.data.db.ShowAs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class ConflictDetectorTest {

    private val zone = ZoneId.of("UTC")

    private fun ts(iso: String) = Instant.parse(iso).toEpochMilli()

    private fun event(
        id: String,
        start: String,
        end: String,
        sourceId: String = "src1",
        showAs: ShowAs = ShowAs.BUSY,
        response: ResponseStatus = ResponseStatus.ACCEPTED,
        dedupeGroupId: String? = null,
    ) = EventInstanceEntity(
        id = id,
        calendarSourceId = sourceId,
        providerEventId = "p-$id",
        iCalUId = null,
        title = id,
        startUtc = ts(start),
        endUtc = ts(end),
        isAllDay = false,
        startDate = null,
        endDate = null,
        showAs = showAs,
        responseStatus = response,
        location = null,
        organizer = null,
        lastModifiedUtc = null,
        dedupeGroupId = dedupeGroupId,
    )

    private fun allDayEvent(
        id: String,
        startDate: String,
        endDate: String,
        showAs: ShowAs = ShowAs.BUSY,
    ) = EventInstanceEntity(
        id = id,
        calendarSourceId = "src1",
        providerEventId = "p-$id",
        iCalUId = null,
        title = id,
        startUtc = null,
        endUtc = null,
        isAllDay = true,
        startDate = startDate,
        endDate = endDate,
        showAs = showAs,
        responseStatus = ResponseStatus.ACCEPTED,
        location = null,
        organizer = null,
        lastModifiedUtc = null,
    )

    @Test
    fun `two overlapping busy events conflict`() {
        val clusters = ConflictDetector.detect(
            listOf(
                event("a", "2026-06-12T14:00:00Z", "2026-06-12T15:00:00Z", sourceId = "work"),
                event("b", "2026-06-12T14:30:00Z", "2026-06-12T15:30:00Z", sourceId = "personal"),
            ),
            zone,
        )
        assertEquals(1, clusters.size)
        assertEquals(setOf("a", "b"), clusters.single().toSet())
    }

    @Test
    fun `free event overlapping busy event is NOT a conflict`() {
        // Spec acceptance criterion 3.
        val clusters = ConflictDetector.detect(
            listOf(
                event("a", "2026-06-12T14:00:00Z", "2026-06-12T15:00:00Z"),
                event("b", "2026-06-12T14:00:00Z", "2026-06-12T15:00:00Z", showAs = ShowAs.FREE),
            ),
            zone,
        )
        assertTrue(clusters.isEmpty())
    }

    @Test
    fun `declined event is excluded from conflicts`() {
        // Spec §8 case 4.
        val clusters = ConflictDetector.detect(
            listOf(
                event("a", "2026-06-12T14:00:00Z", "2026-06-12T15:00:00Z"),
                event("b", "2026-06-12T14:00:00Z", "2026-06-12T15:00:00Z", response = ResponseStatus.DECLINED),
            ),
            zone,
        )
        assertTrue(clusters.isEmpty())
    }

    @Test
    fun `tentative and oof events do conflict`() {
        val clusters = ConflictDetector.detect(
            listOf(
                event("a", "2026-06-12T14:00:00Z", "2026-06-12T15:00:00Z", showAs = ShowAs.TENTATIVE),
                event("b", "2026-06-12T14:30:00Z", "2026-06-12T15:30:00Z", showAs = ShowAs.OOF),
            ),
            zone,
        )
        assertEquals(1, clusters.size)
    }

    @Test
    fun `double booking within a single calendar is detected`() {
        // FR-16.
        val clusters = ConflictDetector.detect(
            listOf(
                event("a", "2026-06-12T09:00:00Z", "2026-06-12T10:00:00Z", sourceId = "same"),
                event("b", "2026-06-12T09:30:00Z", "2026-06-12T10:30:00Z", sourceId = "same"),
            ),
            zone,
        )
        assertEquals(1, clusters.size)
    }

    @Test
    fun `chained overlaps form one cluster of three`() {
        // FR-18: A overlaps B, B overlaps C, A does not overlap C.
        val clusters = ConflictDetector.detect(
            listOf(
                event("a", "2026-06-12T09:00:00Z", "2026-06-12T10:00:00Z"),
                event("b", "2026-06-12T09:45:00Z", "2026-06-12T11:00:00Z"),
                event("c", "2026-06-12T10:30:00Z", "2026-06-12T11:30:00Z"),
            ),
            zone,
        )
        assertEquals(1, clusters.size)
        assertEquals(setOf("a", "b", "c"), clusters.single().toSet())
    }

    @Test
    fun `back-to-back events do not conflict`() {
        val clusters = ConflictDetector.detect(
            listOf(
                event("a", "2026-06-12T09:00:00Z", "2026-06-12T10:00:00Z"),
                event("b", "2026-06-12T10:00:00Z", "2026-06-12T11:00:00Z"),
            ),
            zone,
        )
        assertTrue(clusters.isEmpty())
    }

    @Test
    fun `dedupe group members never conflict with each other`() {
        // The same meeting seen from both work feeds is one logical event.
        val clusters = ConflictDetector.detect(
            listOf(
                event("a", "2026-06-12T14:00:00Z", "2026-06-12T15:00:00Z", sourceId = "workA", dedupeGroupId = "g1"),
                event("b", "2026-06-12T14:00:00Z", "2026-06-12T15:00:00Z", sourceId = "workB", dedupeGroupId = "g1"),
            ),
            zone,
        )
        assertTrue(clusters.isEmpty())
    }

    @Test
    fun `dedupe group conflicts as one logical event and reports all member ids`() {
        val clusters = ConflictDetector.detect(
            listOf(
                event("a", "2026-06-12T14:00:00Z", "2026-06-12T15:00:00Z", sourceId = "workA", dedupeGroupId = "g1"),
                event("b", "2026-06-12T14:00:00Z", "2026-06-12T15:00:00Z", sourceId = "workB", dedupeGroupId = "g1"),
                event("c", "2026-06-12T14:30:00Z", "2026-06-12T15:30:00Z", sourceId = "personal"),
            ),
            zone,
        )
        assertEquals(1, clusters.size)
        assertEquals(setOf("a", "b", "c"), clusters.single().toSet())
    }

    @Test
    fun `all-day event does not conflict with timed event`() {
        // FR-15 default.
        val clusters = ConflictDetector.detect(
            listOf(
                allDayEvent("allday", "2026-06-12", "2026-06-13"),
                event("timed", "2026-06-12T14:00:00Z", "2026-06-12T15:00:00Z"),
            ),
            zone,
        )
        assertTrue(clusters.isEmpty())
    }

    @Test
    fun `two overlapping busy all-day events conflict with each other`() {
        val clusters = ConflictDetector.detect(
            listOf(
                allDayEvent("x", "2026-06-12", "2026-06-14"),
                allDayEvent("y", "2026-06-13", "2026-06-15"),
            ),
            zone,
        )
        assertEquals(1, clusters.size)
    }

    @Test
    fun `disjoint events produce no clusters and separate overlaps produce separate clusters`() {
        val clusters = ConflictDetector.detect(
            listOf(
                event("a1", "2026-06-12T09:00:00Z", "2026-06-12T10:00:00Z"),
                event("a2", "2026-06-12T09:30:00Z", "2026-06-12T10:00:00Z"),
                event("b1", "2026-06-13T09:00:00Z", "2026-06-13T10:00:00Z"),
                event("b2", "2026-06-13T09:30:00Z", "2026-06-13T10:00:00Z"),
                event("solo", "2026-06-14T09:00:00Z", "2026-06-14T10:00:00Z"),
            ),
            zone,
        )
        assertEquals(2, clusters.size)
        assertEquals(setOf("a1", "a2"), clusters[0].toSet())
        assertEquals(setOf("b1", "b2"), clusters[1].toSet())
    }
}
