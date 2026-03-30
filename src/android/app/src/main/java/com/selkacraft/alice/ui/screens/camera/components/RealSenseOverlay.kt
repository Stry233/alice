package com.selkacraft.alice.ui.screens.camera.components

import android.graphics.Bitmap
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.selkacraft.alice.comm.autofocus.FaceDetectionState
import com.selkacraft.alice.comm.core.ConnectionState
import kotlin.math.roundToInt

@Composable
fun RealSenseOverlay(
    connectionState: ConnectionState,
    centerDepth: Float,
    depthConfidence: Float,
    depthBitmap: Bitmap?,
    onMeasurementPositionChanged: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
    isAutofocusActive: Boolean = false,
    autofocusMode: String = "MANUAL",
    faceDetectionState: FaceDetectionState = FaceDetectionState(),
    onFaceTap: (Float, Float) -> Unit = { _, _ -> }
) {
    LaunchedEffect(connectionState) {
        android.util.Log.d("RealSenseOverlay", "Connection state changed to: ${connectionState::class.simpleName}")
    }

    val isVisible = when (connectionState) {
        is ConnectionState.Connected,
        is ConnectionState.Active,
        is ConnectionState.Connecting,
        is ConnectionState.AwaitingPermission -> true
        else -> false
    }

    val isStreaming = connectionState is ConnectionState.Connected || connectionState is ConnectionState.Active

    val hapticFeedback = LocalHapticFeedback.current
    val density = LocalDensity.current
    var isHidden by remember { mutableStateOf(false) }
    var isEnlarged by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    val hideThreshold = with(density) { 150.dp.toPx() }

    val overlayWidth by animateDpAsState(
        targetValue = if (isEnlarged) 360.dp else 180.dp,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "overlay_width"
    )

    val animatedOffset by animateFloatAsState(
        targetValue = if (isHidden) -hideThreshold else dragOffset,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        finishedListener = { dragOffset = 0f },
        label = "realsense_offset"
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
        targetValue = if (isHidden) pulseAlpha else 0.92f,
        animationSpec = tween(300),
        label = "card_alpha"
    )

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInHorizontally(
            initialOffsetX = { -it },
            animationSpec = tween(400, easing = FastOutSlowInEasing)
        ) + fadeIn(animationSpec = tween(400)),
        exit = slideOutHorizontally(
            targetOffsetX = { -it },
            animationSpec = tween(300, easing = FastOutSlowInEasing)
        ) + fadeOut(animationSpec = tween(300)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .offset { IntOffset(animatedOffset.roundToInt(), 0) },
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                onClick = {
                    if (isHidden) {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        isHidden = false
                    } else {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        isEnlarged = !isEnlarged
                    }
                },
                modifier = Modifier
                    .width(overlayWidth)
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
                                isEnlarged = false
                            } else {
                                dragOffset = 0f
                            }
                        }
                    ),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = cardAlpha),
                shadowElevation = if (isHidden) 3.dp else 6.dp,
                tonalElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Depth",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "RealSense",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        if (isAutofocusActive && autofocusMode != "MANUAL") {
                            AutofocusIndicator()
                        }
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(
                                    when (connectionState) {
                                        is ConnectionState.Active -> Color(0xFF4CAF50)
                                        is ConnectionState.Connected -> Color(0xFF2196F3)
                                        is ConnectionState.Connecting,
                                        is ConnectionState.AwaitingPermission -> Color(0xFFFF9800)
                                        is ConnectionState.Error -> Color(0xFFFF5252)
                                        else -> Color.Gray
                                    }
                                )
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = when {
                            autofocusMode == "FACE_TRACKING" && !faceDetectionState.hasFaces -> "N/A"
                            centerDepth > 0 -> "${String.format("%.2f", centerDepth)}m"
                            else -> "---"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            autofocusMode == "FACE_TRACKING" && !faceDetectionState.hasFaces -> MaterialTheme.colorScheme.onSurfaceVariant
                            isAutofocusActive && centerDepth > 0 -> MaterialTheme.colorScheme.primary
                            centerDepth > 0 -> MaterialTheme.colorScheme.onSurface
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )

                    if (centerDepth > 0 && !(autofocusMode == "FACE_TRACKING" && !faceDetectionState.hasFaces)) {
                        LinearProgressIndicator(
                            progress = depthConfidence.coerceIn(0f, 1f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .clip(RoundedCornerShape(1.dp)),
                            color = when {
                                depthConfidence > 0.8f -> Color(0xFF4CAF50)
                                depthConfidence > 0.5f -> Color(0xFFFF9800)
                                else -> Color(0xFFFF5252)
                            },
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }

                    when (connectionState) {
                        is ConnectionState.Connecting -> {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Connecting...",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                        is ConnectionState.Connected -> {
                            if (autofocusMode == "FACE_TRACKING" && !faceDetectionState.hasFaces && depthBitmap != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "No faces detected",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            } else if (depthBitmap == null && centerDepth <= 0) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Initializing stream...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        is ConnectionState.AwaitingPermission -> {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Awaiting permission...",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                        is ConnectionState.Error -> {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Connection error",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center
                            )
                        }
                        else -> {}
                    }
                }
            }

            if (isStreaming && depthBitmap != null && depthBitmap.width > 0 && depthBitmap.height > 0) {
                DraggableDepthPreview(
                    bitmap = depthBitmap,
                    onPositionChanged = onMeasurementPositionChanged,
                    isAutofocusActive = isAutofocusActive,
                    autofocusMode = autofocusMode,
                    isHidden = isHidden,
                    previewWidth = overlayWidth,
                    faceDetectionState = faceDetectionState,
                    onFaceTap = onFaceTap,
                    onTapToRestore = {
                        if (isHidden) {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            isHidden = false
                        }
                    }
                )
            } else if (isStreaming) {
                Surface(
                    modifier = Modifier
                        .width(overlayWidth)
                        .aspectRatio(4f / 3f),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Loading stream...",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AutofocusIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "af_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "af_scale"
    )

    Icon(
        imageVector = Icons.Default.CenterFocusStrong,
        contentDescription = "Autofocus Active",
        modifier = Modifier
            .size(14.dp)
            .scale(scale),
        tint = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun DraggableDepthPreview(
    bitmap: Bitmap,
    onPositionChanged: (Float, Float) -> Unit,
    isAutofocusActive: Boolean = false,
    autofocusMode: String = "MANUAL",
    isHidden: Boolean = false,
    previewWidth: androidx.compose.ui.unit.Dp = 180.dp,
    faceDetectionState: FaceDetectionState = FaceDetectionState(),
    onFaceTap: (Float, Float) -> Unit = { _, _ -> },
    onTapToRestore: () -> Unit = {}
) {
    var normalizedPosition by remember { mutableStateOf(Offset(0.5f, 0.5f)) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var isInitialized by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    val hapticFeedback = LocalHapticFeedback.current
    val density = LocalDensity.current

    var currentSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }

    val scale by animateFloatAsState(
        targetValue = if (isDragging) 1.2f else 1f,
        animationSpec = tween(150),
        label = "indicator_scale"
    )

    var showTapFeedback by remember { mutableStateOf(false) }
    LaunchedEffect(showTapFeedback) {
        if (showTapFeedback) {
            kotlinx.coroutines.delay(200)
            showTapFeedback = false
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "af_indicator")
    val afPulse by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "af_pulse"
    )

    Surface(
        modifier = Modifier
            .width(previewWidth)
            .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat())
            .shadow(4.dp, RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp),
        color = Color.Black
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Depth Stream",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )

            if (autofocusMode == "FACE_TRACKING") {
                FaceDetectionOverlay(
                    faceDetectionState = faceDetectionState,
                    imageWidth = bitmap.width,
                    imageHeight = bitmap.height,
                    onFaceTap = onFaceTap,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { newSize ->
                        if (currentSize.width > 0 && newSize.width != currentSize.width && isInitialized) {
                            dragOffset = Offset(
                                normalizedPosition.x * newSize.width,
                                normalizedPosition.y * newSize.height
                            )
                        }
                        currentSize = newSize
                        if (!isInitialized && newSize.width > 0) {
                            dragOffset = Offset(newSize.width / 2f, newSize.height / 2f)
                            normalizedPosition = Offset(0.5f, 0.5f)
                            isInitialized = true
                            onPositionChanged(0.5f, 0.5f)
                        }
                    }
                    .pointerInput(isHidden) {
                        detectTapGestures(
                            onTap = { offset ->
                                if (isHidden) {
                                    onTapToRestore()
                                } else {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    val normalizedX = offset.x / size.width
                                    val normalizedY = offset.y / size.height
                                    normalizedPosition = Offset(normalizedX, normalizedY)
                                    dragOffset = offset
                                    showTapFeedback = true
                                    onPositionChanged(normalizedX, normalizedY)
                                }
                            }
                        )
                    }
                    .pointerInput(isHidden) {
                        if (!isHidden) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    isDragging = true
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    val normalizedX = offset.x / size.width
                                    val normalizedY = offset.y / size.height
                                    normalizedPosition = Offset(normalizedX, normalizedY)
                                    dragOffset = offset
                                    onPositionChanged(normalizedX, normalizedY)
                                },
                                onDragEnd = {
                                    isDragging = false
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDrag = { _, dragAmount ->
                                    val newOffset = dragOffset + dragAmount
                                    val clampedOffset = Offset(
                                        x = newOffset.x.coerceIn(0f, size.width.toFloat()),
                                        y = newOffset.y.coerceIn(0f, size.height.toFloat())
                                    )
                                    dragOffset = clampedOffset
                                    val normalizedX = clampedOffset.x / size.width
                                    val normalizedY = clampedOffset.y / size.height
                                    normalizedPosition = Offset(normalizedX, normalizedY)
                                    onPositionChanged(normalizedX, normalizedY)
                                }
                            )
                        }
                    }
            ) {
                if (isInitialized && autofocusMode != "FACE_TRACKING") {
                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    dragOffset.x.roundToInt() - with(density) { 15.dp.toPx().roundToInt() },
                                    dragOffset.y.roundToInt() - with(density) { 15.dp.toPx().roundToInt() }
                                )
                            }
                            .size(30.dp)
                            .scale(scale)
                            .alpha(if (showTapFeedback) 0.6f else if (isAutofocusActive) afPulse else 0.8f)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(
                                    when {
                                        showTapFeedback -> Color.White.copy(alpha = 0.5f)
                                        isAutofocusActive && autofocusMode != "MANUAL" ->
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                        else -> Color.White.copy(alpha = 0.3f)
                                    }
                                )
                        )
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .align(Alignment.Center)
                        ) {
                            val crosshairColor = when {
                                isAutofocusActive && autofocusMode != "MANUAL" -> MaterialTheme.colorScheme.primary
                                else -> Color.White
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.dp)
                                    .align(Alignment.Center)
                                    .background(crosshairColor)
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(2.dp)
                                    .align(Alignment.Center)
                                    .background(crosshairColor)
                            )
                        }
                        if (isAutofocusActive && autofocusMode != "MANUAL") {
                            Icon(
                                imageVector = Icons.Default.CenterFocusStrong,
                                contentDescription = "AF Active",
                                modifier = Modifier
                                    .size(12.dp)
                                    .align(Alignment.TopEnd),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = !isDragging && !isInitialized && autofocusMode != "FACE_TRACKING",
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Text(
                    text = if (autofocusMode != "MANUAL") "Tap to focus" else "Tap to measure",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(4.dp)
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}
