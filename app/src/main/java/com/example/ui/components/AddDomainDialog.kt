package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.DomainListType
import com.example.ui.theme.PiPulseThemeTokens

@Composable
fun AddDomainDialog(
    initialListType: DomainListType,
    deviceCount: Int,
    activeDeviceName: String?,
    defaultApplyToAll: Boolean,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (domain: String, listType: DomainListType, regexRule: Boolean, comment: String, applyToAllDevices: Boolean) -> Unit
) {
    var domain by remember { mutableStateOf("") }
    var comment by remember { mutableStateOf("") }
    var listType by remember(initialListType) { mutableStateOf(initialListType) }
    var isRegex by remember { mutableStateOf(false) }
    var applyToAll by remember(defaultApplyToAll, deviceCount) {
        mutableStateOf(defaultApplyToAll && deviceCount > 1)
    }
    var errorText by remember { mutableStateOf<String?>(null) }

    val extended = PiPulseThemeTokens.extended
    val actionColor = if (listType == DomainListType.ALLOW) extended.permitted else extended.blocked

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 20.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Add Domain Rule",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("add_domain_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close add domain dialog"
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Target List",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilterChip(
                            selected = listType == DomainListType.ALLOW,
                            onClick = { listType = DomainListType.ALLOW },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircleOutline,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            label = { Text("Whitelist (Allow)") },
                            modifier = Modifier.testTag("add_domain_whitelist_chip")
                        )
                        FilterChip(
                            selected = listType == DomainListType.DENY,
                            onClick = { listType = DomainListType.DENY },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Block,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            label = { Text("Blacklist (Deny)") },
                            modifier = Modifier.testTag("add_domain_blacklist_chip")
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Match Mode",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilterChip(
                            selected = !isRegex,
                            onClick = { isRegex = false },
                            label = { Text("Exact Domain") },
                            modifier = Modifier.testTag("add_domain_exact_chip")
                        )
                        FilterChip(
                            selected = isRegex,
                            onClick = { isRegex = true },
                            label = { Text("Regex Pattern") },
                            modifier = Modifier.testTag("add_domain_regex_chip")
                        )
                    }
                }

                OutlinedTextField(
                    value = domain,
                    onValueChange = { domain = it.take(255); errorText = null },
                    label = { Text(if (isRegex) "Regular Expression" else "Domain Name") },
                    placeholder = {
                        Text(if (isRegex) "(\\.|^)ads\\.example\\.com$" else "metrics.example.com")
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_domain_input")
                )

                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it.take(120) },
                    label = { Text("Comment (Optional)") },
                    placeholder = { Text("e.g. Required for smart TV streaming") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_domain_comment_input")
                )

                if (deviceCount > 1) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Target Scope",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            FilterChip(
                                selected = applyToAll,
                                onClick = { applyToAll = true },
                                label = { Text("All $deviceCount Pi-holes") },
                                modifier = Modifier.testTag("add_domain_all_devices_chip")
                            )
                            if (activeDeviceName != null) {
                                FilterChip(
                                    selected = !applyToAll,
                                    onClick = { applyToAll = false },
                                    label = { Text(activeDeviceName) },
                                    modifier = Modifier.testTag("add_domain_single_device_chip")
                                )
                            }
                        }
                    }
                }

                if (errorText != null) {
                    Text(
                        text = errorText!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = {
                        val cleaned = domain.trim()
                        if (cleaned.isEmpty() || cleaned.contains(" ")) {
                            errorText = "Enter a valid domain or regex expression without spaces."
                            return@Button
                        }
                        onSubmit(cleaned, listType, isRegex, comment.trim(), applyToAll)
                    },
                    enabled = !isSubmitting,
                    colors = ButtonDefaults.buttonColors(containerColor = actionColor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("submit_add_domain_button")
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        val targetSuffix = if (applyToAll && deviceCount > 1) " (All $deviceCount)" else ""
                        Text(
                            text = (if (listType == DomainListType.ALLOW) "Add to Whitelist" else "Add to Blacklist") + targetSuffix
                        )
                    }
                }
            }
        }
    }
}
