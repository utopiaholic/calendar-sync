package com.calmerge.app.ui

import com.calmerge.app.data.db.AccountType
import com.calmerge.app.data.db.EventInstanceEntity
import com.calmerge.app.data.db.ConflictMemberRow
import com.calmerge.app.data.db.ResponseStatus
import com.calmerge.app.data.db.ShowAs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class ConflictClusterMapperTest {

    private val zone = ZoneId.of("UTC")

    private fun row(
        clusterId: String,
        id: String,
        startUtc: Long?,
        dedupeGroupId: String? = null,
        startDate: String? = null,
    ) = ConflictMemberRow(
        clusterId = clusterId,
        event = EventInstanceEntity(
            id = id,
            calendarSourceId = "src",
            providerEventId = "p-$id",
            iCalUId = null,
            title = id,
            startUtc = startUtc,
            endUtc = startUtc?.plus(3_600_000),
            isAllDay = startDate != null,
            startDate = startDate,
            endDate = null,
            showAs = ShowAs.BUSY,
            responseStatus = ResponseStatus.ACCEPTED,
            location = null,
            organizer = null,
            lastModifiedUtc = null,
            dedupeGroupId = dedupeGroupId,
        ),
        accountId = "acc",
        accountName = "Test",
        accountColor = 0,
        accountType = AccountType.ICS,
    )

    @Test
    fun `dedupe pair collapses to one member with two copies`() {
        val rows = listOf(
            row("c1", "a", startUtc = 1_000L, dedupeGroupId = "g1"),
            row("c1", "b", startUtc = 1_000L, dedupeGroupId = "g1"),
        )
        val clusters = ConflictClusterMapper.toClusterUi(rows, zone)
        assertEquals(1, clusters.size)
        val members = clusters.single().members
        assertEquals("Dedupe pair should collapse to 1 displayed member", 1, members.size)
        assertEquals("Two copies should be in the pair", 2, members.single().second.size)
    }

    @Test
    fun `solo members pass through unchanged`() {
        val rows = listOf(
            row("c1", "x", startUtc = 1_000L),
            row("c1", "y", startUtc = 2_000L),
        )
        val clusters = ConflictClusterMapper.toClusterUi(rows, zone)
        assertEquals(1, clusters.size)
        val members = clusters.single().members
        assertEquals("Two solo members should each pass through", 2, members.size)
        members.forEach { (_, copies) -> assertEquals(1, copies.size) }
    }

    @Test
    fun `members sorted by earliest start including all-day fallback`() {
        val rows = listOf(
            row("c1", "late", startUtc = 5_000L),
            row("c1", "early", startUtc = 1_000L),
            row("c1", "allday", startUtc = null, startDate = "2026-06-12"),
        )
        val clusters = ConflictClusterMapper.toClusterUi(rows, zone)
        val ids = clusters.single().members.map { (rep, _) -> rep.event.id }
        // allday midnight UTC on 2026-06-12 = 1749686400000 ms — after 5000 ms
        // So order should be: early (1000), late (5000), allday (very large ms)
        assertEquals("early", ids[0])
        assertEquals("late", ids[1])
        assertEquals("allday", ids[2])
    }

    @Test
    fun `clusters sorted by their earliest member start`() {
        val rows = listOf(
            row("c2", "z", startUtc = 9_000L),
            row("c1", "a", startUtc = 1_000L),
            row("c1", "b", startUtc = 3_000L),
        )
        val clusters = ConflictClusterMapper.toClusterUi(rows, zone)
        assertEquals("c1", clusters[0].clusterId)
        assertEquals("c2", clusters[1].clusterId)
    }

    @Test
    fun `endKeyMs reflects the latest member end`() {
        // Two members: start 1000/end 4600, start 3000/end 6600
        val rows = listOf(
            row("c1", "a", startUtc = 1_000L),   // endUtc = 1000 + 3_600_000
            row("c1", "b", startUtc = 3_000L),   // endUtc = 3000 + 3_600_000 (later)
        )
        val cluster = ConflictClusterMapper.toClusterUi(rows, zone).single()
        assertEquals(3_000L + 3_600_000L, cluster.endKeyMs)
    }
}
