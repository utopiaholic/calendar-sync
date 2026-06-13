package com.calmerge.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.calmerge.app.data.db.AccountStatus
import com.calmerge.app.ui.theme.ConflictRed
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onOpenFeeds: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAgenda: () -> Unit,
    onOpenConflicts: () -> Unit,
) {
    val accounts by viewModel.accounts.collectAsState()
    val windowedClusters by viewModel.windowedConflictClusters.collectAsState()
    val mergedEvents by viewModel.mergedEvents.collectAsState()
    val syncing by viewModel.syncing.collectAsState()
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)

    // Collapse cross-account duplicates (same meeting in two feeds) before
    // filtering to today, mirroring the agenda (FR-13).
    val todayEvents = remember(mergedEvents, today, zone) {
        val start = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        EventUi.collapseDuplicates(mergedEvents).map { (rep, _) -> rep }.filter { evt ->
            if (evt.event.isAllDay) {
                evt.event.startDate == today.toString()
            } else {
                val s = evt.event.startUtc ?: return@filter false
                val e = evt.event.endUtc ?: s
                s < end && e > start
            }
        }
    }

    val nextCluster = remember(windowedClusters) { windowedClusters.firstOrNull() }
    val activeFeeds = accounts.filter { it.status == AccountStatus.ACTIVE }
    val failingFeeds = accounts.filter { it.status != AccountStatus.ACTIVE }
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        // ── Greeting header ──────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    greeting(),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    today.format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault())),
                    style = MaterialTheme.typography.bodyMedium,
                    color = mutedColor,
                )
            }
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.offset(x = 12.dp),
            ) {
                Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = mutedColor)
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── Summary pill ────────────────────────────────────────────────
        AnimatedContent(
            targetState = todayEvents.size to windowedClusters.size,
            transitionSpec = {
                fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) togetherWith
                    fadeOut(spring(stiffness = Spring.StiffnessMediumLow))
            },
            label = "summaryPill",
        ) { (eventCount, conflictCount) ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                val parts = buildList {
                    add("$eventCount ${if (eventCount == 1) "event" else "events"} today")
                    if (conflictCount > 0) add("$conflictCount upcoming ${if (conflictCount == 1) "conflict" else "conflicts"}")
                }
                Text(
                    parts.joinToString("  ·  "),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Bento row: Feeds | Next conflict ────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Calendars card
            BentoCard(
                modifier = Modifier.weight(1f),
                onClick = onOpenFeeds,
            ) {
                Text("Calendars", style = MaterialTheme.typography.titleSmall, color = mutedColor)
                Spacer(Modifier.height(8.dp))
                if (accounts.isEmpty()) {
                    Text("No calendars yet", style = MaterialTheme.typography.bodyMedium)
                    Text("Tap to add", style = MaterialTheme.typography.bodySmall, color = mutedColor)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        accounts.take(4).forEach { acc ->
                            Spacer(
                                Modifier
                                    .size(10.dp)
                                    .background(Color(acc.color), CircleShape),
                            )
                            Spacer(Modifier.width(3.dp))
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${activeFeeds.size} active",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (failingFeeds.isNotEmpty()) {
                        Text(
                            "${failingFeeds.size} error",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else if (accounts.isNotEmpty()) {
                        val lastSync = accounts.mapNotNull { it.lastSyncUtc }.maxOrNull()
                        Text(
                            if (lastSync != null) "Synced ${relativeTime(lastSync)}" else "Not synced",
                            style = MaterialTheme.typography.bodySmall,
                            color = mutedColor,
                        )
                    }
                }
            }

            // Next conflict card
            BentoCard(
                modifier = Modifier.weight(1f),
                onClick = if (windowedClusters.isEmpty()) null else onOpenConflicts,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Upcoming Conflicts", style = MaterialTheme.typography.titleSmall, color = mutedColor)
                    if (windowedClusters.isNotEmpty()) {
                        Icon(
                            Icons.Rounded.Warning,
                            contentDescription = null,
                            tint = ConflictRed,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (nextCluster == null) {
                    Text(
                        "All clear",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text("No conflicts ahead", style = MaterialTheme.typography.bodySmall, color = mutedColor)
                } else {
                    Text(
                        "${windowedClusters.size}",
                        style = MaterialTheme.typography.headlineMedium,
                        color = ConflictRed,
                        fontWeight = FontWeight.Bold,
                    )
                    val firstMember = nextCluster.members.firstOrNull()?.first
                    val clusterTimeText = firstMember?.event?.startUtc?.let {
                        Instant.ofEpochMilli(it).atZone(zone).let { zdt ->
                            if (zdt.toLocalDate() == today) {
                                "Today ${zdt.format(DateTimeFormatter.ofPattern("HH:mm"))}"
                            } else {
                                zdt.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
                            }
                        }
                    } ?: "All-day"
                    Text(clusterTimeText, style = MaterialTheme.typography.bodySmall, color = mutedColor)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── Today events card (full width) ───────────────────────────────
        BentoCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = onOpenAgenda,
            minHeight = 100.dp,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Today", style = MaterialTheme.typography.titleSmall, color = mutedColor)
                Text(
                    "${todayEvents.size} events",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(10.dp))
            if (todayEvents.isEmpty()) {
                Text(
                    "No events scheduled",
                    style = MaterialTheme.typography.bodyMedium,
                    color = mutedColor,
                )
            } else {
                todayEvents
                    .sortedBy { it.event.startUtc ?: Long.MAX_VALUE }
                    .take(3)
                    .forEach { evt ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 3.dp),
                        ) {
                            Box(
                                Modifier
                                    .width(3.dp)
                                    .height(32.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color(evt.accountColor)),
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    evt.event.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                )
                                val timeStr = if (evt.event.isAllDay) {
                                    "All day"
                                } else {
                                    evt.event.startUtc?.let { ms ->
                                        Instant.ofEpochMilli(ms).atZone(zone)
                                            .format(DateTimeFormatter.ofPattern("HH:mm"))
                                    } ?: ""
                                }
                                Text(
                                    timeStr,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = mutedColor,
                                )
                            }
                        }
                    }
                if (todayEvents.size > 3) {
                    Text(
                        "+${todayEvents.size - 3} more",
                        style = MaterialTheme.typography.bodySmall,
                        color = mutedColor,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Sync pill ────────────────────────────────────────────────────
        Button(
            onClick = { viewModel.syncNow() },
            enabled = !syncing && accounts.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.primary,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                disabledContentColor = mutedColor,
            ),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (syncing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text("Syncing…")
            } else {
                Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Sync now")
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

private fun greeting(): String {
    val hour = java.time.LocalTime.now().hour
    return when {
        hour < 5 -> "Good night"
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        hour < 21 -> "Good evening"
        else -> "Good night"
    }
}

private fun relativeTime(epochMs: Long): String {
    val minutes = (System.currentTimeMillis() - epochMs) / 60_000
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        else -> "${minutes / 60}h ago"
    }
}
