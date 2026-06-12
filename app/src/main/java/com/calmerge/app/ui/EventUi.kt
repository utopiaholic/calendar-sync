package com.calmerge.app.ui

import com.calmerge.app.data.db.MergedEvent
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Shared presentation helpers for agenda/conflicts screens. */
object EventUi {

    // Formatters use Locale.getDefault() for production display strings.
    val dayHeaderFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())
    val timeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

    /** Timed events by UTC start; all-day events pinned to local midnight of their date. */
    fun sortKeyMs(event: MergedEvent, zone: ZoneId): Long =
        event.event.startUtc
            ?: event.event.startDate?.let { runCatching { LocalDate.parse(it).atStartOfDay(zone).toInstant().toEpochMilli() }.getOrNull() }
            ?: Long.MAX_VALUE

    /** Local date an event appears under in the agenda. */
    fun agendaDate(event: MergedEvent, zone: ZoneId): LocalDate =
        event.event.startDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: event.event.startUtc?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
            ?: LocalDate.MAX

    /** Monday 00:00 (inclusive) to next Monday 00:00 (exclusive) of the week containing today. */
    fun currentWeekBounds(zone: ZoneId, today: LocalDate = LocalDate.now(zone)): Pair<Long, Long> {
        val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
        return monday.atStartOfDay(zone).toInstant().toEpochMilli() to
            monday.plusDays(7).atStartOfDay(zone).toInstant().toEpochMilli()
    }

    /**
     * Returns true if the event's time range overlaps [weekStart, weekEnd).
     *
     * For timed events the range is [startUtc, endUtc) — falling back to startUtc when endUtc
     * is absent. For all-day events the date range is converted to local-zone millis.
     * This correctly includes events that started before weekStart but are still ongoing.
     */
    fun overlapsWeek(event: MergedEvent, weekStart: Long, weekEnd: Long, zone: ZoneId): Boolean {
        return if (event.event.isAllDay) {
            val startMs = event.event.startDate?.let {
                runCatching { LocalDate.parse(it).atStartOfDay(zone).toInstant().toEpochMilli() }.getOrNull()
            } ?: return false
            val endMs = event.event.endDate?.let {
                runCatching { LocalDate.parse(it).atStartOfDay(zone).toInstant().toEpochMilli() }.getOrNull()
            } ?: (startMs + 86_400_000L) // endDate is exclusive; default one day
            startMs < weekEnd && endMs > weekStart
        } else {
            val startMs = event.event.startUtc ?: return false
            val endMs = event.event.endUtc ?: startMs
            startMs < weekEnd && endMs > weekStart
        }
    }

    fun timeRangeText(event: MergedEvent, zone: ZoneId): String {
        val e = event.event
        if (e.isAllDay) return "All day"
        val start = e.startUtc?.let { timeFormat.format(Instant.ofEpochMilli(it).atZone(zone)) } ?: "?"
        val end = e.endUtc?.let { timeFormat.format(Instant.ofEpochMilli(it).atZone(zone)) } ?: "?"
        return "$start–$end"
    }

    /**
     * FR-13: rows sharing a dedupeGroupId render once; returns the representative
     * plus every copy (for account badges).
     */
    fun collapseDuplicates(events: List<MergedEvent>): List<Pair<MergedEvent, List<MergedEvent>>> {
        val out = mutableListOf<Pair<MergedEvent, List<MergedEvent>>>()
        val seenGroups = mutableSetOf<String>()
        for (event in events) {
            val groupId = event.event.dedupeGroupId
            if (groupId == null) {
                out += event to listOf(event)
            } else if (seenGroups.add(groupId)) {
                out += event to events.filter { it.event.dedupeGroupId == groupId }
            }
        }
        return out
    }
}
