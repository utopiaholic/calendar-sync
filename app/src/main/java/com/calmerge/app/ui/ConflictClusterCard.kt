package com.calmerge.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.calmerge.app.data.db.ConflictMemberRow
import com.calmerge.app.ui.theme.ConflictRed
import com.calmerge.app.ui.theme.DefaultAccountColor
import com.calmerge.app.ui.theme.OnSlateSecondary
import com.calmerge.app.ui.theme.SlateDark3
import com.calmerge.app.ui.theme.glassCard
import java.time.Instant
import java.time.ZoneId

private data class BarData(
    val clusterStartMs: Long,
    val clusterEndMs: Long,
    val overlapStartMs: Long?,
    val overlapEndMs: Long?,
    val headerText: String,
    val isOverlap: Boolean,
)

@Composable
internal fun ConflictClusterCard(
    cluster: ConflictClusterUi,
    zone: ZoneId,
    onEventClick: (ConflictMemberRow, List<ConflictMemberRow>) -> Unit,
) {
    val timed = cluster.members.mapNotNull { (rep, _) -> rep.event.startUtc?.let { rep } }

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

    // Header fallback: if fewer than 2 timed events exist, show all-day header.
    val effectiveHeaderText: String = barData?.headerText ?: run {
        val rawDate = cluster.members.firstOrNull()?.first?.event?.startDate ?: "?"
        val formattedDate = parseIsoDateOrNull(rawDate)?.let(EventUi.dayHeaderFormat::format) ?: rawDate
        "$formattedDate · All-day overlap"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(cornerRadius = 14.dp, fillAlpha = 0.07f)
            .padding(16.dp),
    ) {
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
            val color = Color(copies.firstOrNull()?.accountColor ?: DefaultAccountColor)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(SlateDark3),
            ) {
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
