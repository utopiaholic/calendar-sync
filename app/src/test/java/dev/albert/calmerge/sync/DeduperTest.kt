package dev.albert.calmerge.sync

import dev.albert.calmerge.data.db.EventInstanceEntity
import dev.albert.calmerge.data.db.ResponseStatus
import dev.albert.calmerge.data.db.ShowAs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeduperTest {

    private fun event(
        id: String,
        sourceId: String,
        iCalUId: String? = null,
        title: String = "Team sync",
        startUtc: Long? = 1_000_000L,
        endUtc: Long? = 2_000_000L,
    ) = EventInstanceEntity(
        id = id,
        calendarSourceId = sourceId,
        providerEventId = "p-$id",
        iCalUId = iCalUId,
        title = title,
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

    @Test
    fun `same iCalUId across two accounts forms one group`() {
        // Spec §8 case 3: identical meeting invited to both work addresses.
        val groups = Deduper.computeGroups(
            listOf(
                event("a", sourceId = "workA", iCalUId = "uid-123"),
                event("b", sourceId = "workB", iCalUId = "uid-123"),
            ),
        )
        assertEquals(1, groups.size)
        assertEquals(setOf("a", "b"), groups.single().eventIds.toSet())
    }

    @Test
    fun `fuzzy match on title and times when iCalUId is missing`() {
        val groups = Deduper.computeGroups(
            listOf(
                event("a", sourceId = "workA", title = "Team  Sync"),
                event("b", sourceId = "workB", title = "team sync"),
            ),
        )
        assertEquals(1, groups.size)
    }

    @Test
    fun `same calendar never dedupes against itself`() {
        val groups = Deduper.computeGroups(
            listOf(
                event("a", sourceId = "workA", iCalUId = "uid-123"),
                event("b", sourceId = "workA", iCalUId = "uid-123"),
            ),
        )
        assertTrue(groups.isEmpty())
    }

    @Test
    fun `different times do not fuzzy-match`() {
        val groups = Deduper.computeGroups(
            listOf(
                event("a", sourceId = "workA", startUtc = 1_000_000L),
                event("b", sourceId = "workB", startUtc = 3_000_000L),
            ),
        )
        assertTrue(groups.isEmpty())
    }

    @Test
    fun `different titles do not fuzzy-match`() {
        val groups = Deduper.computeGroups(
            listOf(
                event("a", sourceId = "workA", title = "Team sync"),
                event("b", sourceId = "workB", title = "1:1"),
            ),
        )
        assertTrue(groups.isEmpty())
    }
}
