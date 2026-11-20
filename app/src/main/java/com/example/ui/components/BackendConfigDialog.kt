package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.remote.config.ApiConfig
import com.example.data.remote.config.AuthExecutionMode

@Composable
fun BackendStatusBar(
    authExecutionMode: AuthExecutionMode,
    backendBaseUrl: String,
    isBackendConfigured: Boolean,
    onConfigureClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDevLocal = authExecutionMode == AuthExecutionMode.DEVELOPMENT_LOCAL_MODE
    val bgColor = when {
        isDevLocal -> Color(0xFFFEF3C7)
        isBackendConfigured -> Color(0xFFDCFCE7)
        else -> Color(0xFFEFF6FF)
    }
    val borderColor = when {
        isDevLocal -> Color(0xFFF59E0B).copy(alpha = 0.45f)
        isBackendConfigured -> Color(0xFF16A34A).copy(alpha = 0.45f)
        else -> Color(0xFF3B82F6).copy(alpha = 0.35f)
    }
    val accentColor = when {
        isDevLocal -> Color(0xFFB45309)
        isBackendConfigured -> Color(0xFF15803D)
        else -> Color(0xFF1D4ED8)
    }
    val statusTitle = when {
        isDevLocal -> "Development Local Mode (Room Offline)"
        isBackendConfigured -> "Backend HTTP API • Configured"
        else -> "Backend HTTP API • URL Not Configured"
    }
    val statusSubtitle = when {
        isDevLocal -> "Local DB auth enabled for dev testing"
        isBackendConfigured -> backendBaseUrl
        else -> "Tap Configure to enter Spring Boot URL or switch to Dev Mode"
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
            .fillMaxWidth()
            .clickable { onConfigureClick() }
            .testTag("backend_status_bar")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = when {
                        isDevLocal -> Icons.Default.DeveloperMode
                        isBackendConfigured -> Icons.Default.CloudDone
                        else -> Icons.Default.CloudOff
                    },
                    contentDescription = "Backend environment status",
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = statusTitle,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = statusSubtitle,
                        fontSize = 10.5.sp,
                        color = accentColor.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = accentColor.copy(alpha = 0.14f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Configure",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                }
            }
        }
    }
}

@Composable
fun BackendConfigDialog(
    currentMode: AuthExecutionMode,
    currentBaseUrl: String,
    onDismiss: () -> Unit,
    onSelectMode: (AuthExecutionMode) -> Unit,
    onSaveBaseUrl: (String) -> Unit
) {
    var urlInput by remember(currentBaseUrl) { mutableStateOf(currentBaseUrl) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Backend & Environment Config",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Authentication & Data Mode",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = currentMode == AuthExecutionMode.PRODUCTION_BACKEND_HTTP,
                        onClick = { onSelectMode(AuthExecutionMode.PRODUCTION_BACKEND_HTTP) },
                        label = { Text("HTTP API", fontSize = 12.sp) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("mode_chip_http")
                    )
                    FilterChip(
                        selected = currentMode == AuthExecutionMode.DEVELOPMENT_LOCAL_MODE,
                        onClick = { onSelectMode(AuthExecutionMode.DEVELOPMENT_LOCAL_MODE) },
                        label = { Text("Dev Local", fontSize = 12.sp) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("mode_chip_dev_local")
                    )
                }
                Text(
                    text = currentMode.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Spring Boot Base URL",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    label = { Text("Base URL (http:// or https://)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("backend_url_input")
                )

                Text(
                    text = "Quick Presets:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = { urlInput = ApiConfig.EMULATOR_DEFAULT_BASE_URL },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Emulator", fontSize = 11.sp)
                    }
                    OutlinedButton(
                        onClick = { urlInput = ApiConfig.PHYSICAL_DEVICE_PLACEHOLDER_URL },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Device LAN", fontSize = 11.sp)
                    }
                }
                OutlinedButton(
                    onClick = { urlInput = ApiConfig.PLACEHOLDER_UNCONFIGURED_BASE_URL },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Reset to Unconfigured Placeholder", fontSize = 11.sp)
                }

                Text(
                    text = "Note: Cleartext HTTP is only allowed for local development hosts (10.0.2.2, localhost, private LAN). Production requires HTTPS.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSaveBaseUrl(urlInput)
                    onDismiss()
                },
                modifier = Modifier.testTag("save_backend_config_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
