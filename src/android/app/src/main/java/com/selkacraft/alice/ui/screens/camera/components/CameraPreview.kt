package com.selkacraft.alice.ui.screens.camera.components

import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.abs

private const val TAG_CPC = "CameraPreview"

@Composable
fun CameraPreview(
    videoAspectRatio: Float?,
    onSurfaceHolderAvailable: (SurfaceHolder) -> Unit,
    onSurfaceHolderDestroyed: () -> Unit,
    modifier: Modifier = Modifier
) {
    var rememberedAspectRatio by remember { mutableStateOf<Float?>(null) }
    Log.d(TAG_CPC, "Composing. Initial videoAspectRatio: $videoAspectRatio, rememberedAspectRatio: $rememberedAspectRatio")

    // Track the last valid aspect ratio for smooth exit animations
    var lastValidAspectRatio by remember { mutableStateOf<Float?>(null) }
    val isPreviewVisible = videoAspectRatio != null && videoAspectRatio > 0f

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

    DisposableEffect(Unit) {
        Log.d(TAG_CPC, "CameraPreview composed")
        onDispose {
            Log.d(TAG_CPC, "CameraPreview disposed")
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
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
                            val firstChange = awaitFirstDown(requireUnconsumed = false)
                            val pointers = mutableMapOf<PointerId, PointerInputChange>()
                            pointers[firstChange.id] = firstChange

                            while (true) {
                                val event = awaitPointerEvent()
                                event.changes.forEach { change ->
                                    if (change.pressed) {
                                        pointers[change.id] = change
                                    } else {
                                        pointers.remove(change.id)
                                    }
                                }

                                if (pointers.size == 3) {
                                    hasShownHint = true
                                    val pointerList = pointers.values.toList()
                                    val centroidX = pointerList.sumOf { it.position.x.toDouble() }.toFloat() / 3f
                                    val centroidY = pointerList.sumOf { it.position.y.toDouble() }.toFloat() / 3f
                                    val avgDistance = pointerList.map { pointer ->
                                        val dx = pointer.position.x - centroidX
                                        val dy = pointer.position.y - centroidY
                                        kotlin.math.sqrt(dx * dx + dy * dy)
                                    }.average().toFloat()

                                    if (!isZooming) {
                                        isZooming = true
                                        initialDistance = avgDistance
                                        initialScale = scale
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    } else {
                                        if (initialDistance > 0) {
                                            val zoomFactor = avgDistance / initialDistance
                                            val newScale = (initialScale * zoomFactor).coerceIn(minScale, maxScale)
                                            val scaleDiff = newScale / scale
                                            if (scaleDiff != 1f) {
                                                offsetX = (offsetX - centroidX) * scaleDiff + centroidX
                                                offsetY = (offsetY - centroidY) * scaleDiff + centroidY
                                            }
                                            val avgPreviousX = pointerList.sumOf { it.previousPosition.x.toDouble() }.toFloat() / 3f
                                            val avgPreviousY = pointerList.sumOf { it.previousPosition.y.toDouble() }.toFloat() / 3f
                                            offsetX += (centroidX - avgPreviousX)
                                            offsetY += (centroidY - avgPreviousY)
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
                                    event.changes.forEach { it.consume() }
                                } else if (isZooming && pointers.size < 3) {
                                    isZooming = false
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                                if (pointers.isEmpty()) break
                            }
                        }
                    }
            ) {
                key(surfaceKey) {
                    AndroidView(
                        factory = { ctx ->
                            Log.d(TAG_CPC, "Creating new SurfaceView with key: $surfaceKey")
                            SurfaceView(ctx).apply {
                                holder.addCallback(object : SurfaceHolder.Callback {
                                    override fun surfaceCreated(h: SurfaceHolder) {
                                        Log.i(TAG_CPC, "Surface CREATED: ${h.surface}, isValid=${h.surface.isValid}")
                                        onSurfaceHolderAvailable(h)
                                    }
                                    override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, hgt: Int) {
                                        Log.i(TAG_CPC, "Surface CHANGED: ${w}x${hgt}, isValid=${h.surface.isValid}")
                                        onSurfaceHolderAvailable(h)
                                    }
                                    override fun surfaceDestroyed(h: SurfaceHolder) {
                                        Log.i(TAG_CPC, "Surface DESTROYED")
                                        onSurfaceHolderDestroyed()
                                    }
                                })
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        update = { surfaceView ->
                            Log.d(TAG_CPC, "AndroidView Update block called for key: $surfaceKey")
                            if (!surfaceView.holder.surface.isValid) {
                                Log.d(TAG_CPC, "Surface is invalid, requesting layout")
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
                .align(Alignment.BottomCenter)
                .padding(32.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color.Black.copy(alpha = 0.5f),
                modifier = Modifier.padding(4.dp)
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
                .padding(32.dp)
        ) {
            DisposableEffect(Unit) {
                onDispose {
                    hasShownHint = true
                }
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color.Black.copy(alpha = 0.5f)
            ) {
                Text(
                    text = "Use 3 fingers to zoom",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        // Placeholder
        AnimatedVisibility(
            visible = !isPreviewVisible,
            enter = fadeIn(animationSpec = tween(durationMillis = 250, delayMillis = 200, easing = LinearOutSlowInEasing)),
            exit = fadeOut(animationSpec = tween(durationMillis = 200, easing = FastOutLinearInEasing))
        ) {
            Log.d(TAG_CPC, "Placeholder AnimatedVisibility: Content visible (Camera Unavailable).")
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Filled.VideocamOff,
                    "Camera Unavailable",
                    modifier = Modifier.size(96.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    "Camera Unavailable",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Please ensure your camera is connected and permissions are granted.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }
}
