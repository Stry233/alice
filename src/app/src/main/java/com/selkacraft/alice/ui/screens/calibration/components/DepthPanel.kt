package com.selkacraft.alice.ui.screens.calibration.components

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.selkacraft.alice.comm.core.ConnectionState
import com.selkacraft.alice.util.CalibrationViewModel
import kotlin.math.roundToInt

@Composable
fun SimplifiedDepthDisplay(
    viewModel: CalibrationViewModel,
    connectionState: ConnectionState,
    modifier: Modifier = Modifier
) {
    val depth by viewModel.depthValue.collectAsStateWithLifecycle()
    val confidence by viewModel.depthConfidence.collectAsStateWithLifecycle()
    val depthBitmap by viewModel.realSenseManager.depthBitmap.collectAsStateWithLifecycle()

    val isConnected = connectionState is ConnectionState.Connected ||
            connectionState is ConnectionState.Active

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // Depth stream preview
        if (isConnected) {
            DepthStreamPreview(
                bitmap = depthBitmap,
                onPositionChanged = { x, y ->
                    viewModel.setMeasurementPosition(x, y)
                }
            )

            // Depth overlay - floating on top
            DepthOverlay(
                depth = depth,
                confidence = confidence,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
            )
        } else {
            // Connection status when not connected
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when (connectionState) {
                            is ConnectionState.Connecting -> "Connecting..."
                            is ConnectionState.AwaitingPermission -> "Awaiting Permission..."
                            is ConnectionState.Error -> "Connection Error"
                            else -> "RealSense Disconnected"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun DepthStreamPreview(
    bitmap: Bitmap?,
    onPositionChanged: (Float, Float) -> Unit
) {
    var measurementPosition by remember { mutableStateOf(Offset(0.5f, 0.5f)) }
    var showIndicator by remember { mutableStateOf(false) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    val hapticFeedback = LocalHapticFeedback.current

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(6.dp)),
        color = Color.Black
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (bitmap != null && bitmap.width > 0 && bitmap.height > 0) {
                // Depth image
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Depth Stream",
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            viewSize = size
                            detectTapGestures { offset ->
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                val normalizedX = offset.x / size.width
                                val normalizedY = offset.y / size.height
                                measurementPosition = Offset(normalizedX, normalizedY)
                                showIndicator = true
                                onPositionChanged(normalizedX, normalizedY)
                            }
                        },
                    contentScale = ContentScale.Fit
                )

                // Measurement indicator
                if (showIndicator && viewSize != IntSize.Zero) {
                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    (measurementPosition.x * viewSize.width.toFloat()).roundToInt() - 12,
                                    (measurementPosition.y * viewSize.height.toFloat()).roundToInt() - 12
                                )
                            }
                            .size(24.dp)
                    ) {
                        // Outer circle
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.3f))
                        )

                        // Crosshair
                        Box(modifier = Modifier.size(16.dp).align(Alignment.Center)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.5.dp)
                                    .align(Alignment.Center)
                                    .background(Color.White)
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(1.5.dp)
                                    .align(Alignment.Center)
                                    .background(Color.White)
                            )
                        }
                    }
                }

                // Helper text
                if (!showIndicator) {
                    Text(
                        text = "Tap to measure",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(4.dp)
                            .background(
                                Color.Black.copy(alpha = 0.4f),
                                RoundedCornerShape(3.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            } else {
                // Loading state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/**
 * Depth information overlay
 * Shows current depth reading and confidence on top of depth preview
 */
@Composable
private fun DepthOverlay(
    depth: Float,
    confidence: Float,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = Color.Black.copy(alpha = 0.7f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Depth",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
                Text(
                    text = if (depth > 0) {
                        String.format("%.3f m", depth)
                    } else {
                        "---"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        depth <= 0 -> Color.White.copy(alpha = 0.5f)
                        confidence > 0.8f -> Color(0xFF4CAF50)
                        confidence > 0.5f -> Color(0xFFFF9800)
                        else -> Color(0xFFFF5252)
                    }
                )
            }

            // Confidence badge
            if (depth > 0) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = when {
                        confidence > 0.8f -> Color(0xFF4CAF50).copy(alpha = 0.3f)
                        confidence > 0.5f -> Color(0xFFFF9800).copy(alpha = 0.3f)
                        else -> Color(0xFFFF5252).copy(alpha = 0.3f)
                    }
                ) {
                    Text(
                        text = "${(confidence * 100).toInt()}%",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            confidence > 0.8f -> Color(0xFF4CAF50)
                            confidence > 0.5f -> Color(0xFFFF9800)
                            else -> Color(0xFFFF5252)
                        }
                    )
                }
            }
        }
    }
}
