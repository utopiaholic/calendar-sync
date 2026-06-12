package com.calmerge.app.sync

import com.calmerge.app.data.db.EventInstanceEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/**
 * End-to-end pipeline tests: IcsParser → Deduper → ConflictDetector.
 *
 * Uses ICS text mirroring the three feeds in testdata/ (worka.ics, workb.ics,
 * personal.ics) so that conflicts visible on-device are also asserted in CI.
 */
class PipelineIntegrationTest {

    private val zone = ZoneId.of("UTC")

    /** Window that covers all events in testdata/. */
    private val window = SyncWindow(
        startUtc = Instant.parse("2026-06-01T00:00:00Z"),
        endUtc = Instant.parse("2026-07-01T00:00:00Z"),
    )

    // -----------------------------------------------------------------------
    // Feed text mirrors testdata/*.ics exactly
    // -----------------------------------------------------------------------

    private val workAIcs = """
        BEGIN:VCALENDAR
        VERSION:2.0
        PRODID:-//CalMerge Test//Work A//EN
        BEGIN:VEVENT
        UID:shared-meeting-001@example.com
        DTSTART:20260612T140000Z
        DTEND:20260612T150000Z
        SUMMARY:Cross-team planning
        LOCATION:Teams
        X-MICROSOFT-CDO-BUSYSTATUS:BUSY
        END:VEVENT
        BEGIN:VEVENT
        UID:worka-only-001@example.com
        DTSTART:20260615T090000Z
        DTEND:20260615T093000Z
        SUMMARY:Work A standup
        X-MICROSOFT-CDO-BUSYSTATUS:BUSY
        END:VEVENT
        END:VCALENDAR
    """.trimIndent()

    private val workBIcs = """
        BEGIN:VCALENDAR
        VERSION:2.0
        PRODID:-//CalMerge Test//Work B//EN
        BEGIN:VEVENT
        UID:shared-meeting-001@example.com
        DTSTART:20260612T140000Z
        DTEND:20260612T150000Z
        SUMMARY:Cross-team planning
        LOCATION:Teams
        X-MICROSOFT-CDO-BUSYSTATUS:BUSY
        END:VEVENT
        BEGIN:VEVENT
        UID:workb-only-001@example.com
        DTSTART:20260616T130000Z
        DTEND:20260616T140000Z
        SUMMARY:Work B retro
        X-MICROSOFT-CDO-BUSYSTATUS:TENTATIVE
        END:VEVENT
        END:VCALENDAR
    """.trimIndent()

    private val personalIcs = """
        BEGIN:VCALENDAR
        VERSION:2.0
        PRODID:-//CalMerge Test//Personal//EN
        BEGIN:VEVENT
        UID:personal-dentist-001@example.com
        DTSTART:20260612T143000Z
        DTEND:20260612T153000Z
        SUMMARY:Dentist appointment
        TRANSP:OPAQUE
        END:VEVENT
        BEGIN:VEVENT
        UID:personal-gym-001@example.com
        DTSTART:20260613T100000Z
        DTEND:20260613T110000Z
        SUMMARY:Gym
        TRANSP:TRANSPARENT
        END:VEVENT
        END:VCALENDAR
    """.trimIndent()

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Simulates SyncCoordinator.recomputeDedupe: assigns a shared dedupeGroupId
     * to every event that Deduper groups together.
     */
    private fun applyDedupe(events: List<EventInstanceEntity>): List<EventInstanceEntity> {
        val mutable = events.map { it.copy(dedupeGroupId = null) }.toMutableList()
        for (group in Deduper.computeGroups(mutable)) {
            val groupId = UUID.randomUUID().toString()
            val ids = group.eventIds.toSet()
            for (i in mutable.indices) {
                if (mutable[i].id in ids) {
                    mutable[i] = mutable[i].copy(dedupeGroupId = groupId)
                }
            }
        }
        return mutable
    }

    // -----------------------------------------------------------------------
    // Tests
    // -----------------------------------------------------------------------

    @Test
    fun `cross-team planning from work A and work B are deduped into one group`() {
        val workA = IcsParser.parse(workAIcs, "src-workA", window)
        val workB = IcsParser.parse(workBIcs, "src-workB", window)
        val all = workA + workB

        val groups = Deduper.computeGroups(all)

        assertEquals("Expected exactly one dedupe group (the shared meeting)", 1, groups.size)
        val groupedIds = groups.single().eventIds.toSet()
        val groupedTitles = all.filter { it.id in groupedIds }.map { it.title }.toSet()
        assertEquals(setOf("Cross-team planning"), groupedTitles)
    }

    @Test
    fun `cross-team planning conflicts with dentist appointment after deduplication`() {
        // The core acceptance test: Work A + Work B have the same meeting at 14:00–15:00.
        // Personal has "Dentist appointment" at 14:30–15:30.
        // After deduplication the shared meeting is one logical event and must still
        // conflict with the distinct personal event.
        val allRaw = IcsParser.parse(workAIcs, "src-workA", window) +
            IcsParser.parse(workBIcs, "src-workB", window) +
            IcsParser.parse(personalIcs, "src-personal", window)

        val allDeduped = applyDedupe(allRaw)
        val clusters = ConflictDetector.detect(allDeduped, zone)

        assertEquals("Expected exactly one conflict cluster", 1, clusters.size)

        val cluster = clusters.single()
        val conflictingEvents = allDeduped.filter { it.id in cluster }
        val conflictingTitles = conflictingEvents.map { it.title }.toSet()

        assertTrue(
            "Cluster must contain Cross-team planning; got $conflictingTitles",
            conflictingTitles.contains("Cross-team planning"),
        )
        assertTrue(
            "Cluster must contain Dentist appointment; got $conflictingTitles",
            conflictingTitles.contains("Dentist appointment"),
        )
    }

    @Test
    fun `both work A and work B copies are included in the conflict cluster`() {
        // FR-22 deep-link requirement: the cluster must carry member IDs from both
        // feeds so both "Open in Outlook" actions are surfaced in the detail sheet.
        val workAEvents = IcsParser.parse(workAIcs, "src-workA", window)
        val workBEvents = IcsParser.parse(workBIcs, "src-workB", window)
        val personalEvents = IcsParser.parse(personalIcs, "src-personal", window)
        val allDeduped = applyDedupe(workAEvents + workBEvents + personalEvents)

        val clusters = ConflictDetector.detect(allDeduped, zone)
        assertEquals(1, clusters.size)

        val clusterIds = clusters.single().toSet()
        val sourcesInCluster = allDeduped
            .filter { it.id in clusterIds }
            .map { it.calendarSourceId }
            .toSet()

        assertTrue("Cluster must include Work A", sourcesInCluster.contains("src-workA"))
        assertTrue("Cluster must include Work B", sourcesInCluster.contains("src-workB"))
        assertTrue("Cluster must include personal", sourcesInCluster.contains("src-personal"))
    }

    @Test
    fun `gym (free status) does not appear in any conflict`() {
        // Spec acceptance criterion 3: TRANSP:TRANSPARENT → FREE → not a conflict.
        val allDeduped = applyDedupe(
            IcsParser.parse(workAIcs, "src-workA", window) +
                IcsParser.parse(workBIcs, "src-workB", window) +
                IcsParser.parse(personalIcs, "src-personal", window),
        )

        val clusters = ConflictDetector.detect(allDeduped, zone)
        val allConflictIds = clusters.flatten().toSet()
        val conflictingTitles = allDeduped.filter { it.id in allConflictIds }.map { it.title }

        assertTrue(
            "Gym (FREE) must not appear in any conflict",
            conflictingTitles.none { it == "Gym" },
        )
    }

    @Test
    fun `solo events on different days produce no conflicts`() {
        // Work A standup (Jun 15) and Work B retro (Jun 16) do not overlap.
        val workA = IcsParser.parse(workAIcs, "src-workA", window)
        val workB = IcsParser.parse(workBIcs, "src-workB", window)
        val allDeduped = applyDedupe(workA + workB)

        // Exclude the deduped shared meeting so only solo events remain.
        val soloOnly = allDeduped.filter { it.dedupeGroupId == null }
        val clusters = ConflictDetector.detect(soloOnly, zone)

        assertTrue("No conflicts expected among solo events", clusters.isEmpty())
    }

    @Test
    fun `work B retro (tentative) would conflict with a concurrent busy personal event`() {
        // FR-14: TENTATIVE counts as a conflicting status.
        val retroIcs = """
            BEGIN:VCALENDAR
            VERSION:2.0
            PRODID:-//Test//EN
            BEGIN:VEVENT
            UID:workb-only-001@example.com
            DTSTART:20260616T130000Z
            DTEND:20260616T140000Z
            SUMMARY:Work B retro
            X-MICROSOFT-CDO-BUSYSTATUS:TENTATIVE
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()
        val concurrentPersonalIcs = """
            BEGIN:VCALENDAR
            VERSION:2.0
            PRODID:-//Test//EN
            BEGIN:VEVENT
            UID:personal-call-001@example.com
            DTSTART:20260616T133000Z
            DTEND:20260616T143000Z
            SUMMARY:Personal call
            TRANSP:OPAQUE
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val events = IcsParser.parse(retroIcs, "src-workB", window) +
            IcsParser.parse(concurrentPersonalIcs, "src-personal", window)
        val clusters = ConflictDetector.detect(events, zone)

        assertEquals(1, clusters.size)
        val titles = events.filter { it.id in clusters.single() }.map { it.title }.toSet()
        assertEquals(setOf("Work B retro", "Personal call"), titles)
    }
}
