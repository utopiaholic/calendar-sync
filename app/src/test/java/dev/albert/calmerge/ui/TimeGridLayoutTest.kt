package dev.albert.calmerge.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class TimeGridLayoutTest {

    private val utc = ZoneId.of("UTC")

    private fun ts(iso: String) = Instant.parse(iso).toEpochMilli()

    private fun timed(
        id: String,
        start: String,
        end: String,
    ) = TimeGridLayout.InputEvent(
        id = id,
        startUtc = ts(start),
        endUtc = ts(end),
        isAllDay = false,
        startDate = null,
        endDate = null,
    )

    private fun allDay(
        id: String,
        startDate: String,
        endDateExclusive: String,
    ) = TimeGridLayout.InputEvent(
        id = id,
        startUtc = null,
        endUtc = null,
        isAllDay = true,
        startDate = startDate,
        endDate = endDateExclusive,
    )

    private val monday = LocalDate.of(2024, 6, 10)   // a Monday
    private val weekDates = (0..6).map { monday.plusDays(it.toLong()) }

    // ---- No events ----

    @Test fun emptyInput() {
        val result = TimeGridLayout.layout(emptyList(), weekDates, utc)
        assertTrue(result.timedSlots.isEmpty())
        assertTrue(result.allDaySlots.isEmpty())
    }

    // ---- Single non-overlapping event ----

    @Test fun singleTimedEvent_noOverlap() {
        val events = listOf(timed("A", "2024-06-10T09:00:00Z", "2024-06-10T10:00:00Z"))
        val result = TimeGridLayout.layout(events, weekDates, utc)
        assertEquals(1, result.timedSlots.size)
        val slot = result.timedSlots[0]
        assertEquals("A", slot.eventId)
        assertEquals(monday, slot.date)
        assertEquals(0, slot.column)
        assertEquals(1, slot.columnCount)
        // 9:00 / 24h = 0.375
        assertEquals(0.375f, slot.startFraction, 0.001f)
        // 10:00 / 24h = 0.4167
        assertEquals(10f / 24f, slot.endFraction, 0.001f)
    }

    // ---- Two overlapping events get separate columns ----

    @Test fun twoOverlapping_getDistinctColumns() {
        val events = listOf(
            timed("A", "2024-06-10T09:00:00Z", "2024-06-10T11:00:00Z"),
            timed("B", "2024-06-10T10:00:00Z", "2024-06-10T12:00:00Z"),
        )
        val result = TimeGridLayout.layout(events, weekDates, utc)
        assertEquals(2, result.timedSlots.size)
        val cols = result.timedSlots.map { it.column }.toSet()
        assertEquals(setOf(0, 1), cols)
        result.timedSlots.forEach { assertEquals(2, it.columnCount) }
    }

    // ---- Three events: A overlaps B, B overlaps C → all in same cluster ----

    @Test fun transitiveCluster_sameColumnCount() {
        val events = listOf(
            timed("A", "2024-06-10T08:00:00Z", "2024-06-10T10:00:00Z"),
            timed("B", "2024-06-10T09:00:00Z", "2024-06-10T11:00:00Z"),
            timed("C", "2024-06-10T10:30:00Z", "2024-06-10T12:00:00Z"),
        )
        val result = TimeGridLayout.layout(events, weekDates, utc)
        assertEquals(3, result.timedSlots.size)
        // A,B,C are transitively connected — all share a cluster of width >= 2
        // A overlaps B (col 0 and 1); B overlaps C (C reuses col 0 since A ended)
        val cols = result.timedSlots.sortedBy { it.eventId }.map { it.column }
        // Columns must be distinct per overlapping pair
        val slotA = result.timedSlots.first { it.eventId == "A" }
        val slotB = result.timedSlots.first { it.eventId == "B" }
        assertTrue(slotA.column != slotB.column)
    }

    // ---- Non-overlapping events share column 0 and get columnCount 1 each ----

    @Test fun twoNonOverlapping_sameColumn() {
        val events = listOf(
            timed("A", "2024-06-10T09:00:00Z", "2024-06-10T10:00:00Z"),
            timed("B", "2024-06-10T11:00:00Z", "2024-06-10T12:00:00Z"),
        )
        val result = TimeGridLayout.layout(events, weekDates, utc)
        assertEquals(2, result.timedSlots.size)
        result.timedSlots.forEach {
            assertEquals(0, it.column)
            assertEquals(1, it.columnCount)
        }
    }

    // ---- Multi-day timed event is split into per-day segments ----

    @Test fun multiDay_splitIntoSegments() {
        // Runs from Mon 22:00 UTC to Wed 02:00 UTC → segments on Mon, Tue, Wed
        val events = listOf(timed("A", "2024-06-10T22:00:00Z", "2024-06-12T02:00:00Z"))
        val result = TimeGridLayout.layout(events, weekDates, utc)
        val dates = result.timedSlots.map { it.date }.toSet()
        assertEquals(setOf(monday, monday.plusDays(1), monday.plusDays(2)), dates)
    }

    @Test fun multiDay_mondaySegment_startsAt22h() {
        val events = listOf(timed("A", "2024-06-10T22:00:00Z", "2024-06-12T02:00:00Z"))
        val result = TimeGridLayout.layout(events, weekDates, utc)
        val monSlot = result.timedSlots.first { it.date == monday }
        assertEquals(22f / 24f, monSlot.startFraction, 0.001f)
        assertEquals(1f, monSlot.endFraction, 0.001f)
    }

    @Test fun multiDay_tuesdaySegment_fullDay() {
        val events = listOf(timed("A", "2024-06-10T22:00:00Z", "2024-06-12T02:00:00Z"))
        val result = TimeGridLayout.layout(events, weekDates, utc)
        val tueSlot = result.timedSlots.first { it.date == monday.plusDays(1) }
        assertEquals(0f, tueSlot.startFraction, 0.001f)
        assertEquals(1f, tueSlot.endFraction, 0.001f)
    }

    @Test fun multiDay_wednesdaySegment_endsAt2h() {
        val events = listOf(timed("A", "2024-06-10T22:00:00Z", "2024-06-12T02:00:00Z"))
        val result = TimeGridLayout.layout(events, weekDates, utc)
        val wedSlot = result.timedSlots.first { it.date == monday.plusDays(2) }
        assertEquals(0f, wedSlot.startFraction, 0.001f)
        assertEquals(2f / 24f, wedSlot.endFraction, 0.001f)
    }

    // ---- Event outside visible dates is excluded ----

    @Test fun eventOutsideVisibleDates_excluded() {
        val events = listOf(timed("A", "2024-06-17T09:00:00Z", "2024-06-17T10:00:00Z"))
        val result = TimeGridLayout.layout(events, weekDates, utc)
        assertTrue(result.timedSlots.isEmpty())
    }

    // ---- All-day events ----

    @Test fun allDay_singleEvent_rowZero() {
        val events = listOf(allDay("A", "2024-06-10", "2024-06-11"))  // Mon only
        val result = TimeGridLayout.layout(events, weekDates, utc)
        assertEquals(1, result.allDaySlots.size)
        val slot = result.allDaySlots[0]
        assertEquals("A", slot.eventId)
        assertEquals(monday, slot.startDate)
        assertEquals(monday, slot.endDateInclusive)
        assertEquals(0, slot.row)
    }

    @Test fun allDay_twoOverlapping_distinctRows() {
        val events = listOf(
            allDay("A", "2024-06-10", "2024-06-13"),  // Mon–Wed (inclusive)
            allDay("B", "2024-06-11", "2024-06-12"),  // Tue only
        )
        val result = TimeGridLayout.layout(events, weekDates, utc)
        assertEquals(2, result.allDaySlots.size)
        val rows = result.allDaySlots.map { it.row }.toSet()
        assertEquals(setOf(0, 1), rows)
    }

    @Test fun allDay_nonOverlapping_sameRow() {
        val events = listOf(
            allDay("A", "2024-06-10", "2024-06-11"),  // Mon
            allDay("B", "2024-06-12", "2024-06-13"),  // Wed
        )
        val result = TimeGridLayout.layout(events, weekDates, utc)
        result.allDaySlots.forEach { assertEquals(0, it.row) }
    }

    @Test fun allDay_multiDay_endInclusiveIsOneLessThanExclusive() {
        val events = listOf(allDay("A", "2024-06-10", "2024-06-13"))
        val result = TimeGridLayout.layout(events, weekDates, utc)
        val slot = result.allDaySlots[0]
        assertEquals(monday, slot.startDate)
        assertEquals(monday.plusDays(2), slot.endDateInclusive)  // Wed
    }

    // ---- DST day: 23-hour day ----
    // In America/New_York, 2024-03-10 is 23 hours long (clocks spring forward).
    @Test fun dstDay_23Hours_fractionRemainsNormalized() {
        val nyZone = ZoneId.of("America/New_York")
        val dstDay = LocalDate.of(2024, 3, 10)
        // 12:00 EST = 17:00 UTC; day is 23h so noon is at 12/23 ≈ 0.5217
        val events = listOf(
            TimeGridLayout.InputEvent(
                id = "noon",
                startUtc = ts("2024-03-10T17:00:00Z"),
                endUtc = ts("2024-03-10T18:00:00Z"),
                isAllDay = false,
                startDate = null,
                endDate = null,
            )
        )
        val result = TimeGridLayout.layout(events, listOf(dstDay), nyZone)
        assertEquals(1, result.timedSlots.size)
        val slot = result.timedSlots[0]
        assertTrue("start fraction should be > 0.5", slot.startFraction > 0.5f)
        assertTrue("end fraction should be > start", slot.endFraction > slot.startFraction)
        assertTrue("fractions must be in [0,1]", slot.startFraction in 0f..1f && slot.endFraction in 0f..1f)
    }

    // ---- Zero-duration event (missing endUtc) doesn't crash ----
    @Test fun zeroDurationEvent_singleSlot() {
        val event = TimeGridLayout.InputEvent(
            id = "Z",
            startUtc = ts("2024-06-10T14:00:00Z"),
            endUtc = null,
            isAllDay = false,
            startDate = null,
            endDate = null,
        )
        val result = TimeGridLayout.layout(listOf(event), weekDates, utc)
        assertEquals(1, result.timedSlots.size)
        val slot = result.timedSlots[0]
        assertEquals(slot.startFraction, slot.endFraction, 0.001f)
    }
}
