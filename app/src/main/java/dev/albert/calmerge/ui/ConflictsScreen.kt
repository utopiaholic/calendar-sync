package dev.albert.calmerge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.albert.calmerge.data.db.AccountStatus
import dev.albert.calmerge.data.db.ConflictMemberRow
import java.time.Instant
import java.time.ZoneId

/**
 * FR-21: dedicated list of conflict clusters, soonest first — the screen used
 * to plan manual fixes in the native calendar apps.
 */
@Composable
fun ConflictsScreen(viewModel: MainViewModel) {
    val clusters by viewModel.conflictClusters.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val zone = ZoneId.systemDefault()
    var selectedEvent by remember { mutableStateOf<EventDetailModel?>(null) }
    val failingAccounts = accounts.filter { it.status != AccountStatus.ACTIVE }

    selectedEvent?.let { event ->
        EventDetailSheet(event = event, onDismiss = { selectedEvent = null })
    }

    if (clusters.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No conflicts", color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (failingAccounts.isNotEmpty()) {
                    Spacer(Modifier.size(6.dp))
                    Text(
                        "Some feeds failed to sync:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    failingAccounts.forEach { account ->
                        Text(
                            "- ${account.displayName}: ${account.lastSyncError ?: account.status.name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                } else {
                    Spacer(Modifier.size(6.dp))
                    Text(
                        "Duplicated meetings across feeds are merged into one event.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        items(clusters, key = { it.clusterId }) { cluster ->
            ConflictCard(
                cluster = cluster,
                zone = zone,
                onEventClick = { rep, copies ->
                    selectedEvent = EventDetailModel(
                        title = rep.event.title,
                        startUtc = rep.event.startUtc,
                        endUtc = rep.event.endUtc,
                        isAllDay = rep.event.isAllDay,
                        startDate = rep.event.startDate,
                        location = rep.event.location,
                        organizer = rep.event.organizer,
                        showAs = rep.event.showAs.name,
                        accounts = copies.distinctBy { it.accountId }.map {
                            EventDetailAccount(
                                id = it.accountId,
                                name = it.accountName,
                                type = it.accountType,
                            )
                        },
                    )
                },
            )
            Spacer(Modifier.size(10.dp))
        }
    }
}

@Composable
private fun ConflictCard(
    cluster: ConflictClusterUi,
    zone: ZoneId,
    onEventClick: (ConflictMemberRow, List<ConflictMemberRow>) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            val timed = cluster.members.mapNotNull { (rep, _) -> rep.event.startUtc?.let { rep } }
            val header = if (timed.isNotEmpty()) {
                val clusterStartMs = timed.minOf { it.event.startUtc!! }
                val startDate = Instant.ofEpochMilli(clusterStartMs).atZone(zone).toLocalDate()
                // Overlap range: latest start to earliest end; falls back to the
                // cluster's span for chained overlaps with no common intersection.
                val overlapStart = timed.maxOf { it.event.startUtc!! }
                val overlapEnd = timed.minOf { it.event.endUtc ?: it.event.startUtc!! }
                val rangeText = if (overlapStart < overlapEnd) {
                    val endDate = Instant.ofEpochMilli(overlapEnd).atZone(zone).toLocalDate()
                    val endStr = if (endDate != startDate) {
                        "${EventUi.dayHeaderFormat.format(endDate)} ${fmt(overlapEnd, zone)}"
                    } else {
                        fmt(overlapEnd, zone)
                    }
                    "Overlap ${fmt(overlapStart, zone)}–$endStr"
                } else {
                    val spanEndMs = timed.maxOf { it.event.endUtc ?: it.event.startUtc!! }
                    val spanEndDate = Instant.ofEpochMilli(spanEndMs).atZone(zone).toLocalDate()
                    val endStr = if (spanEndDate != startDate) {
                        "${EventUi.dayHeaderFormat.format(spanEndDate)} ${fmt(spanEndMs, zone)}"
                    } else {
                        fmt(spanEndMs, zone)
                    }
                    "Chained ${fmt(clusterStartMs, zone)}–$endStr"
                }
                "${EventUi.dayHeaderFormat.format(startDate)} · $rangeText"
            } else {
                val rawDate = cluster.members.firstOrNull()?.first?.event?.startDate ?: "?"
                val formattedDate = runCatching {
                    EventUi.dayHeaderFormat.format(java.time.LocalDate.parse(rawDate))
                }.getOrElse { rawDate }
                "$formattedDate · All-day overlap"
            }
            Text(header, style = MaterialTheme.typography.titleSmall, color = Color(0xFFD93025))
            Spacer(Modifier.size(6.dp))
            cluster.members.forEach { (rep, copies) ->
                MemberRow(rep, copies, zone, onClick = { onEventClick(rep, copies) })
            }
        }
    }
}

@Composable
private fun MemberRow(
    rep: ConflictMemberRow,
    copies: List<ConflictMemberRow>,
    zone: ZoneId,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 3.dp),
    ) {
        Row {
            copies.distinctBy { it.accountId }.forEach {
                Spacer(Modifier.size(10.dp).background(Color(it.accountColor), CircleShape))
                Spacer(Modifier.width(2.dp))
            }
        }
        Spacer(Modifier.width(8.dp))
        Column {
            Text(rep.event.title, style = MaterialTheme.typography.bodyMedium)
            val time = if (rep.event.isAllDay) {
                "All day"
            } else {
                "${fmt(rep.event.startUtc ?: 0, zone)}–${fmt(rep.event.endUtc ?: 0, zone)}"
            }
            val badge = copies.distinctBy { it.accountId }.joinToString(" + ") { it.accountName }
            Text(
                "$time · ${rep.event.showAs} · $badge",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun fmt(epochMs: Long, zone: ZoneId): String =
    EventUi.timeFormat.format(Instant.ofEpochMilli(epochMs).atZone(zone))
