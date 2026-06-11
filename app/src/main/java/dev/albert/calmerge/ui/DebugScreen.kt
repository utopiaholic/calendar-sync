package dev.albert.calmerge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import dev.albert.calmerge.data.db.AccountEntity
import dev.albert.calmerge.data.db.AccountStatus
import dev.albert.calmerge.data.db.MergedEvent
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * M2 deliverable: a throwaway "CLI-style dump" of merged, deduped events with
 * per-feed sync status. Replaced by the real agenda view in M3.
 */
@Composable
fun DebugScreen(viewModel: MainViewModel) {
    val accounts by viewModel.accounts.collectAsState()
    val sources by viewModel.sources.collectAsState()
    val events by viewModel.mergedEvents.collectAsState()
    val syncing by viewModel.syncing.collectAsState()
    var showIcsDialog by remember { mutableStateOf(false) }

    if (showIcsDialog) {
        IcsDialog(
            onConfirm = { name, url ->
                viewModel.addIcsAccount(name, url)
                showIcsDialog = false
            },
            onDismiss = { showIcsDialog = false },
        )
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        item {
            Text("CalMerge debug", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                OutlinedButton(onClick = { showIcsDialog = true }) { Text("+ Add ICS feed") }
                Button(onClick = { viewModel.syncNow() }, enabled = !syncing && accounts.isNotEmpty()) {
                    Text(if (syncing) "Syncing…" else "Sync now")
                }
            }
        }

        items(accounts, key = { it.id }) { account ->
            AccountRow(account = account, onRemove = { viewModel.removeAccount(account) })
            // FR-5: per-feed include toggle.
            sources.filter { it.accountId == account.id }.forEach { source ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(start = 24.dp),
                ) {
                    Text(source.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    Switch(
                        checked = source.included,
                        onCheckedChange = { viewModel.setSourceIncluded(source.id, it) },
                    )
                }
            }
        }

        // FR-13: rows sharing a dedupeGroupId render once, badged with every account.
        val collapsed = collapseDuplicates(events)

        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text("Merged events (${collapsed.size})", style = MaterialTheme.typography.titleMedium)
        }
        items(collapsed, key = { it.first.event.id }) { (primary, allAccounts) ->
            EventRow(primary, allAccounts)
        }
    }
}

/** Groups duplicate events; returns the representative row plus every account it came from. */
internal fun collapseDuplicates(events: List<MergedEvent>): List<Pair<MergedEvent, List<MergedEvent>>> {
    val out = mutableListOf<Pair<MergedEvent, List<MergedEvent>>>()
    val seenGroups = mutableSetOf<String>()
    for (event in events) {
        val groupId = event.event.dedupeGroupId
        if (groupId == null) {
            out += event to listOf(event)
        } else if (seenGroups.add(groupId)) {
            out += event to events.filter { it.event.dedupeGroupId == groupId }
        }
    }
    return out
}

@Composable
private fun AccountRow(account: AccountEntity, onRemove: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Spacer(
            Modifier.size(10.dp).background(Color(account.color), CircleShape),
        )
        Spacer(Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(account.displayName, style = MaterialTheme.typography.bodyMedium)
            // FR-24: per-account last-synced + error state.
            val status = when (account.status) {
                AccountStatus.ACTIVE -> account.lastSyncUtc?.let { "Last synced: ${relativeMinutes(it)}" } ?: "Never synced"
                AccountStatus.NEEDS_REAUTH -> "Feed auth problem"
                AccountStatus.ERROR -> "Sync error: ${account.lastSyncError}"
            }
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = if (account.status == AccountStatus.ACTIVE) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            )
        }
        TextButton(onClick = onRemove) { Text("Remove") }
    }
}

private val timeFormat = DateTimeFormatter.ofPattern("EEE MMM d HH:mm")

@Composable
private fun EventRow(merged: MergedEvent, fromAccounts: List<MergedEvent>) {
    val zone = ZoneId.systemDefault()
    val event = merged.event
    val timeText = if (event.isAllDay) {
        "${event.startDate} (all day)"
    } else {
        val start = event.startUtc?.let { timeFormat.format(Instant.ofEpochMilli(it).atZone(zone)) } ?: "?"
        val end = event.endUtc?.let { DateTimeFormatter.ofPattern("HH:mm").format(Instant.ofEpochMilli(it).atZone(zone)) } ?: "?"
        "$start–$end"
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row {
            fromAccounts.distinctBy { it.accountId }.forEach {
                Spacer(Modifier.size(10.dp).background(Color(it.accountColor), CircleShape))
                Spacer(Modifier.width(2.dp))
            }
        }
        Spacer(Modifier.width(6.dp))
        Column {
            Text(event.title, style = MaterialTheme.typography.bodyMedium)
            val badge = fromAccounts.distinctBy { it.accountId }.joinToString(" + ") { it.accountName }
            Text(
                "$timeText · ${event.showAs} · $badge",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun IcsDialog(onConfirm: (String, String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add ICS feed") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name (e.g. Work A)") })
                OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("https://… .ics URL") })
            }
        },
        confirmButton = {
            // http is permitted only for the emulator host alias in debug builds
            // (see src/debug network_security_config); real feeds must be https.
            val urlOk = url.startsWith("https://") ||
                (dev.albert.calmerge.BuildConfig.DEBUG && url.startsWith("http://10.0.2.2"))
            TextButton(onClick = { onConfirm(name, url) }, enabled = urlOk) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun relativeMinutes(epochMs: Long): String {
    val minutes = (System.currentTimeMillis() - epochMs) / 60_000
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        else -> "${minutes / 60} h ${minutes % 60} min ago"
    }
}
