package com.selkacraft.alice.ui.screens.camera.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.selkacraft.alice.comm.autofocus.FaceDetectionState
import com.selkacraft.alice.comm.autofocus.DetectedFace
import com.selkacraft.alice.comm.autofocus.TrackingState
import kotlinx.coroutines.launch

/**
 * Overlay component that displays face detection bounding boxes
 * with tap-to-select functionality
 */
@Composable
fun FaceDetectionOverlay(
    faceDetectionState: FaceDetectionState,
    imageWidth: Int,
    imageHeight: Int,
    onFaceTap: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val detectedFaces = faceDetectionState.detectedFaces
    val selectedFaceId = faceDetectionState.selectedFaceId

    // Track previous selected ID to detect selection changes
    var previousSelectedId by remember { mutableStateOf<Int?>(null) }

    // Determine primary face
    val primaryFace = detectedFaces.find { it.trackingId == selectedFaceId }
        ?: detectedFaces.maxByOrNull { it.score }

    // Detect if selection just changed to a new face
    val newlySelectedId = if (selectedFaceId != previousSelectedId && selectedFaceId != null) {
        selectedFaceId
    } else null

    LaunchedEffect(selectedFaceId) {
        previousSelectedId = selectedFaceId
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val normalizedX = offset.x / size.width
                    val normalizedY = offset.y / size.height
                    onFaceTap(normalizedX, normalizedY)
                }
            }
    ) {
        // Render each face with its own animation state
        detectedFaces.forEach { face ->
            key(face.trackingId) {
                val isPrimary = face == primaryFace
                val justSelected = face.trackingId == newlySelectedId

                AnimatedFaceBox(
                    face = face,
                    isPrimary = isPrimary,
                    justSelected = justSelected,
                    imageWidth = imageWidth,
                    imageHeight = imageHeight
                )
            }
        }
    }
}

/**
 * Single face with smooth position and state animations
 */
@Composable
private fun AnimatedFaceBox(
    face: DetectedFace,
    isPrimary: Boolean,
    justSelected: Boolean,
    imageWidth: Int,
    imageHeight: Int
) {
    val bounds = face.getNormalizedBounds(imageWidth, imageHeight)

    // Smooth position animation spec - fast response, no bounce
    val positionSpec = spring<Float>(
        dampingRatio = 0.85f,
        stiffness = 400f
    )

    // Animated position values
    val animLeft = remember { Animatable(bounds.left) }
    val animTop = remember { Animatable(bounds.top) }
    val animWidth = remember { Animatable(bounds.width) }
    val animHeight = remember { Animatable(bounds.height) }

    // Animate to new position when bounds change
    LaunchedEffect(bounds.left, bounds.top, bounds.width, bounds.height) {
        launch { animLeft.animateTo(bounds.left, positionSpec) }
        launch { animTop.animateTo(bounds.top, positionSpec) }
        launch { animWidth.animateTo(bounds.width, positionSpec) }
        launch { animHeight.animateTo(bounds.height, positionSpec) }
    }

    // Eye position animation
    val hasEyeTracking = face.trackingState == TrackingState.EYE_LOCKED && face.hasEyes
    val trackedEye = if (hasEyeTracking) face.nearestEye else null

    val animEyeX = remember { Animatable(trackedEye?.x ?: 0.5f) }
    val animEyeY = remember { Animatable(trackedEye?.y ?: 0.5f) }

    LaunchedEffect(trackedEye?.x, trackedEye?.y) {
        trackedEye?.let { eye ->
            launch { animEyeX.animateTo(eye.x, positionSpec) }
            launch { animEyeY.animateTo(eye.y, positionSpec) }
        }
    }

    // Selection pulse animation - triggers when face becomes selected
    var selectionTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(justSelected) {
        if (justSelected) {
            selectionTrigger++
        }
    }

    // Selection pulse animation value
    var pulseValue by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(selectionTrigger) {
        if (selectionTrigger > 0) {
            // Animate: 0 -> 1 -> 0 over 400ms
            animate(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = tween(150, easing = FastOutSlowInEasing)
            ) { value, _ -> pulseValue = value }

            animate(
                initialValue = 1f,
                targetValue = 0f,
                animationSpec = tween(250, easing = FastOutSlowInEasing)
            ) { value, _ -> pulseValue = value }
        }
    }

    // Animated color based on tracking state
    val targetColor = when {
        !isPrimary -> Colors.unselected
        hasEyeTracking -> Colors.primaryFaint
        face.trackingState == TrackingState.FACE_ONLY -> Colors.secondary
        face.trackingState == TrackingState.PREDICTED -> Colors.warning
        else -> Colors.secondaryFaint
    }

    val animatedColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = 200),
        label = "faceColor"
    )

    // Animated stroke width - bolder when selected, extra bold during pulse
    val baseStroke = if (isPrimary) 4.5f else 3f
    val pulseStroke = baseStroke + (pulseValue * 2f)

    Canvas(modifier = Modifier.fillMaxSize()) {
        val faceLeft = animLeft.value * size.width
        val faceTop = animTop.value * size.height
        val faceWidth = animWidth.value * size.width
        val faceHeight = animHeight.value * size.height

        // Selection pulse: slight scale up
        val scale = 1f + (pulseValue * 0.04f)
        val scaledWidth = faceWidth * scale
        val scaledHeight = faceHeight * scale
        val scaledLeft = faceLeft - (scaledWidth - faceWidth) / 2f
        val scaledTop = faceTop - (scaledHeight - faceHeight) / 2f

        val isTooSmall = scaledWidth < 40f || scaledHeight < 40f
        val cornerRadius = if (isTooSmall) 4f else minOf(scaledWidth, scaledHeight) * 0.1f

        // Draw selection glow during pulse
        if (pulseValue > 0.01f && isPrimary) {
            val glowExpand = pulseValue * 6f
            drawRoundRect(
                color = Colors.selectionGlow.copy(alpha = pulseValue * 0.5f),
                topLeft = Offset(scaledLeft - glowExpand, scaledTop - glowExpand),
                size = Size(scaledWidth + glowExpand * 2, scaledHeight + glowExpand * 2),
                cornerRadius = CornerRadius(cornerRadius + glowExpand, cornerRadius + glowExpand),
                style = Stroke(width = 3f)
            )
        }

        // Main face bounding box
        drawRoundRect(
            color = animatedColor,
            topLeft = Offset(scaledLeft, scaledTop),
            size = Size(scaledWidth, scaledHeight),
            cornerRadius = CornerRadius(cornerRadius, cornerRadius),
            style = Stroke(width = pulseStroke)
        )

        // Eye tracking box
        if (isPrimary && hasEyeTracking) {
            drawEyeBox(
                eyeX = animEyeX.value,
                eyeY = animEyeY.value,
                faceWidth = scaledWidth,
                isFaceSmall = isTooSmall,
                pulseValue = pulseValue
            )
        }
    }
}

/**
 * Refined color palette - bold and clear
 */
private object Colors {
    val primary = Color(0xFF66BB6A)           // Clear green for eye tracking
    val primaryFaint = Color(0x9966BB6A)      // Semi-transparent when eye locked
    val secondary = Color(0xFF42A5F5)         // Clear blue for face-only
    val secondaryFaint = Color(0x7742A5F5)    // Faint secondary
    val warning = Color(0xFFFFA726)           // Orange for predicted
    val unselected = Color(0xBBFFFFFF)        // Light white for unselected faces
    val selectionGlow = Color(0xFFFFFFFF)     // White glow for selection feedback
}

/**
 * Draw a bounding box around the tracked eye
 */
private fun DrawScope.drawEyeBox(
    eyeX: Float,
    eyeY: Float,
    faceWidth: Float,
    isFaceSmall: Boolean,
    pulseValue: Float
) {
    // Eye box size proportional to face
    val baseSize = (faceWidth * 0.22f).coerceIn(24f, 64f)
    val actualSize = if (isFaceSmall) baseSize * 0.75f else baseSize

    // Slight scale during pulse
    val scale = 1f + (pulseValue * 0.06f)
    val scaledSize = actualSize * scale

    val eyePixelX = eyeX * size.width
    val eyePixelY = eyeY * size.height

    val halfSize = scaledSize / 2f
    val cornerRadius = scaledSize * 0.2f

    // Bold, visible eye box
    val strokeWidth = 4f + (pulseValue * 1.5f)

    drawRoundRect(
        color = Colors.primary,
        topLeft = Offset(eyePixelX - halfSize, eyePixelY - halfSize),
        size = Size(scaledSize, scaledSize),
        cornerRadius = CornerRadius(cornerRadius, cornerRadius),
        style = Stroke(width = strokeWidth)
    )
}
