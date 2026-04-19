package com.selkacraft.alice.ui.screens.settings.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.selkacraft.alice.ui.screens.settings.components.SettingsCard

@Composable
fun NetworkSettingsTab(
    isConnected: Boolean,
    serverIp: String,
    serverPort: Int,
    remoteMotorPosition: Int,
    remoteDepth: Float,
    remoteFocusMode: String,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onScanQr: () -> Unit,
    onServerIpChanged: (String) -> Unit,
    onServerPortChanged: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

        // Connection status
        SettingsCard(
            title = "Connection Status",
            containerColor = if (isConnected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            else
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Status", style = MaterialTheme.typography.bodyMedium)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (isConnected) "Connected" else "Not Connected",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isConnected)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isConnected) {
                        FilledTonalButton(onClick = onDisconnect) {
                            Icon(Icons.Default.LinkOff, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Disconnect")
                        }
                    }
                }
            }
            if (isConnected && serverIp.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Server", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "$serverIp:$serverPort",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }

        // Remote state (when connected)
        if (isConnected) {
            SettingsCard(title = "Desktop State") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Motor Position", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "$remoteMotorPosition",
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Depth", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        if (remoteDepth > 0) String.format("%.2f m", remoteDepth) else "—",
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Focus Mode", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        when (remoteFocusMode) {
                            "MANUAL" -> "MF"
                            "SINGLE_AUTO" -> "AF-S"
                            "CONTINUOUS_AUTO" -> "AF-C"
                            "FACE_TRACKING" -> "AF-F"
                            else -> remoteFocusMode.ifBlank { "—" }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // QR scan button
        if (!isConnected) {
            Button(
                onClick = onScanQr,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Scan QR Code to Connect")
            }
        }

        // Manual connection
        if (!isConnected) {
            SettingsCard(title = "Manual Connection") {
                OutlinedTextField(
                    value = serverIp,
                    onValueChange = onServerIpChanged,
                    label = { Text("Server IP Address") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = serverPort.toString(),
                    onValueChange = { str ->
                        str.toIntOrNull()?.let { onServerPortChanged(it) }
                    },
                    label = { Text("Port") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Button(
                    onClick = onConnect,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = serverIp.isNotBlank()
                ) {
                    Text("Connect")
                }
            }
        }

        Text(
            text = "Start the sync server on the desktop Alice Studio, " +
                    "then scan the QR code displayed on screen.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
