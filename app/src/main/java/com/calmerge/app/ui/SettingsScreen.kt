package com.calmerge.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.calmerge.app.settings.SettingsStore

private val SYNC_OPTIONS = listOf(
    15L to "Every 15 minutes",
    30L to "Every 30 minutes",
    60L to "Every hour",
    SettingsStore.MANUAL_SYNC to "Manual only",
)

private val LOOKAHEAD_OPTIONS = listOf(
    1 to "Today only",
    3 to "Next 3 days",
    7 to "Next week",
    14 to "Next 2 weeks",
    30 to "Next month",
)

@Composable
fun SettingsScreen(viewModel: MainViewModel, onOpenFeeds: () -> Unit = {}) {
    val syncInterval by viewModel.syncIntervalMinutes.collectAsState()
    val includeTentative by viewModel.includeTentative.collectAsState()
    val includeOof by viewModel.includeOof.collectAsState()
    val allDayVsTimed by viewModel.allDayConflictsWithTimed.collectAsState()
    val lookaheadDays by viewModel.conflictLookaheadDays.collectAsState()
    var showWipeConfirm by remember { mutableStateOf(false) }

    if (showWipeConfirm) {
        AlertDialog(
            onDismissRequest = { showWipeConfirm = false },
            title = { Text("Delete all data?") },
            text = { Text("This will remove all feeds, cached events, and conflict history. This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.wipeAllData()
                        showWipeConfirm = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showWipeConfirm = false }) { Text("Cancel") }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        // ---- Calendars link ----
        SectionHeader("Calendars")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Manage calendars", style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onOpenFeeds) {
                Text("Open")
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null)
            }
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))

        // ---- Sync interval ----
        SectionHeader("Background sync interval (FR-9)")
        SYNC_OPTIONS.forEach { (minutes, label) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                RadioButton(
                    selected = syncInterval == minutes,
                    onClick = { viewModel.setSyncInterval(minutes) },
                )
                Text(label, style = MaterialTheme.typography.bodyMedium)
            }
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))

        // ---- Conflict detection config ----
        SectionHeader("Conflict detection")
        Text(
            "Events with these statuses are treated as busy for conflict detection.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        SwitchRow(
            label = "Include TENTATIVE events",
            checked = includeTentative,
            onCheckedChange = viewModel::setIncludeTentative,
        )
        SwitchRow(
            label = "Include OUT OF OFFICE events",
            checked = includeOof,
            onCheckedChange = viewModel::setIncludeOof,
        )
        SwitchRow(
            label = "All-day events conflict with timed events",
            checked = allDayVsTimed,
            onCheckedChange = viewModel::setAllDayConflictsWithTimed,
            subtitle = "Off by default (FR-15)",
        )

        Spacer(Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))

        // ---- Conflict lookahead window ----
        SectionHeader("Conflict window")
        Text(
            "Conflicts highlighted on the home screen.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        LOOKAHEAD_OPTIONS.forEach { (days, label) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                RadioButton(
                    selected = lookaheadDays == days,
                    onClick = { viewModel.setConflictLookaheadDays(days) },
                )
                Text(label, style = MaterialTheme.typography.bodyMedium)
            }
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))

        // ---- Data management (NFR-6) ----
        SectionHeader("Data")
        Text(
            "Disconnecting individual calendars removes their events — see Calendars. The button below removes everything.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        OutlinedButton(
            onClick = { showWipeConfirm = true },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Delete all data")
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
