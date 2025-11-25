package com.selkacraft.alice.ui.screens.settings.tabs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.selkacraft.alice.ui.screens.settings.components.DropdownSettingItem
import com.selkacraft.alice.ui.screens.settings.components.SettingsCard
import com.selkacraft.alice.ui.screens.settings.components.SliderSettingItem
import com.selkacraft.alice.ui.screens.settings.components.SwitchSettingItem
import com.selkacraft.alice.util.CameraViewModel
import com.selkacraft.alice.util.SettingsManager

@Composable
fun SystemSettingsTab(
    settingsManager: SettingsManager,
    @Suppress("UNUSED_PARAMETER") viewModel: CameraViewModel
) {
    val logVerbosity by settingsManager.logVerbosity.collectAsState()
    val usbBandwidthLimit by settingsManager.usbBandwidthLimit.collectAsState()
    val devicePriority by settingsManager.devicePriority.collectAsState()
    val autoReconnect by settingsManager.autoReconnect.collectAsState()
    val reconnectDelay by settingsManager.reconnectDelay.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SettingsCard(title = "USB Configuration") {
            DropdownSettingItem(
                label = "Device Priority",
                value = devicePriority,
                options = listOf("Camera", "RealSense", "Motor", "Balanced"),
                onValueChange = { settingsManager.setDevicePriority(it) }
            )

            SliderSettingItem(
                label = "Bandwidth Limit",
                value = usbBandwidthLimit.toFloat(),
                onValueChange = { settingsManager.setUsbBandwidthLimit(it.toInt()) },
                valueRange = 1000f..5000f,
                steps = 40,
                displayFormat = { "${it.toInt()} Mbps" }
            )

            SwitchSettingItem(
                label = "Auto Reconnect",
                checked = autoReconnect,
                onCheckedChange = { settingsManager.setAutoReconnect(it) }
            )

            AnimatedVisibility(visible = autoReconnect) {
                SliderSettingItem(
                    label = "Reconnect Delay",
                    value = reconnectDelay.toFloat(),
                    onValueChange = { settingsManager.setReconnectDelay(it.toInt()) },
                    valueRange = 500f..5000f,
                    steps = 45,
                    displayFormat = { "${it.toInt()} ms" }
                )
            }
        }

        SettingsCard(title = "Debug Options") {
            DropdownSettingItem(
                label = "Log Level",
                value = logVerbosity,
                options = listOf("ERROR", "WARNING", "INFO", "DEBUG"),
                onValueChange = { settingsManager.setLogVerbosity(it) }
            )
        }

        OutlinedButton(
            onClick = { settingsManager.resetSystemSettings() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Refresh, contentDescription = "Reset")
            Spacer(Modifier.width(8.dp))
            Text("Reset System Settings")
        }

        Button(
            onClick = { settingsManager.resetAllSettings() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error
            )
        ) {
            Icon(Icons.Default.RestartAlt, contentDescription = "Reset All")
            Spacer(Modifier.width(8.dp))
            Text("Reset All Settings")
        }
    }
}
