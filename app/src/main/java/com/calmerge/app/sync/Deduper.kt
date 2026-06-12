package com.calmerge.app.sync

import com.calmerge.app.data.db.EventInstanceEntity
import java.util.Locale

/**
 * FR-13: the same meeting invited to both work addresses should appear once,
 * badged with both accounts. Events are grouped by iCalUId when available,
 * otherwise by normalized title + exact start + end. Grouping only applies
 * across DIFFERENT calendar sources — two distinct events inside one calendar
 * are never duplicates of each other.
 */
object Deduper {

    data class Group(val key: String, val eventIds: List<String>)

    fun computeGroups(events: List<EventInstanceEntity>): List<Group> =
        events
            .groupBy { dedupeKey(it) }
            .filterValues { members -> members.map { it.calendarSourceId }.distinct().size > 1 }
            .map { (key, members) -> Group(key, members.map { it.id }) }

    internal fun dedupeKey(e: EventInstanceEntity): String =
        e.iCalUId?.takeIf { it.isNotBlank() }
            ?: buildString {
                append("fuzzy:")
                append(normalizeTitle(e.title))
                append('|')
                append(e.startUtc ?: e.startDate)
                append('|')
                append(e.endUtc ?: e.endDate)
            }

    internal fun normalizeTitle(title: String): String =
        title.lowercase(Locale.ROOT).replace(Regex("\\s+"), " ").trim()
}
