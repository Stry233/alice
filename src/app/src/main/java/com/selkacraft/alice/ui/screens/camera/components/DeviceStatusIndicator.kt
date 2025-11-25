package com.selkacraft.alice.ui.screens.camera.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ControlCamera
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun DeviceStatusIndicator(
    connectedDevices: Int,
    activeDevices: Int,
    cameraActive: Boolean,
    motorActive: Boolean,
    realSenseActive: Boolean,
    totalBandwidthUsed: Int = 0,
    modifier: Modifier = Modifier
) {
    val hapticFeedback = LocalHapticFeedback.current
    val density = LocalDensity.current

    var isHidden by remember { mutableStateOf(false) }
    var isExpanded by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    val hideThreshold = with(density) { 150.dp.toPx() }

    val animatedOffset by animateFloatAsState(
        targetValue = if (isHidden) -hideThreshold else dragOffset,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        finishedListener = { dragOffset = 0f },
        label = "status_offset"
    )

    val draggableState = rememberDraggableState { delta ->
        if (!isHidden) {
            val newOffset = dragOffset + delta
            dragOffset = when {
                newOffset > 0 -> 0f
                newOffset < -hideThreshold -> -hideThreshold
                else -> newOffset
            }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val cardAlpha by animateFloatAsState(
        targetValue = if (isHidden && activeDevices > 0) pulseAlpha else if (isHidden) 0.85f else 0.92f,
        animationSpec = tween(300),
        label = "card_alpha"
    )

    Box(
        modifier = modifier.wrapContentSize(),
        contentAlignment = Alignment.CenterStart
    ) {
        Card(
            onClick = {
                if (isHidden) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    isHidden = false
                } else {
                    isExpanded = !isExpanded
                }
            },
            modifier = Modifier
                .width(if (isExpanded) 200.dp else 180.dp)
                .offset { IntOffset(animatedOffset.roundToInt(), 0) }
                .draggable(
                    state = draggableState,
                    orientation = Orientation.Horizontal,
                    enabled = !isHidden,
                    onDragStarted = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDragStopped = { velocity ->
                        val shouldHide = dragOffset < -hideThreshold * 0.4 || velocity < -500
                        if (shouldHide) {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            isHidden = true
                        } else {
                            dragOffset = 0f
                        }
                    }
                ),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            elevation = CardDefaults.cardElevation(defaultElevation = if (isHidden) 3.dp else 6.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Usb,
                        contentDescription = "USB Devices",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Devices",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "$activeDevices/$connectedDevices",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    CompactDeviceStatus(
                        icon = Icons.Default.CameraAlt,
                        label = "Camera",
                        isActive = cameraActive
                    )
                    CompactDeviceStatus(
                        icon = Icons.Default.ControlCamera,
                        label = "Motor",
                        isActive = motorActive
                    )
                    CompactDeviceStatus(
                        icon = Icons.Default.Visibility,
                        label = "Depth",
                        isActive = realSenseActive
                    )
                }

                if (isExpanded || totalBandwidthUsed > 2000) {
                    Divider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = when {
                                    totalBandwidthUsed > 3000 -> MaterialTheme.colorScheme.errorContainer
                                    totalBandwidthUsed > 2000 -> MaterialTheme.colorScheme.tertiaryContainer
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                },
                                shape = RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = "Bandwidth",
                            modifier = Modifier.size(14.dp),
                            tint = when {
                                totalBandwidthUsed > 3000 -> MaterialTheme.colorScheme.error
                                totalBandwidthUsed > 2000 -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            }
                        )
                        Text(
                            text = "USB BW",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${totalBandwidthUsed} Mbps",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                totalBandwidthUsed > 3000 -> MaterialTheme.colorScheme.error
                                totalBandwidthUsed > 2000 -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.primary
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactDeviceStatus(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean
) {
    val statusColor by animateColorAsState(
        targetValue = if (isActive) Color(0xFF4CAF50) else Color.Gray,
        animationSpec = tween(300),
        label = "status_color"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (isActive) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(14.dp),
            tint = if (isActive) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = if (isActive) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(statusColor)
        )
    }
}
