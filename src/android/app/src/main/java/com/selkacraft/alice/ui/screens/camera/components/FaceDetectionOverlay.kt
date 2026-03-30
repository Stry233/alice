package com.selkacraft.alice.ui.screens.camera.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.selkacraft.alice.comm.autofocus.FaceDetectionState

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
        Canvas(modifier = Modifier.fillMaxSize()) {
            detectedFaces.forEach { face ->
                val bounds = face.getNormalizedBounds(imageWidth, imageHeight)

                val rect = Rect(
                    left = bounds.left * size.width,
                    top = bounds.top * size.height,
                    right = bounds.right * size.width,
                    bottom = bounds.bottom * size.height
                )

                val isSelected = face.trackingId == selectedFaceId

                drawRect(
                    color = face.color,
                    topLeft = rect.topLeft,
                    size = rect.size,
                    style = Stroke(
                        width = if (isSelected) 6f else 3f,
                        cap = StrokeCap.Round
                    )
                )

                val cornerLength = 20f
                val corners = listOf(
                    Pair(Offset(rect.left, rect.top), Offset(rect.left + cornerLength, rect.top)),
                    Pair(Offset(rect.left, rect.top), Offset(rect.left, rect.top + cornerLength)),
                    Pair(Offset(rect.right, rect.top), Offset(rect.right - cornerLength, rect.top)),
                    Pair(Offset(rect.right, rect.top), Offset(rect.right, rect.top + cornerLength)),
                    Pair(Offset(rect.left, rect.bottom), Offset(rect.left + cornerLength, rect.bottom)),
                    Pair(Offset(rect.left, rect.bottom), Offset(rect.left, rect.bottom - cornerLength)),
                    Pair(Offset(rect.right, rect.bottom), Offset(rect.right - cornerLength, rect.bottom)),
                    Pair(Offset(rect.right, rect.bottom), Offset(rect.right, rect.bottom - cornerLength))
                )

                corners.forEach { (start, end) ->
                    drawLine(
                        color = face.color,
                        start = start,
                        end = end,
                        strokeWidth = if (isSelected) 8f else 5f,
                        cap = StrokeCap.Round
                    )
                }

                val centerX = rect.center.x
                val centerY = rect.center.y
                drawCircle(
                    color = face.color,
                    radius = if (isSelected) 8f else 5f,
                    center = Offset(centerX, centerY)
                )

                if (isSelected) {
                    drawCircle(
                        color = face.color.copy(alpha = 0.3f),
                        radius = 20f,
                        center = Offset(centerX, centerY),
                        style = Stroke(width = 3f)
                    )
                }
            }
        }
    }
}
