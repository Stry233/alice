package com.selkacraft.alice.ui.screens.settings.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.selkacraft.alice.comm.core.ConnectionState
import com.selkacraft.alice.ui.screens.settings.components.DropdownSettingItem
import com.selkacraft.alice.ui.screens.settings.components.SettingsCard
import com.selkacraft.alice.util.CameraViewModel
import com.selkacraft.alice.util.SettingsManager

@Composable
fun CameraSettingsTab(
    settingsManager: SettingsManager,
    viewModel: CameraViewModel
) {
    val cameraResolution by settingsManager.cameraResolution.collectAsState()
    val supportedResolutions by viewModel.supportedCameraResolutions.collectAsState()
    val cameraConnectionState by viewModel.cameraConnectionState.collectAsState()
    val isChangingResolution by viewModel.isChangingResolution.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SettingsCard(title = "Video Settings") {
            if (cameraConnectionState !is ConnectionState.Connected &&
                cameraConnectionState !is ConnectionState.Active) {
                Text(
                    "Connect a camera to see available resolutions",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            val resolutionOptions = if (supportedResolutions.isNotEmpty()) {
                supportedResolutions
            } else {
                listOf("1920x1080", "1280x720", "640x480", "424x240")
            }

            if (isChangingResolution) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Text(
                            "Changing resolution...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            DropdownSettingItem(
                label = "Resolution",
                value = cameraResolution,
                options = resolutionOptions,
                onValueChange = { newResolution ->
                    if (!isChangingResolution && newResolution != cameraResolution) {
                        viewModel.changeCameraResolution(newResolution)
                    }
                },
                enabled = (cameraConnectionState is ConnectionState.Connected ||
                        cameraConnectionState is ConnectionState.Active) && !isChangingResolution
            )
        }

        OutlinedButton(
            onClick = { settingsManager.resetCameraSettings() },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isChangingResolution
        ) {
            Icon(Icons.Default.Refresh, contentDescription = "Reset")
            Spacer(Modifier.width(8.dp))
            Text("Reset Camera Settings")
        }
    }
}
