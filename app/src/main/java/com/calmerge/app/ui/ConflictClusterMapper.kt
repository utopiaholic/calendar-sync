package com.calmerge.app.ui

import com.calmerge.app.data.db.ConflictMemberRow
import java.time.ZoneId

/**
 * Pure function that maps raw conflict member rows (from the DB query) into
 * display-ready [ConflictClusterUi] items. Extracted here so it can be unit-
 * tested without an Android runtime.
 */
object ConflictClusterMapper {

    fun toClusterUi(
        rows: List<ConflictMemberRow>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<ConflictClusterUi> {
        return rows.groupBy { it.clusterId }.map { (clusterId, members) ->
            // Collapse dedupe copies inside the cluster for display.
            val collapsed = mutableListOf<Pair<ConflictMemberRow, List<ConflictMemberRow>>>()
            val seen = mutableSetOf<String>()
            for (m in members) {
                val g = m.event.dedupeGroupId
                if (g == null) {
                    collapsed += m to listOf(m)
                } else if (seen.add(g)) {
                    collapsed += m to members.filter { it.event.dedupeGroupId == g }
                }
            }
            // Sort members by earliest start so display order is deterministic.
            val sortedCollapsed = collapsed.sortedBy { (rep, _) ->
                EventUi.sortKeyMs(rep.event, zone)
            }
            ConflictClusterUi(
                clusterId = clusterId,
                members = sortedCollapsed,
                sortKeyMs = members.minOf { m -> EventUi.sortKeyMs(m.event, zone) },
                endKeyMs = members.maxOf { m -> EventUi.eventEndMs(m.event, zone) },
            )
        }.sortedBy { it.sortKeyMs }
    }
}
