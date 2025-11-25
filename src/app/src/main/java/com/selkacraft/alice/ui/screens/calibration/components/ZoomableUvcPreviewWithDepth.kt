package com.selkacraft.alice.ui.screens.calibration.components

import android.graphics.Bitmap
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.selkacraft.alice.comm.core.ConnectionState
import com.selkacraft.alice.util.CalibrationViewModel
import kotlin.math.abs
import kotlin.math.roundToInt

private const val TAG = "ZoomableUvcPreview"

/**
 * Zoomable UVC camera preview with depth overlay
 * Combines UVC camera feed with RealSense depth visualization
 */
@Composable
fun ZoomableUvcPreviewWithDepth(
    viewModel: CalibrationViewModel,
    modifier: Modifier = Modifier
) {
    val cameraConnectionState by viewModel.cameraConnectionState.collectAsStateWithLifecycle()
    val videoAspectRatio by viewModel.videoAspectRatio.collectAsStateWithLifecycle()

    var lastValidAspectRatio by remember { mutableStateOf<Float?>(null) }
    val isPreviewVisible = videoAspectRatio != null && videoAspectRatio!! > 0f

    // Zoom state
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    val minScale = 1f
    val maxScale = 5f

    // Track gesture state
    var initialDistance by remember { mutableFloatStateOf(0f) }
    var initialScale by remember { mutableFloatStateOf(1f) }
    var isZooming by remember { mutableStateOf(false) }
    var hasShownHint by remember { mutableStateOf(false) }

    val hapticFeedback = LocalHapticFeedback.current

    // Update lastValidAspectRatio only when we have a valid aspect ratio
    if (videoAspectRatio != null) {
        lastValidAspectRatio = videoAspectRatio
    }

    // Force recreation of surface on recomposition by using a key
    var surfaceKey by remember { mutableStateOf(0) }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // UVC Camera Preview
        AnimatedVisibility(
            visible = isPreviewVisible,
            enter = fadeIn(animationSpec = tween(400, easing = LinearOutSlowInEasing)) +
                    scaleIn(animationSpec = tween(400, easing = FastOutSlowInEasing), initialScale = 0.95f),
            exit = fadeOut(animationSpec = tween(300, easing = FastOutLinearInEasing)) +
                    scaleOut(animationSpec = tween(300, easing = FastOutLinearInEasing), targetScale = 0.95f)
        ) {
            Box(
                modifier = Modifier
                    .aspectRatio(lastValidAspectRatio ?: 1f)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offsetX
                        translationY = offsetY
                    }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            // Wait for initial press
                            val firstChange = awaitFirstDown(requireUnconsumed = false)

                            // Track all pointers
                            val pointers = mutableMapOf<PointerId, PointerInputChange>()
                            pointers[firstChange.id] = firstChange

                            // Continue tracking pointer events
                            while (true) {
                                val event = awaitPointerEvent()

                                // Update pointer map
                                event.changes.forEach { change ->
                                    if (change.pressed) {
                                        pointers[change.id] = change
                                    } else {
                                        pointers.remove(change.id)
                                    }
                                }

                                // Check if we have exactly 3 fingers
                                if (pointers.size == 3) {
                                    hasShownHint = true
                                    val pointerList = pointers.values.toList()

                                    // Calculate centroid
                                    val centroidX = pointerList.sumOf { it.position.x.toDouble() }.toFloat() / 3f
                                    val centroidY = pointerList.sumOf { it.position.y.toDouble() }.toFloat() / 3f

                                    // Calculate average distance from centroid
                                    val avgDistance = pointerList.map { pointer ->
                                        val dx = pointer.position.x - centroidX
                                        val dy = pointer.position.y - centroidY
                                        kotlin.math.sqrt(dx * dx + dy * dy)
                                    }.average().toFloat()

                                    if (!isZooming) {
                                        // Start zoom
                                        isZooming = true
                                        initialDistance = avgDistance
                                        initialScale = scale
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    } else {
                                        // Continue zoom
                                        if (initialDistance > 0) {
                                            val zoomFactor = avgDistance / initialDistance
                                            val newScale = (initialScale * zoomFactor).coerceIn(minScale, maxScale)

                                            // Calculate zoom center offset
                                            val scaleDiff = newScale / scale
                                            if (scaleDiff != 1f) {
                                                offsetX = (offsetX - centroidX) * scaleDiff + centroidX
                                                offsetY = (offsetY - centroidY) * scaleDiff + centroidY
                                            }

                                            // Apply pan
                                            val avgPreviousX = pointerList.sumOf { it.previousPosition.x.toDouble() }.toFloat() / 3f
                                            val avgPreviousY = pointerList.sumOf { it.previousPosition.y.toDouble() }.toFloat() / 3f
                                            offsetX += (centroidX - avgPreviousX)
                                            offsetY += (centroidY - avgPreviousY)

                                            // Constrain offsets
                                            if (newScale > 1f) {
                                                val maxOffsetX = (size.width * (newScale - 1f)) / 2f
                                                val maxOffsetY = (size.height * (newScale - 1f)) / 2f
                                                offsetX = offsetX.coerceIn(-maxOffsetX, maxOffsetX)
                                                offsetY = offsetY.coerceIn(-maxOffsetY, maxOffsetY)
                                            } else {
                                                offsetX = 0f
                                                offsetY = 0f
                                            }

                                            scale = newScale
                                        }
                                    }

                                    // Consume the changes
                                    event.changes.forEach { it.consume() }
                                } else if (isZooming && pointers.size < 3) {
                                    // End zoom
                                    isZooming = false
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                }

                                // Break if no pointers left
                                if (pointers.isEmpty()) break
                            }
                        }
                    }
            ) {
                key(surfaceKey) {
                    AndroidView(
                        factory = { ctx ->
                            Log.d(TAG, "Creating new SurfaceView with key: $surfaceKey")
                            SurfaceView(ctx).apply {
                                holder.addCallback(object : SurfaceHolder.Callback {
                                    override fun surfaceCreated(h: SurfaceHolder) {
                                        Log.i(TAG, "Surface CREATED: ${h.surface}, isValid=${h.surface.isValid}")
                                        if (h.surface.isValid) {
                                            viewModel.uvcCameraManager.setSurface(h.surface)
                                        }
                                    }

                                    override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, hgt: Int) {
                                        Log.i(TAG, "Surface CHANGED: ${w}x${hgt}, isValid=${h.surface.isValid}")
                                        if (h.surface.isValid) {
                                            viewModel.uvcCameraManager.setSurface(h.surface)
                                        }
                                    }

                                    override fun surfaceDestroyed(h: SurfaceHolder) {
                                        Log.i(TAG, "Surface DESTROYED")
                                        viewModel.uvcCameraManager.setSurface(null)
                                    }
                                })
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        update = { surfaceView ->
                            Log.d(TAG, "AndroidView Update block called for key: $surfaceKey")
                            if (!surfaceView.holder.surface.isValid) {
                                Log.d(TAG, "Surface is invalid, requesting layout")
                                surfaceView.requestLayout()
                            }
                        }
                    )
                }
            }
        }

        // Zoom indicator
        AnimatedVisibility(
            visible = isPreviewVisible && scale > 1.1f,
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(200)),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color.Black.copy(alpha = 0.6f)
            ) {
                Text(
                    text = "${String.format("%.1f", scale)}×",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        // 3-finger zoom hint
        AnimatedVisibility(
            visible = isPreviewVisible && !hasShownHint,
            enter = fadeIn(animationSpec = tween(500, delayMillis = 1000)),
            exit = fadeOut(animationSpec = tween(500)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(24.dp)
        ) {
            DisposableEffect(Unit) {
                onDispose {
                    hasShownHint = true
                }
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color.Black.copy(alpha = 0.6f)
            ) {
                Text(
                    text = "Use 3 fingers to zoom",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        // Camera Unavailable Placeholder
        AnimatedVisibility(
            visible = !isPreviewVisible,
            enter = fadeIn(animationSpec = tween(durationMillis = 250, delayMillis = 200, easing = LinearOutSlowInEasing)),
            exit = fadeOut(animationSpec = tween(durationMillis = 200, easing = FastOutLinearInEasing))
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when (cameraConnectionState) {
                        is ConnectionState.Connecting -> "Connecting..."
                        is ConnectionState.AwaitingPermission -> "Awaiting Permission..."
                        is ConnectionState.Error -> "Connection Error"
                        else -> "UVC Camera Disconnected"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
