package com.calmerge.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.calmerge.app.BuildConfig

@Composable
internal fun AddIcsFeedDialog(
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
            val authority = urlHostOrNull(u).orEmpty()
            authority == "localhost" || authority == "10.0.2.2"
        }
        when {
            !isHttps && !isDebugHttp -> "URL must start with https://"
            else -> {
                val host = urlHostOrNull(u)
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
