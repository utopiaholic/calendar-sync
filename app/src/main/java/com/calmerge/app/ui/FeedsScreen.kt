package com.calmerge.app.ui

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.window.Dialog
import com.calmerge.app.data.db.AccountEntity
import kotlinx.coroutines.delay

/** Calendar management: add from phone or ICS link, include toggles, per-account sync status. */
@Composable
fun FeedsScreen(viewModel: MainViewModel) {
    val accounts by viewModel.accounts.collectAsState()
    val sources by viewModel.sources.collectAsState()
    val syncing by viewModel.syncing.collectAsState()
    val addFeedError by viewModel.addFeedError.collectAsState()
    val addFeedSuccess by viewModel.addFeedSuccess.collectAsState()

    var showChooser by remember { mutableStateOf(false) }
    var showIcsDialog by remember { mutableStateOf(false) }
    var showLocalPicker by remember { mutableStateOf(false) }
    var showOutlookGuide by remember { mutableStateOf(false) }
    var removeTarget by remember { mutableStateOf<AccountEntity?>(null) }
    var colorPickerTarget by remember { mutableStateOf<AccountEntity?>(null) }

    LaunchedEffect(addFeedSuccess) {
        if (addFeedSuccess) {
            showIcsDialog = false
            showLocalPicker = false
            viewModel.consumeAddFeedSuccess()
        }
    }

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

    if (showChooser) {
        AddCalendarChooser(
            onOutlook = {
                showChooser = false
                showOutlookGuide = true
            },
            onFromPhone = {
                showChooser = false
                showLocalPicker = true
            },
            onFromLink = {
                showChooser = false
                showIcsDialog = true
            },
            onDismiss = { showChooser = false },
        )
    }

    if (showOutlookGuide) {
        OutlookGuide(
            onCalendarsFound = {
                showOutlookGuide = false
                showLocalPicker = true
            },
            onUseLink = {
                showOutlookGuide = false
                showIcsDialog = true
            },
            onDismiss = { showOutlookGuide = false },
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

    if (showLocalPicker) {
        LocalCalendarPicker(
            viewModel = viewModel,
            alreadyImportedSourceIds = sources.map { it.providerCalendarId }.toSet(),
            onNeedHelp = {
                showLocalPicker = false
                showOutlookGuide = true
            },
            onDismiss = { showLocalPicker = false },
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
                OutlinedButton(onClick = { showChooser = true }) { Text("+ Add calendar") }
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
            val accountSources = sources.filter { it.accountId == account.id }
            accountSources.forEach { source ->
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
            if (accountSources.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun AddCalendarChooser(
    onOutlook: () -> Unit,
    onFromPhone: () -> Unit,
    onFromLink: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
                .padding(horizontal = 24.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier.padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 12.dp),
            ) {
                Text("Add calendar", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(20.dp))
                ChooserOption(
                    iconPackageName = OUTLOOK_PACKAGE,
                    title = "Outlook calendar",
                    subtitle = "Use the Outlook app or a published calendar link.",
                    onClick = onOutlook,
                )
                HorizontalDivider()
                ChooserOption(
                    iconPackageName = GOOGLE_CALENDAR_PACKAGE,
                    title = "Google & phone calendars",
                    subtitle = "Import calendars already synced to Android.",
                    onClick = onFromPhone,
                )
                HorizontalDivider()
                ChooserOption(
                    iconPackageName = null,
                    title = "Paste calendar link",
                    subtitle = "Use an ICS/iCal subscription URL.",
                    onClick = onFromLink,
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                }
            }
        }
    }
}

@Composable
private fun ChooserOption(
    iconPackageName: String?,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(
                packageName = iconPackageName,
                contentDescription = null,
                size = 34.dp,
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
