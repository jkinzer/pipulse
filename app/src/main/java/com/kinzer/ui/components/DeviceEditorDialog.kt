package com.kinzer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kinzer.data.PiholeApiVersion
import com.kinzer.data.PiholeConnectionResult
import com.kinzer.data.PiholeDeviceProfile
import com.kinzer.ui.theme.PiPulseThemeTokens

@Composable
fun DeviceEditorDialog(
    editingDevice: PiholeDeviceProfile?,
    testLoading: Boolean,
    testResult: PiholeConnectionResult?,
    onDismiss: () -> Unit,
    onTestConnection: (
        editingDeviceId: String?,
        name: String,
        host: String,
        port: Long,
        useHttps: Boolean,
        apiVersion: PiholeApiVersion,
        basePath: String,
        secret: String
    ) -> Unit,
    onSave: (
        name: String,
        host: String,
        port: Long,
        useHttps: Boolean,
        apiVersion: PiholeApiVersion,
        basePath: String,
        locationTag: String,
        notes: String,
        secret: String
    ) -> Unit
) {
    var name by remember(editingDevice) { mutableStateOf(editingDevice?.name.orEmpty()) }
    var host by remember(editingDevice) { mutableStateOf(editingDevice?.host.orEmpty()) }
    var useHttps by remember(editingDevice) { mutableStateOf(editingDevice?.useHttps ?: false) }
    var portText by remember(editingDevice) {
        mutableStateOf((editingDevice?.port ?: 80L).toString())
    }
    var apiVersion by remember(editingDevice) {
        mutableStateOf(editingDevice?.parsedApiVersion ?: PiholeApiVersion.V6)
    }
    var basePath by remember(editingDevice) { mutableStateOf(editingDevice?.basePath.orEmpty()) }
    var locationTag by remember(editingDevice) {
        mutableStateOf(editingDevice?.locationTag?.ifBlank { "Home LAN" } ?: "Home LAN")
    }
    var notes by remember(editingDevice) { mutableStateOf(editingDevice?.notes.orEmpty()) }
    var secretInput by remember(editingDevice) { mutableStateOf("") }
    var showSecret by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val extended = PiPulseThemeTokens.extended
    val parsedPort = portText.toLongOrNull()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 24.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (editingDevice == null) "Add Pi-hole Device" else "Edit Pi-hole Device",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Stored locally on device; credentials stay AES-256 encrypted",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("device_dialog_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close dialog"
                        )
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(60); validationError = null },
                    label = { Text("Device Label") },
                    placeholder = { Text("e.g. Rack Primary Pi-hole") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("device_name_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it.take(255); validationError = null },
                        label = { Text("Host / IP") },
                        placeholder = { Text("192.168.1.2") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(2.2f)
                            .testTag("device_host_input")
                    )
                    OutlinedTextField(
                        value = portText,
                        onValueChange = { input ->
                            portText = input.filter { it.isDigit() }.take(5)
                            validationError = null
                        },
                        label = { Text("Port") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("device_port_input")
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Use HTTPS (TLS)",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Recommended for remote VPN or reverse-proxy setups",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = useHttps,
                        onCheckedChange = { enabled ->
                            useHttps = enabled
                            if (enabled && portText == "80") portText = "443"
                            if (!enabled && portText == "443") portText = "80"
                        },
                        modifier = Modifier.testTag("device_https_switch")
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Pi-hole API Version",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PiholeApiVersion.entries.forEach { ver ->
                            FilterChip(
                                selected = apiVersion == ver,
                                onClick = { apiVersion = ver },
                                label = { Text(ver.displayLabel) },
                                modifier = Modifier.testTag("api_version_chip_${ver.wireValue}")
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = secretInput,
                    onValueChange = { secretInput = it },
                    label = {
                        Text(
                            if (apiVersion == PiholeApiVersion.V6) "App Password / Web Password"
                            else "Pi-hole v5 API Token"
                        )
                    },
                    placeholder = {
                        Text(
                            if (editingDevice?.hasStoredCredential == true) {
                                "Stored securely (leave blank to keep)"
                            } else {
                                "Optional if passwordless"
                            }
                        )
                    },
                    singleLine = true,
                    visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = "Encrypted credential"
                        )
                    },
                    trailingIcon = {
                        IconButton(onClick = { showSecret = !showSecret }) {
                            Icon(
                                imageVector = if (showSecret) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = if (showSecret) "Hide secret" else "Show secret"
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("device_secret_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = locationTag,
                        onValueChange = { locationTag = it.take(40) },
                        label = { Text("Network Tag") },
                        placeholder = { Text("Home LAN / VPN") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = basePath,
                        onValueChange = { basePath = it.take(64) },
                        label = { Text("Base Path") },
                        placeholder = { Text("Optional") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it.take(240) },
                    label = { Text("Notes (Optional)") },
                    placeholder = { Text("e.g. Raspberry Pi 5 primary DNS") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                if (validationError != null) {
                    Text(
                        text = validationError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                if (testResult != null) {
                    val container = if (testResult.connected) extended.permittedContainer else extended.blockedContainer
                    val content = if (testResult.connected) extended.onPermittedContainer else extended.onBlockedContainer
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(container)
                            .border(1.dp, content.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = if (testResult.connected) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                            contentDescription = "Connection status",
                            tint = content
                        )
                        Text(
                            text = testResult.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = content
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val portVal = parsedPort
                            if (host.isBlank() || portVal == null || portVal !in 1..65535) {
                                validationError = "Enter a valid host and port (1–65535) before testing."
                                return@OutlinedButton
                            }
                            onTestConnection(
                                editingDevice?.id,
                                name,
                                host,
                                portVal,
                                useHttps,
                                apiVersion,
                                basePath,
                                secretInput
                            )
                        },
                        enabled = !testLoading,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("test_connection_button")
                    ) {
                        if (testLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.NetworkCheck,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Test")
                        }
                    }

                    Button(
                        onClick = {
                            val portVal = parsedPort
                            when {
                                name.trim().isEmpty() -> validationError = "Please enter a device label."
                                host.trim().isEmpty() -> validationError = "Please enter the Pi-hole host or IP."
                                portVal == null || portVal !in 1..65535 -> validationError = "Port must be between 1 and 65535."
                                else -> {
                                    onSave(
                                        name.trim(),
                                        host.trim(),
                                        portVal,
                                        useHttps,
                                        apiVersion,
                                        basePath.trim(),
                                        locationTag.trim(),
                                        notes.trim(),
                                        secretInput
                                    )
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1.3f)
                            .height(48.dp)
                            .testTag("save_device_button")
                    ) {
                        Text(if (editingDevice == null) "Save Device" else "Update Device")
                    }
                }
            }
        }
    }
}
