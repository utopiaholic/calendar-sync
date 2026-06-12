package com.calmerge.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.calmerge.app.data.db.AccountStatus
import com.calmerge.app.ui.theme.OnSlateSecondary
import com.calmerge.app.ui.theme.TealAccent
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
    val icsHostMap = remember(accounts) { icsHostsByAccountId(accounts) }

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
                    selectedEvent = rep.toDetailModel(copies, icsHostMap)
                },
            )
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}
