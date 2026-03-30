package com.selkacraft.alice.ui.screens.settings.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.selkacraft.alice.ui.screens.settings.components.SettingsCard
import com.selkacraft.alice.ui.screens.settings.components.SliderSettingItem
import com.selkacraft.alice.util.CameraViewModel
import com.selkacraft.alice.util.SettingsManager

@Composable
fun DepthSettingsTab(
    settingsManager: SettingsManager,
    @Suppress("UNUSED_PARAMETER") viewModel: CameraViewModel
) {
    val depthConfidence by settingsManager.depthConfidenceThreshold.collectAsState()
    val depthSmoothing by settingsManager.depthFilterSmoothing.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SettingsCard(title = "Depth Processing") {
            SliderSettingItem(
                label = "Confidence Threshold",
                value = depthConfidence,
                onValueChange = { settingsManager.setDepthConfidenceThreshold(it) },
                valueRange = 0f..1f,
                steps = 100,
                displayFormat = { "${(it * 100).toInt()}%" }
            )

            SliderSettingItem(
                label = "Filter Smoothing",
                value = depthSmoothing,
                onValueChange = { settingsManager.setDepthFilterSmoothing(it) },
                valueRange = 0f..1f,
                steps = 100,
                displayFormat = { "${(it * 100).toInt()}%" }
            )
        }

        OutlinedButton(
            onClick = { settingsManager.resetDepthSettings() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Refresh, contentDescription = "Reset")
            Spacer(Modifier.width(8.dp))
            Text("Reset Depth Settings")
        }
    }
}
