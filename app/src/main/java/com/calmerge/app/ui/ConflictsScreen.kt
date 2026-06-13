package com.calmerge.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.calmerge.app.data.db.AccountStatus
import java.time.ZoneId

private enum class ConflictTab { UPCOMING, PAST }

/**
 * FR-21: timeline-style conflict clusters, soonest first.
 * Defaults to Upcoming (today onward); Past shows resolved/historical clusters.
 * Within Upcoming, clusters inside the configured lookahead window are shown first,
 * with a "Later" section separator before anything beyond the window.
 */
@Composable
fun ConflictsScreen(viewModel: MainViewModel) {
    val upcoming by viewModel.upcomingConflictClusters.collectAsState()
    val past by viewModel.pastConflictClusters.collectAsState()
    val lookaheadDays by viewModel.conflictLookaheadDays.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val zone = ZoneId.systemDefault()
    var selectedEvent by remember { mutableStateOf<EventDetailModel?>(null) }
    var tab by remember { mutableStateOf(ConflictTab.UPCOMING) }
    val failingAccounts = accounts.filter { it.status != AccountStatus.ACTIVE }
    val icsHostMap = remember(accounts) { icsHostsByAccountId(accounts) }

    // Partition upcoming into within-window and beyond-window.
    val withinWindow = remember(upcoming, lookaheadDays) {
        val startOfToday = EventUi.startOfTodayMs(zone)
        val windowEnd = startOfToday + lookaheadDays.toLong() * 86_400_000L
        upcoming.filter { it.sortKeyMs < windowEnd }
    }
    val beyondWindow = remember(upcoming, lookaheadDays) {
        val startOfToday = EventUi.startOfTodayMs(zone)
        val windowEnd = startOfToday + lookaheadDays.toLong() * 86_400_000L
        upcoming.filter { it.sortKeyMs >= windowEnd }
    }

    selectedEvent?.let { event ->
        EventDetailSheet(event = event, onDismiss = { selectedEvent = null })
    }

    Column(modifier = Modifier.fillMaxSize()) {
        CenteredSegmentedTabs(
            options = listOf("Upcoming", "Past"),
            selectedIndex = if (tab == ConflictTab.UPCOMING) 0 else 1,
            onSelect = { tab = if (it == 0) ConflictTab.UPCOMING else ConflictTab.PAST },
        )

        val displayClusters = if (tab == ConflictTab.UPCOMING) upcoming else past

        if (displayClusters.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp),
                ) {
                    Text("✓", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "All clear",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (tab == ConflictTab.UPCOMING) "No conflicts ahead" else "No past conflicts",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (tab == ConflictTab.UPCOMING && failingAccounts.isNotEmpty()) {
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
            return@Column
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Spacer(Modifier.height(4.dp)) }

            if (tab == ConflictTab.UPCOMING) {
                items(withinWindow, key = { it.clusterId }) { cluster ->
                    ConflictClusterCard(
                        cluster = cluster,
                        zone = zone,
                        onEventClick = { rep, copies ->
                            selectedEvent = rep.toDetailModel(copies, icsHostMap)
                        },
                    )
                }
                if (beyondWindow.isNotEmpty()) {
                    item(key = "later-separator") {
                        ConflictSectionSeparator("Later")
                    }
                    items(beyondWindow, key = { it.clusterId }) { cluster ->
                        ConflictClusterCard(
                            cluster = cluster,
                            zone = zone,
                            onEventClick = { rep, copies ->
                                selectedEvent = rep.toDetailModel(copies, icsHostMap)
                            },
                        )
                    }
                }
            } else {
                items(displayClusters, key = { it.clusterId }) { cluster ->
                    ConflictClusterCard(
                        cluster = cluster,
                        zone = zone,
                        onEventClick = { rep, copies ->
                            selectedEvent = rep.toDetailModel(copies, icsHostMap)
                        },
                    )
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun ConflictSectionSeparator(label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outline,
            thickness = 1.dp,
        )
        Box(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 14.dp, vertical = 5.dp),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outline,
            thickness = 1.dp,
        )
    }
}
