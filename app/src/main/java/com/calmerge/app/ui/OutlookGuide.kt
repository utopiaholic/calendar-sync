package com.calmerge.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.calmerge.app.sync.DeviceCalendar
import com.calmerge.app.sync.LocalCalendarStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun OutlookGuide(
    onCalendarsFound: () -> Unit,
    onUseLink: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember { LocalCalendarStore(context) }
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var stillNotFound by remember { mutableStateOf(false) }

    val outlookLaunchIntent = remember {
        context.packageManager.getLaunchIntentForPackage(OUTLOOK_PACKAGE)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    var resumeCount by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeCount++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Auto-detect Outlook calendars when returning from the Outlook app
    LaunchedEffect(resumeCount) {
        if (resumeCount <= 1) return@LaunchedEffect
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_CALENDAR,
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return@LaunchedEffect
        val cals = withContext(Dispatchers.IO) { store.listCalendars() }
        if (cals.any { isOutlookCalendar(it) }) onCalendarsFound()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Add Outlook calendar", style = MaterialTheme.typography.titleLarge)
                    OutlinedButton(onClick = onDismiss) { Text("Cancel") }
                }
                HorizontalDivider()

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 32.dp),
                    verticalArrangement = Arrangement.Top,
                ) {
                    Text(
                        "Outlook keeps its calendars private by default. To share them with Android:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(20.dp))
                    InstructionSteps(
                        listOf(
                            "Open Outlook",
                            "Tap your profile picture (top left)",
                            "Tap the Settings gear (bottom left)",
                            "Tap Calendar",
                            "Tap Sync Calendars",
                            "Turn on the calendars you want to sync",
                            "Return to CalMerge",
                        ),
                    )
                    if (stillNotFound) {
                        Spacer(Modifier.height(20.dp))
                        Text(
                            "Outlook calendars still not showing. Make sure Sync Calendars is on and try again, or use a calendar link instead.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                HorizontalDivider()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 260.dp)
                        .padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 56.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (outlookLaunchIntent != null) {
                        Button(
                            onClick = { context.startActivity(outlookLaunchIntent) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Open Outlook") }
                    }
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                checking = true
                                stillNotFound = false
                                val hasPermission = ContextCompat.checkSelfPermission(
                                    context, Manifest.permission.READ_CALENDAR,
                                ) == PackageManager.PERMISSION_GRANTED
                                if (!hasPermission) {
                                    // Permission not yet granted — open the picker which handles it
                                    onCalendarsFound()
                                    return@launch
                                }
                                val cals = withContext(Dispatchers.IO) { store.listCalendars() }
                                if (cals.any { isOutlookCalendar(it) }) {
                                    onCalendarsFound()
                                } else {
                                    stillNotFound = true
                                    checking = false
                                }
                            }
                        },
                        enabled = !checking,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (checking) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text("I turned it on")
                        }
                    }
                    TextButton(
                        onClick = onUseLink,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) { Text("Use calendar link instead") }
                }
            }
        }
    }
}

internal fun isOutlookCalendar(cal: DeviceCalendar) =
    cal.accountType.contains("exchange", ignoreCase = true) ||
    cal.accountType.contains("outlook", ignoreCase = true) ||
    cal.accountType.contains("microsoft", ignoreCase = true)
