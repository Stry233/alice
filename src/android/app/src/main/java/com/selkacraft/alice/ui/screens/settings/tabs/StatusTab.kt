package com.selkacraft.alice.ui.screens.settings.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.selkacraft.alice.comm.UsbDeviceCoordinator
import com.selkacraft.alice.ui.screens.settings.components.DeviceInfoRow
import com.selkacraft.alice.ui.screens.settings.components.SettingsCard
import com.selkacraft.alice.util.CameraViewModel

@Composable
fun StatusTab(
    coordinatorState: UsbDeviceCoordinator.CoordinatorState,
    viewModel: CameraViewModel
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SettingsCard(title = "USB Device Status") {
            Text(
                "Connected Devices:",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            val connectedCameras = viewModel.getConnectedCameras()
            val connectedMotors = viewModel.getConnectedMotors()
            val connectedRealSenseDevices = viewModel.getConnectedRealSenseDevices()

            if (connectedCameras.isEmpty() && connectedMotors.isEmpty() && connectedRealSenseDevices.isEmpty()) {
                Text(
                    "No devices connected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                connectedCameras.forEach { camera ->
                    DeviceInfoRow(
                        deviceInfo = camera,
                        type = "UVC Camera"
                    )
                }
                connectedMotors.forEach { motor ->
                    DeviceInfoRow(
                        deviceInfo = motor,
                        type = "Motor Controller"
                    )
                }
                connectedRealSenseDevices.forEach { realsense ->
                    DeviceInfoRow(
                        deviceInfo = realsense,
                        type = "RealSense Camera"
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Active Devices:",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    "${coordinatorState.activeDevices.size} device(s)",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Bandwidth Used:",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    "${coordinatorState.totalBandwidthUsed} Mbps",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (coordinatorState.conflicts.isNotEmpty()) {
            SettingsCard(
                title = "Conflicts",
                containerColor = MaterialTheme.colorScheme.errorContainer
            ) {
                coordinatorState.conflicts.forEach { conflict ->
                    Text(
                        "• $conflict",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}
