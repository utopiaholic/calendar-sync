package com.calmerge.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.calmerge.app.data.db.AccountEntity
import kotlinx.coroutines.delay

/** Feed management: add/remove ICS feeds, include toggles, per-feed sync status (FR-5, FR-24). */
@Composable
fun FeedsScreen(viewModel: MainViewModel) {
    val accounts by viewModel.accounts.collectAsState()
    val sources by viewModel.sources.collectAsState()
    val syncing by viewModel.syncing.collectAsState()
    val addFeedError by viewModel.addFeedError.collectAsState()
    val addFeedSuccess by viewModel.addFeedSuccess.collectAsState()
    var showIcsDialog by remember { mutableStateOf(false) }
    var removeTarget by remember { mutableStateOf<AccountEntity?>(null) }
    var colorPickerTarget by remember { mutableStateOf<AccountEntity?>(null) }

    // Close the dialog when the VM signals a successful insert.
    LaunchedEffect(addFeedSuccess) {
        if (addFeedSuccess) {
            showIcsDialog = false
            viewModel.consumeAddFeedSuccess()
        }
    }

    // Minute ticker so "Last synced" labels refresh without a full recompose trigger.
    val now by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            delay(60_000)
            value = System.currentTimeMillis()
        }
    }

    colorPickerTarget?.let { account ->
        ColorPickerDialog(
            palette = viewModel.palette,
            currentColor = account.color,
            onColorSelected = { color ->
                viewModel.updateAccountColor(account.id, color)
                colorPickerTarget = null
            },
            onDismiss = { colorPickerTarget = null },
        )
    }

    if (showIcsDialog) {
        AddIcsFeedDialog(
            existingError = addFeedError,
            onConfirm = { name, url -> viewModel.addIcsAccount(name, url) },
            onDismiss = {
                viewModel.clearAddFeedError()
                showIcsDialog = false
            },
            onUrlChange = { viewModel.clearAddFeedError() },
        )
    }

    removeTarget?.let { account ->
        AlertDialog(
            onDismissRequest = { removeTarget = null },
            title = { Text("Remove ${account.displayName}?") },
            text = { Text("Cached events will be deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeAccount(account)
                    removeTarget = null
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { removeTarget = null }) { Text("Cancel") }
            },
        )
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                OutlinedButton(onClick = { showIcsDialog = true }) { Text("+ Add ICS feed") }
                Button(onClick = { viewModel.syncNow() }, enabled = !syncing && accounts.isNotEmpty()) {
                    Text(if (syncing) "Syncing…" else "Sync now")
                }
            }
        }

        items(accounts, key = { it.id }) { account ->
            AccountRow(
                account = account,
                now = now,
                onRemove = { removeTarget = account },
                onColorClick = { colorPickerTarget = account },
            )
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
    }
}
