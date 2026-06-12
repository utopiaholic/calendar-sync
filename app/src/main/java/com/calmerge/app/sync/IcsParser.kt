package com.calmerge.app.sync

import biweekly.Biweekly
import biweekly.ICalendar
import biweekly.component.VEvent
import com.calmerge.app.data.db.EventInstanceEntity
import com.calmerge.app.data.db.ResponseStatus
import com.calmerge.app.data.db.ShowAs
import java.util.Date
import java.util.TimeZone
import java.util.UUID

/**
 * FR-8/FR-10: ICS feeds are fully re-parsed each refresh, with recurrences
 * expanded by biweekly inside the sync window. Pure JVM logic — unit-testable.
 */
object IcsParser {

    private const val MAX_INSTANCES_PER_EVENT = 1000

    fun parse(icsText: String, calendarSourceId: String, window: SyncWindow): List<EventInstanceEntity> {
        val ical = Biweekly.parse(icsText).first() ?: return emptyList()
        val result = mutableListOf<EventInstanceEntity>()

        // Recurrence exceptions (spec §8 case 2): VEVENTs sharing a UID where the
        // override carries RECURRENCE-ID. Skip the overridden occurrence from the
        // base expansion and emit the override instead.
        val eventsByUid = ical.events.groupBy { it.uid?.value ?: UUID.randomUUID().toString() }

        for ((uid, group) in eventsByUid) {
            val base = group.firstOrNull { it.recurrenceId == null } ?: group.first()
            val overrides = group.filter { it.recurrenceId != null }
            val overriddenStarts = overrides.mapNotNull { it.recurrenceId?.value?.time }.toSet()

            if (base.recurrenceId == null) {
                result += expand(ical, base, uid, calendarSourceId, window, overriddenStarts)
            }
            for (override in overrides) {
                singleInstance(ical, override, uid, calendarSourceId, window)?.let { result += it }
            }
        }
        return result
    }

    private fun expand(
        ical: ICalendar,
        event: VEvent,
        uid: String,
        calendarSourceId: String,
        window: SyncWindow,
        overriddenStarts: Set<Long>,
    ): List<EventInstanceEntity> {
        if (isCancelled(event)) return emptyList()
        val start = event.dateStart?.value ?: return emptyList()
        val isAllDay = !start.hasTime()
        val durationMs = eventDurationMs(event)
        val tz = timezoneFor(ical, event)
        val windowStartMs = window.startUtc.toEpochMilli()
        val windowEndMs = window.endUtc.toEpochMilli()

        val out = mutableListOf<EventInstanceEntity>()
        val iterator = event.getDateIterator(tz)
        // Start far enough back that an in-progress occurrence still overlaps the window.
        iterator.advanceTo(Date(windowStartMs - durationMs - 1))
        var count = 0
        while (iterator.hasNext() && count < MAX_INSTANCES_PER_EVENT) {
            val occurrenceStart = iterator.next()
            count++
            if (occurrenceStart.time >= windowEndMs) break
            if (occurrenceStart.time + durationMs <= windowStartMs) continue
            if (occurrenceStart.time in overriddenStarts) continue
            out += toEntity(event, uid, calendarSourceId, occurrenceStart.time, durationMs, isAllDay, tz)
        }
        return out
    }

    private fun singleInstance(
        ical: ICalendar,
        event: VEvent,
        uid: String,
        calendarSourceId: String,
        window: SyncWindow,
    ): EventInstanceEntity? {
        if (isCancelled(event)) return null
        val start = event.dateStart?.value ?: return null
        val durationMs = eventDurationMs(event)
        if (start.time >= window.endUtc.toEpochMilli()) return null
        if (start.time + durationMs <= window.startUtc.toEpochMilli()) return null
        return toEntity(
            event, uid, calendarSourceId, start.time, durationMs,
            isAllDay = !start.hasTime(),
            tz = timezoneFor(ical, event),
        )
    }

    private fun toEntity(
        event: VEvent,
        uid: String,
        calendarSourceId: String,
        startMs: Long,
        durationMs: Long,
        isAllDay: Boolean,
        tz: TimeZone,
    ): EventInstanceEntity {
        var startUtc: Long? = null
        var endUtc: Long? = null
        var startDate: String? = null
        var endDate: String? = null
        if (isAllDay) {
            // FR-11: date-only, formatted in the event's own zone — not via UTC.
            startDate = formatDate(startMs, tz)
            endDate = formatDate(startMs + durationMs.coerceAtLeast(86_400_000L), tz)
        } else {
            startUtc = startMs
            endUtc = startMs + durationMs
        }
        return EventInstanceEntity(
            id = UUID.randomUUID().toString(),
            calendarSourceId = calendarSourceId,
            providerEventId = "$uid#$startMs",
            iCalUId = uid,
            title = event.summary?.value ?: "(no title)",
            startUtc = startUtc,
            endUtc = endUtc,
            isAllDay = isAllDay,
            startDate = startDate,
            endDate = endDate,
            showAs = mapShowAs(event),
            responseStatus = ResponseStatus.UNKNOWN,
            location = event.location?.value?.takeIf { it.isNotBlank() },
            organizer = event.organizer?.email,
            lastModifiedUtc = event.lastModified?.value?.time,
        )
    }

    /** Edge case 7: missing DTEND → use DURATION if present, else zero duration. */
    internal fun eventDurationMs(event: VEvent): Long {
        val start = event.dateStart?.value ?: return 0
        event.dateEnd?.value?.let { return (it.time - start.time).coerceAtLeast(0) }
        event.duration?.value?.let { d ->
            var ms = 0L
            d.weeks?.let { ms += it * 7L * 86_400_000L }
            d.days?.let { ms += it * 86_400_000L }
            d.hours?.let { ms += it * 3_600_000L }
            d.minutes?.let { ms += it * 60_000L }
            d.seconds?.let { ms += it * 1_000L }
            return if (d.isPrior) -ms else ms
        }
        // All-day events without DTEND default to one day per RFC 5545.
        return if (!start.hasTime()) 86_400_000L else 0L
    }

    private fun isCancelled(event: VEvent): Boolean =
        event.status?.value.equals("CANCELLED", ignoreCase = true)

    /**
     * Outlook published feeds carry X-MICROSOFT-CDO-BUSYSTATUS; fall back to
     * TRANSP (transparent → FREE) per plain RFC 5545.
     */
    private fun mapShowAs(event: VEvent): ShowAs {
        val busyStatus = event.getExperimentalProperty("X-MICROSOFT-CDO-BUSYSTATUS")?.value
        if (busyStatus != null) {
            return when (busyStatus.uppercase()) {
                "FREE" -> ShowAs.FREE
                "TENTATIVE" -> ShowAs.TENTATIVE
                "OOF" -> ShowAs.OOF
                "BUSY" -> ShowAs.BUSY
                else -> ShowAs.UNKNOWN
            }
        }
        val transp = event.transparency?.value
        return if (transp.equals("TRANSPARENT", ignoreCase = true)) ShowAs.FREE else ShowAs.BUSY
    }

    private fun timezoneFor(ical: ICalendar, event: VEvent): TimeZone {
        val dateStart = event.dateStart ?: return TimeZone.getTimeZone("UTC")
        return ical.timezoneInfo?.getTimezone(dateStart)?.timeZone ?: TimeZone.getTimeZone("UTC")
    }

    private fun formatDate(epochMs: Long, tz: TimeZone): String {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT)
        fmt.timeZone = tz
        return fmt.format(Date(epochMs))
    }
}
