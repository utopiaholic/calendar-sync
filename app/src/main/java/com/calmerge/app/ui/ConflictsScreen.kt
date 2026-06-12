package com.calmerge.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.calmerge.app.data.db.AccountStatus
import com.calmerge.app.data.db.ConflictMemberRow
import com.calmerge.app.ui.theme.ConflictRed
import com.calmerge.app.ui.theme.OnSlateSecondary
import com.calmerge.app.ui.theme.SlateDark3
import com.calmerge.app.ui.theme.TealAccent
import com.calmerge.app.ui.theme.glassCard
import java.time.Instant
import java.time.ZoneId

/**
 * FR-21: timeline-style conflict clusters, soonest first.
 */
@Composable
fun ConflictsScreen(viewModel: MainViewModel) {
    val clusters by viewModel.conflictClusters.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val zone = ZoneId.systemDefault()
    var selectedEvent by remember { mutableStateOf<EventDetailModel?>(null) }
    val failingAccounts = accounts.filter { it.status != AccountStatus.ACTIVE }
    val icsHostById = remember(accounts) {
        accounts.associate { acc ->
            acc.id to acc.icsUrl?.let { runCatching { java.net.URI(it).host }.getOrNull() }
        }
    }

    selectedEvent?.let { event ->
        EventDetailSheet(event = event, onDismiss = { selectedEvent = null })
    }

    if (clusters.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                // Friendly empty state
                Text("✓", style = MaterialTheme.typography.headlineLarge, color = TealAccent)
                Spacer(Modifier.height(12.dp))
                Text(
                    "All clear",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "No conflicts ahead",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSlateSecondary,
                )
                if (failingAccounts.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    failingAccounts.forEach { account ->
                        Text(
                            "⚠ ${account.displayName}: ${account.lastSyncError ?: account.status.name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Spacer(Modifier.height(4.dp)) }
        items(clusters, key = { it.clusterId }) { cluster ->
            ConflictClusterCard(
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
                                icsHost = icsHostById[it.accountId],
                            )
                        },
                    )
                },
            )
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun ConflictClusterCard(
    cluster: ConflictClusterUi,
    zone: ZoneId,
    onEventClick: (ConflictMemberRow, List<ConflictMemberRow>) -> Unit,
) {
    val timed = cluster.members.mapNotNull { (rep, _) -> rep.event.startUtc?.let { rep } }
    val hasAllDay = cluster.members.any { (rep, _) -> rep.event.isAllDay }

    // Compute overlap/span for the visual bar
    data class BarData(
        val clusterStartMs: Long,
        val clusterEndMs: Long,
        val overlapStartMs: Long?,
        val overlapEndMs: Long?,
        val headerText: String,
        val isOverlap: Boolean,
    )

    val barData: BarData? = if (timed.size >= 2) {
        val clusterStartMs = timed.minOf { it.event.startUtc!! }
        val clusterEndMs = timed.maxOf { it.event.endUtc ?: it.event.startUtc!! }
        val overlapStart = timed.maxOf { it.event.startUtc!! }
        val overlapEnd = timed.minOf { it.event.endUtc ?: it.event.startUtc!! }
        val startDate = Instant.ofEpochMilli(clusterStartMs).atZone(zone).toLocalDate()
        val isOverlap = overlapStart < overlapEnd
        val rangeMs = if (isOverlap) overlapStart to overlapEnd else null
        val headerText = if (isOverlap) {
            "${EventUi.dayHeaderFormat.format(startDate)} · Overlap ${fmt(overlapStart, zone)}–${fmt(overlapEnd, zone)}"
        } else {
            "${EventUi.dayHeaderFormat.format(startDate)} · Chained ${fmt(clusterStartMs, zone)}–${fmt(clusterEndMs, zone)}"
        }
        BarData(clusterStartMs, clusterEndMs, rangeMs?.first, rangeMs?.second, headerText, isOverlap)
    } else {
        null
    }

    // Header fallback: if fewer than 2 timed events exist, show all-day header
    val effectiveHeaderText: String = barData?.headerText ?: run {
        if (hasAllDay) {
            val rawDate = cluster.members.firstOrNull()?.first?.event?.startDate ?: "?"
            val formattedDate = runCatching {
                EventUi.dayHeaderFormat.format(java.time.LocalDate.parse(rawDate))
            }.getOrElse { rawDate }
            "$formattedDate · All-day overlap"
        } else {
            val rawDate = cluster.members.firstOrNull()?.first?.event?.startDate ?: "?"
            val formattedDate = runCatching {
                EventUi.dayHeaderFormat.format(java.time.LocalDate.parse(rawDate))
            }.getOrElse { rawDate }
            "$formattedDate · All-day overlap"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(cornerRadius = 14.dp, fillAlpha = 0.07f)
            .padding(16.dp),
    ) {
        // Header with conflict indicator
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(ConflictRed),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                effectiveHeaderText,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = ConflictRed,
            )
        }

        Spacer(Modifier.height(12.dp))

        // Visual overlap bar
        if (barData != null) {
            OverlapBar(
                clusterStartMs = barData.clusterStartMs,
                clusterEndMs = barData.clusterEndMs,
                overlapStartMs = barData.overlapStartMs,
                overlapEndMs = barData.overlapEndMs,
                members = cluster.members,
            )
            Spacer(Modifier.height(12.dp))
        }

        // Member rows
        cluster.members.forEachIndexed { index, (rep, copies) ->
            if (index > 0) Spacer(Modifier.height(6.dp))
            ConflictMemberItem(rep, copies, zone, onClick = { onEventClick(rep, copies) })
        }
    }
}

@Composable
private fun OverlapBar(
    clusterStartMs: Long,
    clusterEndMs: Long,
    overlapStartMs: Long?,
    overlapEndMs: Long?,
    members: List<Pair<ConflictMemberRow, List<ConflictMemberRow>>>,
) {
    val totalSpan = (clusterEndMs - clusterStartMs).coerceAtLeast(1L)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // One bar per member event
        members.forEach { (rep, copies) ->
            // All-day events render as full-width bars; timed events use their UTC fractions.
            val startFrac = if (rep.event.isAllDay || rep.event.startUtc == null) {
                0f
            } else {
                ((rep.event.startUtc - clusterStartMs).toFloat() / totalSpan).coerceIn(0f, 1f)
            }
            val endFrac = if (rep.event.isAllDay || rep.event.startUtc == null) {
                1f
            } else {
                val evtEnd = rep.event.endUtc ?: rep.event.startUtc
                ((evtEnd - clusterStartMs).toFloat() / totalSpan).coerceIn(0f, 1f)
            }
            val color = Color(copies.firstOrNull()?.accountColor ?: 0xFF39D0C8.toInt())

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(SlateDark3),
            ) {
                // Event span bar
                Row {
                    if (startFrac > 0f) {
                        Spacer(Modifier.weight(startFrac))
                    }
                    Box(
                        modifier = Modifier
                            .weight((endFrac - startFrac).coerceAtLeast(0.02f))
                            .height(20.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(color.copy(alpha = 0.5f)),
                    )
                    val afterFrac = 1f - endFrac
                    if (afterFrac > 0f) {
                        Spacer(Modifier.weight(afterFrac))
                    }
                }

                // Overlap highlight
                if (overlapStartMs != null && overlapEndMs != null) {
                    val oStart = ((overlapStartMs - clusterStartMs).toFloat() / totalSpan).coerceIn(0f, 1f)
                    val oEnd = ((overlapEndMs - clusterStartMs).toFloat() / totalSpan).coerceIn(0f, 1f)
                    Row {
                        if (oStart > 0f) Spacer(Modifier.weight(oStart))
                        Box(
                            modifier = Modifier
                                .weight((oEnd - oStart).coerceAtLeast(0.02f))
                                .height(20.dp)
                                .background(ConflictRed.copy(alpha = 0.35f)),
                        )
                        val after = 1f - oEnd
                        if (after > 0f) Spacer(Modifier.weight(after))
                    }
                }
            }
        }
    }
}

@Composable
private fun ConflictMemberItem(
    rep: ConflictMemberRow,
    copies: List<ConflictMemberRow>,
    zone: ZoneId,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
    ) {
        // Account colored dots
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            copies.distinctBy { it.accountId }.forEach { copy ->
                Spacer(
                    Modifier
                        .size(9.dp)
                        .background(Color(copy.accountColor), CircleShape),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                rep.event.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            val time = if (rep.event.isAllDay) {
                "All day"
            } else {
                "${fmt(rep.event.startUtc ?: 0, zone)}–${fmt(rep.event.endUtc ?: 0, zone)}"
            }
            val badge = copies.distinctBy { it.accountId }.joinToString(" + ") { it.accountName }
            Text(
                "$time · ${rep.event.showAs} · $badge",
                style = MaterialTheme.typography.bodySmall,
                color = OnSlateSecondary,
            )
        }
    }
}

private fun fmt(epochMs: Long, zone: ZoneId): String =
    EventUi.timeFormat.format(Instant.ofEpochMilli(epochMs).atZone(zone))
