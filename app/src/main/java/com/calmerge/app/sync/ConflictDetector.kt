package com.calmerge.app.sync

import com.calmerge.app.data.db.EventInstanceEntity
import com.calmerge.app.data.db.ResponseStatus
import com.calmerge.app.data.db.ShowAs
import java.time.LocalDate
import java.time.ZoneId

/**
 * FR-14..18: a conflict is two or more events whose time ranges overlap, where
 * every participant has show-as BUSY, TENTATIVE, or OOF. Pure logic — runs
 * after each sync, results cached in SQLite by the coordinator.
 *
 * Rules:
 * - FREE events never conflict (default config, FR-14).
 * - Declined events are excluded (spec §8 case 4).
 * - All-day events do not conflict with timed events (FR-15 default); they may
 *   conflict with other all-day events on overlapping dates.
 * - Same-calendar double bookings count (FR-16).
 * - Events in one dedupe group are the SAME meeting seen from two feeds and
 *   never conflict with each other; the group participates as one logical event.
 * - Transitive overlaps merge: A↔B and B↔C → one cluster {A,B,C} (FR-18).
 */
data class ConflictConfig(
    val includeTentative: Boolean = true,
    val includeOof: Boolean = true,
    /** FR-15 default: all-day events do NOT conflict with timed events. */
    val allDayConflictsWithTimed: Boolean = false,
)

object ConflictDetector {

    /** Each returned cluster is the list of EventInstance ids involved (incl. all dedupe copies). */
    fun detect(
        events: List<EventInstanceEntity>,
        zone: ZoneId,
        config: ConflictConfig = ConflictConfig(),
    ): List<List<String>> {
        val conflicting = buildSet {
            add(ShowAs.BUSY)
            if (config.includeTentative) add(ShowAs.TENTATIVE)
            if (config.includeOof) add(ShowAs.OOF)
        }
        val eligible = events.filter { it.showAs in conflicting && it.responseStatus != ResponseStatus.DECLINED }

        // Collapse dedupe groups into logical events.
        val logical = eligible
            .groupBy { it.dedupeGroupId ?: "solo:${it.id}" }
            .values
            .mapNotNull { members -> toInterval(members, zone) }

        return if (config.allDayConflictsWithTimed) {
            clusterBySweep(logical)
        } else {
            val (allDay, timed) = logical.partition { it.isAllDay }
            clusterBySweep(timed) + clusterBySweep(allDay)
        }
    }

    private data class Interval(
        val startMs: Long,
        val endMs: Long,
        val isAllDay: Boolean,
        val memberEventIds: List<String>,
    )

    private fun toInterval(members: List<EventInstanceEntity>, zone: ZoneId): Interval? {
        val rep = members.first()
        return if (rep.isAllDay) {
            val start = rep.startDate ?: return null
            // endDate is exclusive per RFC 5545; missing → one day.
            val end = rep.endDate ?: LocalDate.parse(start).plusDays(1).toString()
            Interval(
                startMs = LocalDate.parse(start).atStartOfDay(zone).toInstant().toEpochMilli(),
                endMs = LocalDate.parse(end).atStartOfDay(zone).toInstant().toEpochMilli(),
                isAllDay = true,
                memberEventIds = members.map { it.id },
            )
        } else {
            val start = rep.startUtc ?: return null
            val end = rep.endUtc ?: start
            Interval(start, end, isAllDay = false, memberEventIds = members.map { it.id })
        }
    }

    /**
     * Sweep-line over start-sorted intervals: consecutive chaining against the
     * cluster's max end yields exactly the connected components of the interval
     * overlap graph. Touching endpoints (end == next start) do NOT overlap.
     */
    private fun clusterBySweep(intervals: List<Interval>): List<List<String>> {
        if (intervals.size < 2) return emptyList()
        val sorted = intervals.sortedBy { it.startMs }
        val clusters = mutableListOf<List<String>>()
        var current = mutableListOf(sorted.first())
        var maxEnd = sorted.first().endMs

        for (interval in sorted.drop(1)) {
            if (interval.startMs < maxEnd) {
                current.add(interval)
                maxEnd = maxOf(maxEnd, interval.endMs)
            } else {
                if (current.size >= 2) clusters.add(current.flatMap { it.memberEventIds })
                current = mutableListOf(interval)
                maxEnd = interval.endMs
            }
        }
        if (current.size >= 2) clusters.add(current.flatMap { it.memberEventIds })
        return clusters
    }
}
