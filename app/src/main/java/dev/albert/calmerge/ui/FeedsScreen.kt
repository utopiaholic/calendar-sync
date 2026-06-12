package dev.albert.calmerge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.albert.calmerge.BuildConfig
import dev.albert.calmerge.data.db.AccountEntity
import dev.albert.calmerge.data.db.AccountStatus
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
        IcsDialog(
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

@Composable
private fun AccountRow(account: AccountEntity, now: Long, onRemove: () -> Unit, onColorClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        // FR-19: tapping the color dot opens the palette picker. 48dp touch target wraps the 22dp dot.
        Box(
            modifier = Modifier
                .size(48.dp)
                .semantics { role = Role.Button; contentDescription = "Change color for ${account.displayName}" }
                .clickable(onClick = onColorClick),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color(account.color)),
            )
        }
        Spacer(Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(account.displayName, style = MaterialTheme.typography.bodyMedium)
            val status = when (account.status) {
                AccountStatus.ACTIVE -> account.lastSyncUtc?.let { "Last synced: ${relativeMinutes(it, now)}" } ?: "Never synced"
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

@Composable
private fun IcsDialog(
    existingError: String?,
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit,
    onUrlChange: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    // Rewrite webcal:// to https:// before validation and before passing to the VM.
    val effectiveUrl = if (url.trim().startsWith("webcal://", ignoreCase = true)) {
        "https://" + url.trim().substringAfter("://")
    } else {
        url.trim()
    }

    val urlError: String? = run {
        val u = effectiveUrl
        if (u.isEmpty()) return@run null // no error while empty
        val isHttps = u.startsWith("https://")
        val isDebugHttp = BuildConfig.DEBUG && run {
            // Allow http only for emulator loopback hosts.
            if (!u.startsWith("http://")) return@run false
            val authority = try {
                java.net.URI(u).host ?: ""
            } catch (_: Exception) { "" }
            authority == "localhost" || authority == "10.0.2.2"
        }
        when {
            !isHttps && !isDebugHttp -> "URL must start with https://"
            else -> {
                val host = try { java.net.URI(u).host } catch (_: Exception) { null }
                if (host.isNullOrEmpty()) "Enter a valid URL with a host" else null
            }
        }
    }

    val urlOk = effectiveUrl.isNotEmpty() && urlError == null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add ICS feed") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name (e.g. Work A)") })
                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it
                        onUrlChange()
                    },
                    label = { Text("https:// or webcal:// URL") },
                    isError = urlError != null || existingError != null,
                    supportingText = when {
                        existingError != null -> {
                            { Text(existingError, color = MaterialTheme.colorScheme.error) }
                        }
                        urlError != null -> {
                            { Text(urlError, color = MaterialTheme.colorScheme.error) }
                        }
                        else -> null
                    },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, effectiveUrl) }, enabled = urlOk) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorPickerDialog(
    palette: List<Int>,
    currentColor: Int,
    onColorSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pick a color") },
        text = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                palette.forEach { color ->
                    val selected = color == currentColor
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(color))
                            .then(
                                if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                else Modifier
                            )
                            .clickable { onColorSelected(color) },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun relativeMinutes(epochMs: Long, now: Long = System.currentTimeMillis()): String {
    val minutes = (now - epochMs) / 60_000
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        else -> "${minutes / 60} h ${minutes % 60} min ago"
    }
}
