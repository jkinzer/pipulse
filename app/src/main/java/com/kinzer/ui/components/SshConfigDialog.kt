package com.kinzer.ui.components

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.UploadFile
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kinzer.data.PiholeConnectionResult
import com.kinzer.data.PiholeDeviceProfile
import com.kinzer.data.SshFleetConfig
import com.kinzer.data.SshKeyValidationResult
import com.kinzer.ui.theme.JetBrainsMonoFontFamily
import com.kinzer.ui.theme.PiPulseThemeTokens
import java.nio.charset.StandardCharsets

@Composable
fun SshConfigDialog(
    initialConfig: SshFleetConfig,
    sampleTestDevice: PiholeDeviceProfile?,
    testLoading: Boolean,
    testResult: PiholeConnectionResult?,
    onValidateKey: (keyPem: String, passphrase: String) -> SshKeyValidationResult,
    onTestSsh: (
        device: PiholeDeviceProfile,
        username: String,
        port: Int,
        newKeyPem: String?,
        passphraseInput: String
    ) -> Unit,
    onClearTrustedHosts: () -> Unit,
    onRemoveStoredKey: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (
        username: String,
        port: Int,
        scriptPath: String,
        newKeyPem: String?,
        keyFileName: String,
        passphraseInput: String,
        rebootTimeoutSeconds: Int
    ) -> Unit
) {
    val context = LocalContext.current
    val extended = PiPulseThemeTokens.extended

    var username by remember(initialConfig) {
        mutableStateOf(initialConfig.username.ifBlank { "jeff" })
    }
    var portText by remember(initialConfig) {
        mutableStateOf(initialConfig.port.toString())
    }
    var scriptPath by remember(initialConfig) {
        mutableStateOf(initialConfig.scriptPath)
    }
    var rebootTimeoutSec by remember(initialConfig) {
        mutableIntStateOf(initialConfig.rebootTimeoutSeconds)
    }
    var newKeyPem by remember { mutableStateOf<String?>(null) }
    var keyFileName by remember(initialConfig) {
        mutableStateOf(initialConfig.keyFileName)
    }
    var passphrase by remember { mutableStateOf("") }
    var showPassphrase by remember { mutableStateOf(false) }
    var showPasteBox by remember { mutableStateOf(false) }
    var keyStatusNote by remember { mutableStateOf<String?>(null) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val keyFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val loaded = readSshKeyFromUri(context, uri)
        if (loaded == null) {
            validationError = "Could not read selected file (max 64 KB UTF-8 key)."
            return@rememberLauncherForActivityResult
        }
        val (fileName, keyContent) = loaded
        newKeyPem = keyContent
        keyFileName = fileName
        showPasteBox = false
        val check = onValidateKey(keyContent, passphrase)
        if (check.valid) {
            validationError = null
            keyStatusNote = "$fileName • ${check.message}"
        } else if (check.encrypted) {
            validationError = null
            keyStatusNote = "$fileName loaded (enter passphrase below)"
        } else {
            keyStatusNote = null
            validationError = check.message
        }
    }

    val hasEffectiveKey = !newKeyPem.isNullOrBlank() || initialConfig.hasPrivateKey
    val parsedPort = portText.toIntOrNull()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 20.dp),
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "SSH Rolling Update Config",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Shared OpenSSH key & script for sequential server updates",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("ssh_config_close_button")
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close SSH config dialog")
                    }
                }

                // SSH Private Key Section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Key,
                                contentDescription = null,
                                tint = if (hasEffectiveKey) extended.permitted else extended.warning,
                                modifier = Modifier.size(18.dp)
                            )
                            Column {
                                Text(
                                    text = "SSH Private Key (id_rsa)",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = when {
                                        keyStatusNote != null -> keyStatusNote!!
                                        initialConfig.hasPrivateKey ->
                                            "${initialConfig.keyFileName.ifBlank { "id_rsa" }} • AES-256 encrypted in Keystore"
                                        else -> "OpenSSH / PEM private key required"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (hasEffectiveKey) extended.permitted else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (initialConfig.hasPrivateKey && newKeyPem == null) {
                            IconButton(
                                onClick = {
                                    onRemoveStoredKey()
                                    keyStatusNote = null
                                    keyFileName = ""
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("ssh_remove_key_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = "Remove stored SSH key",
                                    tint = extended.blocked
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { keyFilePicker.launch(arrayOf("*/*")) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("ssh_upload_key_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.UploadFile,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (hasEffectiveKey) "Replace Key File" else "Upload Key File",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        TextButton(
                            onClick = { showPasteBox = !showPasteBox },
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("ssh_paste_key_toggle")
                        ) {
                            Text(if (showPasteBox) "Hide Paste" else "Paste")
                        }
                    }

                    if (showPasteBox) {
                        OutlinedTextField(
                            value = newKeyPem.orEmpty(),
                            onValueChange = { pasted ->
                                newKeyPem = pasted
                                if (keyFileName.isBlank()) keyFileName = "id_rsa"
                                validationError = null
                                keyStatusNote = if (pasted.contains("PRIVATE KEY")) {
                                    "Pasted OpenSSH key ready to save"
                                } else null
                            },
                            label = { Text("Paste OpenSSH Private Key") },
                            placeholder = { Text("-----BEGIN OPENSSH PRIVATE KEY-----") },
                            minLines = 3,
                            maxLines = 5,
                            textStyle = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMonoFontFamily
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ssh_paste_key_input")
                        )
                    }
                }

                // Passphrase (optional)
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it; validationError = null },
                    label = { Text("Key Passphrase (Optional)") },
                    placeholder = {
                        Text(
                            if (initialConfig.hasPassphrase && newKeyPem == null) {
                                "Stored securely (leave blank to keep)"
                            } else {
                                "Leave blank if id_rsa has no passphrase"
                            }
                        )
                    },
                    singleLine = true,
                    visualTransformation = if (showPassphrase) VisualTransformation.None else PasswordVisualTransformation(),
                    leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { showPassphrase = !showPassphrase }) {
                            Icon(
                                imageVector = if (showPassphrase) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = if (showPassphrase) "Hide passphrase" else "Show passphrase"
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ssh_passphrase_input")
                )

                // Username & Port
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it.take(64); validationError = null },
                        label = { Text("SSH User") },
                        placeholder = { Text("jeff") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(2f)
                            .testTag("ssh_username_input")
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
                            .testTag("ssh_port_input")
                    )
                }

                // Update Script Path
                OutlinedTextField(
                    value = scriptPath,
                    onValueChange = { scriptPath = it.take(255); validationError = null },
                    label = { Text("Update Script Path (Same on All Servers)") },
                    placeholder = { Text("/home/jeff/update.sh") },
                    leadingIcon = { Icon(Icons.Rounded.Terminal, contentDescription = null) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = JetBrainsMonoFontFamily
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ssh_script_path_input")
                )

                // Reboot Wait Timeout
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Max Reboot Wait Per Server",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(180 to "3 min", 240 to "4 min", 360 to "6 min").forEach { (secs, label) ->
                            FilterChip(
                                selected = rebootTimeoutSec == secs,
                                onClick = { rebootTimeoutSec = secs },
                                label = { Text(label) },
                                modifier = Modifier.testTag("ssh_timeout_chip_$secs")
                            )
                        }
                    }
                }

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
                            contentDescription = "SSH test result",
                            tint = content
                        )
                        Text(
                            text = testResult.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = content
                        )
                    }
                }

                // Action Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (sampleTestDevice != null) {
                        OutlinedButton(
                            onClick = {
                                val p = parsedPort
                                when {
                                    username.trim().isEmpty() -> validationError = "Enter an SSH username."
                                    p == null || p !in 1..65535 -> validationError = "SSH port must be 1–65535."
                                    !hasEffectiveKey -> validationError = "Upload or paste your id_rsa private key first."
                                    else -> {
                                        validationError = null
                                        onTestSsh(
                                            sampleTestDevice,
                                            username.trim(),
                                            p,
                                            newKeyPem,
                                            passphrase
                                        )
                                    }
                                }
                            },
                            enabled = !testLoading,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("ssh_test_connection_button")
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
                                Text("Test SSH")
                            }
                        }
                    }

                    Button(
                        onClick = {
                            val p = parsedPort
                            when {
                                username.trim().isEmpty() -> validationError = "SSH username is required."
                                p == null || p !in 1..65535 -> validationError = "Port must be between 1 and 65535."
                                scriptPath.trim().isEmpty() -> validationError = "Enter the update script path."
                                !hasEffectiveKey -> validationError = "Upload or paste your SSH private key."
                                else -> {
                                    val candidateKey = newKeyPem
                                    if (!candidateKey.isNullOrBlank()) {
                                        val check = onValidateKey(candidateKey, passphrase)
                                        if (!check.valid) {
                                            validationError = check.message
                                            return@Button
                                        }
                                    }
                                    onSave(
                                        username.trim(),
                                        p,
                                        scriptPath.trim(),
                                        candidateKey,
                                        keyFileName.ifBlank { "id_rsa" },
                                        passphrase,
                                        rebootTimeoutSec
                                    )
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1.25f)
                            .height(48.dp)
                            .testTag("ssh_save_config_button")
                    ) {
                        Text("Save Config")
                    }
                }

                TextButton(
                    onClick = onClearTrustedHosts,
                    modifier = Modifier
                        .align(Alignment.End)
                        .testTag("ssh_clear_host_keys_button")
                ) {
                    Text(
                        text = "Reset Trusted Host Keys",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun readSshKeyFromUri(context: Context, uri: Uri): Pair<String, String>? {
    return runCatching {
        var displayName = "id_rsa"
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) {
                    displayName = cursor.getString(idx)?.takeIf { it.isNotBlank() } ?: "id_rsa"
                }
            }
        }
        val bytes = context.contentResolver.openInputStream(uri)?.use { stream ->
            val maxBytes = 64 * 1024
            val buffer = stream.readNBytesCompat(maxBytes + 1)
            if (buffer.size > maxBytes) return null
            buffer
        } ?: return null
        displayName to String(bytes, StandardCharsets.UTF_8)
    }.getOrNull()
}

private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val chunk = ByteArray(4096)
    var total = 0
    while (total < limit) {
        val read = read(chunk, 0, minOf(chunk.size, limit - total))
        if (read <= 0) break
        out.write(chunk, 0, read)
        total += read
    }
    return out.toByteArray()
}
