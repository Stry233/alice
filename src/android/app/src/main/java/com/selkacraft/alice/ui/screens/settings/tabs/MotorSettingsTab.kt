package com.selkacraft.alice.ui.screens.settings.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.selkacraft.alice.ui.screens.settings.components.SettingsCard
import com.selkacraft.alice.ui.screens.settings.components.SliderSettingItem
import com.selkacraft.alice.util.CameraViewModel
import com.selkacraft.alice.util.SettingsManager

@Composable
fun MotorSettingsTab(
    settingsManager: SettingsManager,
    viewModel: CameraViewModel,
    navController: NavController
) {
    val motorSpeed by settingsManager.motorSpeed.collectAsState()
    val motorDestinationDiscovered by settingsManager.motorDestinationDiscovered.collectAsState()
    val motorDestinationAddress by settingsManager.motorDestinationAddress.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Motor Discovery Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (motorDestinationDiscovered)
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else
                    MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Motor Address",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (motorDestinationDiscovered) "Discovered" else "Not configured",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "0x${String.format("%04X", motorDestinationAddress)}",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Button(
                    onClick = { navController.navigate("motor_discovery") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (motorDestinationDiscovered)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.tertiary
                    )
                ) {
                    Icon(Icons.Default.Search, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (motorDestinationDiscovered) "Rediscover Motor" else "Discover Motor Address")
                }

                if (!motorDestinationDiscovered) {
                    Text(
                        text = "Run motor discovery to find your focus motor's address",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        SettingsCard(title = "Motor Control") {
            SliderSettingItem(
                label = "Motor Speed",
                value = motorSpeed.toFloat(),
                onValueChange = { settingsManager.setMotorSpeed(it.toInt()) },
                valueRange = 10f..100f,
                steps = 90,
                displayFormat = { "${it.toInt()}%" }
            )

            Button(
                onClick = {
                    viewModel.setMotorPosition(2048)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.CenterFocusStrong, contentDescription = "Calibrate")
                Spacer(Modifier.width(8.dp))
                Text("Center Motor")
            }
        }

        OutlinedButton(
            onClick = { settingsManager.resetMotorSettings() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Refresh, contentDescription = "Reset")
            Spacer(Modifier.width(8.dp))
            Text("Reset Motor Settings")
        }
    }
}
