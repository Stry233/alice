package com.selkacraft.alice.ui.screens.calibration.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Motor control panel with position slider and increment/decrement buttons.
 */
@Composable
fun ImprovedMotorControl(
    position: Int,
    isConnected: Boolean,
    isTestMode: Boolean,
    onPositionChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header
            Text(
                text = "Motor Control",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )

            // Position display with +1/-1 buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // -1 button
                FilledTonalIconButton(
                    onClick = {
                        if (isConnected && position > 0) {
                            onPositionChange((position - 1).coerceAtLeast(0))
                        }
                    },
                    enabled = isConnected && position > 0 && !isTestMode,
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Decrease position by 1",
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Position text
                Text(
                    text = position.toString(),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.widthIn(min = 90.dp),
                    textAlign = TextAlign.Center,
                    color = if (isConnected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )

                Spacer(modifier = Modifier.width(12.dp))

                // +1 button
                FilledTonalIconButton(
                    onClick = {
                        if (isConnected && position < 4095) {
                            onPositionChange((position + 1).coerceAtMost(4095))
                        }
                    },
                    enabled = isConnected && position < 4095 && !isTestMode,
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Increase position by 1",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Test mode indicator or slider
            if (isTestMode) {
                TestModeIndicator()
            } else {
                ImprovedMotorSlider(
                    currentPosition = position,
                    enabled = isConnected,
                    onPositionChange = onPositionChange
                )
            }

            // Connection status - always reserve space to prevent layout shift
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp),
                contentAlignment = Alignment.Center
            ) {
                if (!isConnected && !isTestMode) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            text = "Motor Disconnected",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(6.dp),
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ImprovedMotorSlider(
    currentPosition: Int,
    enabled: Boolean,
    onPositionChange: (Int) -> Unit
) {
    var sliderPosition by remember { mutableFloatStateOf(currentPosition.toFloat()) }
    val hapticFeedback = LocalHapticFeedback.current
    var lastHapticPosition by remember { mutableFloatStateOf(currentPosition.toFloat()) }

    LaunchedEffect(currentPosition) {
        sliderPosition = currentPosition.toFloat()
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Range labels at the top
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Near",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "Focus Range",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Far",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                fontWeight = FontWeight.Medium
            )
        }

        // Slider
        Slider(
            value = sliderPosition,
            onValueChange = { newValue ->
                sliderPosition = newValue

                // Haptic feedback at intervals
                if (abs(newValue - lastHapticPosition) > 100f) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    lastHapticPosition = newValue
                }

                if (enabled) {
                    onPositionChange(newValue.toInt())
                }
            },
            valueRange = 0f..4095f,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )

        // Numeric range labels at bottom
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "0",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Text(
                text = "4095",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun SimplifiedMotorControl(
    position: Int,
    isConnected: Boolean,
    isTestMode: Boolean,
    onPositionChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Position display with +1/-1 buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Motor Position",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // -1 button
                        FilledTonalIconButton(
                            onClick = {
                                if (isConnected && position > 0) {
                                    onPositionChange((position - 1).coerceAtLeast(0))
                                }
                            },
                            enabled = isConnected && position > 0,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Remove,
                                contentDescription = "Decrease position by 1",
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Position text
                        Text(
                            text = position.toString(),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.widthIn(min = 80.dp),
                            textAlign = TextAlign.Center,
                            color = if (isConnected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )

                        // +1 button
                        FilledTonalIconButton(
                            onClick = {
                                if (isConnected && position < 4095) {
                                    onPositionChange((position + 1).coerceAtMost(4095))
                                }
                            },
                            enabled = isConnected && position < 4095,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Increase position by 1",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Percentage indicator
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "${((position / 4095f) * 100).toInt()}%",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // Test mode indicator or slider
            if (isTestMode) {
                TestModeIndicator()
            } else {
                MotorSlider(
                    currentPosition = position,
                    enabled = isConnected,
                    onPositionChange = onPositionChange
                )

                // Connection status
                if (!isConnected) {
                    Text(
                        text = "Motor disconnected",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun MotorSlider(
    currentPosition: Int,
    enabled: Boolean,
    onPositionChange: (Int) -> Unit
) {
    var sliderPosition by remember { mutableFloatStateOf(currentPosition.toFloat()) }
    val hapticFeedback = LocalHapticFeedback.current
    var lastHapticPosition by remember { mutableFloatStateOf(currentPosition.toFloat()) }

    LaunchedEffect(currentPosition) {
        sliderPosition = currentPosition.toFloat()
    }

    Column {
        // Range labels at the top
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Near",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Text(
                text = "Focus Range",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Far",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }

        // Slider with better height for easier manipulation
        Slider(
            value = sliderPosition,
            onValueChange = { newValue ->
                sliderPosition = newValue

                // Haptic feedback at intervals
                if (abs(newValue - lastHapticPosition) > 100f) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    lastHapticPosition = newValue
                }

                if (enabled) {
                    onPositionChange(newValue.toInt())
                }
            },
            valueRange = 0f..4095f,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp), // Add some padding for better touch target
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )

        // Numeric range labels at bottom
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "0",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Text(
                text = "4095",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        }
    }
}

@Composable
private fun TestModeIndicator() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Test Mode",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Test Mode Active",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}
