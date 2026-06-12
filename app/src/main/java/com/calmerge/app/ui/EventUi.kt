package com.calmerge.app.ui

import com.calmerge.app.data.db.MergedEvent
import com.calmerge.app.data.db.EventInstanceEntity
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

    /**
     * Timed events by UTC start; all-day events pinned to local midnight of their date.
     * Long.MAX_VALUE keeps unparseable dates sorted last instead of crashing display code.
     */
    fun sortKeyMs(event: EventInstanceEntity, zone: ZoneId): Long =
        event.startUtc
            ?: isoDateToEpochMillisOrNull(event.startDate, zone)
            ?: Long.MAX_VALUE

    fun sortKeyMs(event: MergedEvent, zone: ZoneId): Long =
        sortKeyMs(event.event, zone)

    /** Epoch millis of start-of-today in the given zone. */
    fun startOfTodayMs(zone: ZoneId, today: LocalDate = LocalDate.now(zone)): Long =
        today.atStartOfDay(zone).toInstant().toEpochMilli()

    /**
     * Exclusive end of an event in epoch millis, used for past/upcoming partitioning.
     * All-day: end date → local zone millis (exclusive), defaulting to start + 1 day.
     * Timed: endUtc, falling back to startUtc so zero-duration events are not treated as end=0.
     */
    fun eventEndMs(event: EventInstanceEntity, zone: ZoneId): Long =
        if (event.isAllDay) {
            isoDateToEpochMillisOrNull(event.endDate, zone)
                ?: ((isoDateToEpochMillisOrNull(event.startDate, zone) ?: 0L) + 86_400_000L)
        } else {
            event.endUtc ?: event.startUtc ?: 0L
        }

    fun eventEndMs(event: MergedEvent, zone: ZoneId): Long = eventEndMs(event.event, zone)

    /**
     * True when the event is fully in the past.
     * Uses <= so all-day events with an exclusive end-date equal to today's midnight
     * (e.g. a yesterday event with endDate "2026-06-12") are correctly classified as past.
     */
    fun isPast(event: EventInstanceEntity, zone: ZoneId, todayStartMs: Long = startOfTodayMs(zone)): Boolean =
        eventEndMs(event, zone) <= todayStartMs

    fun isPast(event: MergedEvent, zone: ZoneId, todayStartMs: Long = startOfTodayMs(zone)): Boolean =
        isPast(event.event, zone, todayStartMs)

    /**
     * Local date an event appears under in the agenda.
     * LocalDate.MAX gives malformed rows a far-future header instead of crashing display code.
     */
    fun agendaDate(event: MergedEvent, zone: ZoneId): LocalDate =
        parseIsoDateOrNull(event.event.startDate)
            ?: event.event.startUtc?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
            ?: LocalDate.MAX

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
