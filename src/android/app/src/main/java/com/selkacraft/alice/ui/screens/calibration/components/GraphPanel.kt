package com.selkacraft.alice.ui.screens.calibration.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.selkacraft.alice.autofocus.CalibrationPoint
import com.selkacraft.alice.util.CalibrationViewModel
import kotlin.math.roundToInt
import kotlin.math.sqrt

@OptIn(ExperimentalTextApi::class)
@Composable
fun CompactGraphPanel(
    viewModel: CalibrationViewModel,
    modifier: Modifier = Modifier,
    selectedPointId: String? = null,
    onPointSelected: ((CalibrationPoint?) -> Unit)? = null,
    onPointDragged: ((pointId: String, newDepth: Float, newMotorPosition: Int) -> Unit)? = null,
    onPointDeleted: ((CalibrationPoint) -> Unit)? = null
) {
    val calibrationPoints by viewModel.calibrationPoints.collectAsStateWithLifecycle()
    val currentMapping by viewModel.currentMapping.collectAsStateWithLifecycle()
    val currentDepth by viewModel.depthValue.collectAsStateWithLifecycle()
    val currentMotorPosition by viewModel.motorPosition.collectAsStateWithLifecycle()
    val isTestMode by viewModel.isTestMode.collectAsStateWithLifecycle()

    val textMeasurer = rememberTextMeasurer()
    val hapticFeedback = LocalHapticFeedback.current
    val density = LocalDensity.current

    // Store calculated point positions for hit testing
    var pointPositions by remember { mutableStateOf<List<Pair<CalibrationPoint, Offset>>>(emptyList()) }

    // Drag state - track the current drag offset for the selected point
    var dragOffset by remember { mutableStateOf<Offset?>(null) }
    var isDragging by remember { mutableStateOf(false) }

    // Graph layout info for coordinate conversion
    var graphInfo by remember { mutableStateOf<GraphLayoutInfo?>(null) }

    // Layout dimensions (same as in drawProfessionalGraph)
    val leftPaddingDp = 35.dp
    val rightPaddingDp = 15.dp
    val topPaddingDp = 15.dp
    val bottomPaddingDp = 30.dp

    // Get the selected point
    val selectedPoint = calibrationPoints.find { it.id == selectedPointId }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(4.dp)
    ) {
        if (calibrationPoints.isNotEmpty()) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(calibrationPoints, selectedPointId, onPointSelected, onPointDragged) {
                        detectTapGestures { tapOffset ->
                            // Find if any point was tapped (within touch radius)
                            val touchRadiusPx = with(density) { 24.dp.toPx() }

                            val tappedPoint = pointPositions.find { (_, pointOffset) ->
                                val distance = sqrt(
                                    (tapOffset.x - pointOffset.x) * (tapOffset.x - pointOffset.x) +
                                            (tapOffset.y - pointOffset.y) * (tapOffset.y - pointOffset.y)
                                )
                                distance <= touchRadiusPx
                            }

                            if (tappedPoint != null) {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                onPointSelected?.invoke(tappedPoint.first)
                            } else {
                                // Tap outside points - deselect
                                onPointSelected?.invoke(null)
                            }
                        }
                    }
                    .pointerInput(selectedPointId, graphInfo, onPointDragged) {
                        if (selectedPointId != null && onPointDragged != null && graphInfo != null) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    // Check if drag started on the selected point
                                    val selectedPointPos = pointPositions.find { it.first.id == selectedPointId }?.second
                                    if (selectedPointPos != null) {
                                        val distance = sqrt(
                                            (offset.x - selectedPointPos.x) * (offset.x - selectedPointPos.x) +
                                                    (offset.y - selectedPointPos.y) * (offset.y - selectedPointPos.y)
                                        )
                                        if (distance <= with(density) { 30.dp.toPx() }) {
                                            isDragging = true
                                            dragOffset = selectedPointPos
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }
                                    }
                                },
                                onDrag = { change, dragAmount ->
                                    if (isDragging && dragOffset != null) {
                                        change.consume()
                                        dragOffset = Offset(
                                            (dragOffset!!.x + dragAmount.x).coerceIn(
                                                graphInfo!!.leftPadding,
                                                graphInfo!!.leftPadding + graphInfo!!.graphWidth
                                            ),
                                            (dragOffset!!.y + dragAmount.y).coerceIn(
                                                graphInfo!!.topPadding,
                                                graphInfo!!.topPadding + graphInfo!!.graphHeight
                                            )
                                        )
                                    }
                                },
                                onDragEnd = {
                                    if (isDragging && dragOffset != null && graphInfo != null) {
                                        // Convert drag position back to depth/motor values
                                        val info = graphInfo!!
                                        val newDepth = ((dragOffset!!.x - info.leftPadding) / info.graphWidth * info.depthRange)
                                            .coerceIn(0.01f, 10f)
                                        val newMotorPosition = ((1f - (dragOffset!!.y - info.topPadding) / info.graphHeight) * 4095f)
                                            .toInt()
                                            .coerceIn(0, 4095)

                                        onPointDragged(selectedPointId, newDepth, newMotorPosition)
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                    isDragging = false
                                    dragOffset = null
                                },
                                onDragCancel = {
                                    isDragging = false
                                    dragOffset = null
                                }
                            )
                        }
                    }
            ) {
                val leftPadding = leftPaddingDp.toPx()
                val rightPadding = rightPaddingDp.toPx()
                val topPadding = topPaddingDp.toPx()
                val bottomPadding = bottomPaddingDp.toPx()

                val graphWidth = size.width - leftPadding - rightPadding
                val graphHeight = size.height - topPadding - bottomPadding

                // Calculate depth range
                val minDepth = 0f
                val maxDepth = (calibrationPoints.maxByOrNull { it.depth }?.depth ?: 5f) * 1.1f
                val depthRange = maxDepth - minDepth

                // Store graph info for coordinate conversion
                graphInfo = GraphLayoutInfo(
                    leftPadding = leftPadding,
                    topPadding = topPadding,
                    graphWidth = graphWidth,
                    graphHeight = graphHeight,
                    depthRange = depthRange
                )

                // Calculate and store point positions for hit testing
                pointPositions = calibrationPoints.map { point ->
                    val x = leftPadding + (point.depth / depthRange) * graphWidth
                    val y = topPadding + graphHeight - (point.motorPosition / 4095f) * graphHeight
                    point to Offset(x, y)
                }

                drawProfessionalGraph(
                    points = calibrationPoints,
                    currentDepth = if (currentDepth > 0 && isTestMode) currentDepth else null,
                    currentMotorPosition = if (isTestMode) currentMotorPosition else null,
                    textMeasurer = textMeasurer,
                    mapping = currentMapping,
                    selectedPointId = selectedPointId,
                    dragOffset = if (isDragging) dragOffset else null
                )
            }

            // Trash bin icon - shown when a point is selected (positioned at top-right)
            if (selectedPoint != null && onPointDeleted != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    FilledTonalIconButton(
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            onPointDeleted(selectedPoint)
                        },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete point",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Hint text when point is selected
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
                    ) {
                        Text(
                            text = "Drag to adjust",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        } else {
            // Empty state
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Calibration Curve",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Record points to visualize",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    if (onPointSelected != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap points to edit",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

// Helper data class to store graph layout information
private data class GraphLayoutInfo(
    val leftPadding: Float,
    val topPadding: Float,
    val graphWidth: Float,
    val graphHeight: Float,
    val depthRange: Float
)

@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawProfessionalGraph(
    points: List<CalibrationPoint>,
    currentDepth: Float?,
    currentMotorPosition: Int?,
    textMeasurer: TextMeasurer,
    mapping: com.selkacraft.alice.comm.autofocus.AutofocusMapping?,
    selectedPointId: String? = null,
    dragOffset: Offset? = null
) {
    // Layout dimensions
    val leftPadding = 35.dp.toPx()
    val rightPadding = 15.dp.toPx()
    val topPadding = 15.dp.toPx()
    val bottomPadding = 30.dp.toPx()

    val graphWidth = size.width - leftPadding - rightPadding
    val graphHeight = size.height - topPadding - bottomPadding

    // Colors
    val gridColor = Color.Gray.copy(alpha = 0.15f)
    val axisColor = Color.Gray.copy(alpha = 0.4f)
    val textColor = Color.Gray.copy(alpha = 0.6f)
    val pointColor = Color(0xFF2196F3)
    val selectedPointColor = Color(0xFFFF5722) // Orange for selected point
    val curveColor = Color(0xFF4CAF50)
    val currentPositionColor = Color(0xFFFF9800)

    if (points.isEmpty()) return

    // Calculate data ranges
    val minDepth = 0f
    val maxDepth = (points.maxByOrNull { it.depth }?.depth ?: 5f) * 1.1f
    val depthRange = maxDepth - minDepth

    // Draw grid lines
    drawGrid(
        leftPadding = leftPadding,
        topPadding = topPadding,
        graphWidth = graphWidth,
        graphHeight = graphHeight,
        gridColor = gridColor,
        depthRange = depthRange,
        maxDepth = maxDepth,
        textMeasurer = textMeasurer,
        textColor = textColor
    )

    // Draw axes
    drawAxes(
        leftPadding = leftPadding,
        topPadding = topPadding,
        graphWidth = graphWidth,
        graphHeight = graphHeight,
        axisColor = axisColor
    )

    // Draw axis labels
    drawAxisLabels(
        leftPadding = leftPadding,
        topPadding = topPadding,
        bottomPadding = bottomPadding,
        graphWidth = graphWidth,
        graphHeight = graphHeight,
        textMeasurer = textMeasurer,
        textColor = textColor
    )

    // Draw curve if we have enough points
    if (points.size >= 2 && mapping != null) {
        drawCurve(
            mapping = mapping,
            leftPadding = leftPadding,
            topPadding = topPadding,
            graphWidth = graphWidth,
            graphHeight = graphHeight,
            minDepth = minDepth,
            maxDepth = maxDepth,
            depthRange = depthRange,
            curveColor = curveColor
        )
    }

    // Draw calibration points
    drawPoints(
        points = points,
        leftPadding = leftPadding,
        topPadding = topPadding,
        graphWidth = graphWidth,
        graphHeight = graphHeight,
        depthRange = depthRange,
        pointColor = pointColor,
        selectedPointId = selectedPointId,
        selectedPointColor = selectedPointColor,
        dragOffset = dragOffset
    )

    // Draw current position if in test mode
    if (currentDepth != null && currentMotorPosition != null && currentDepth > 0) {
        drawCurrentPosition(
            currentDepth = currentDepth,
            currentMotorPosition = currentMotorPosition,
            leftPadding = leftPadding,
            topPadding = topPadding,
            graphWidth = graphWidth,
            graphHeight = graphHeight,
            minDepth = minDepth,
            maxDepth = maxDepth,
            depthRange = depthRange,
            currentPositionColor = currentPositionColor
        )
    }
}

private fun DrawScope.drawGrid(
    leftPadding: Float,
    topPadding: Float,
    graphWidth: Float,
    graphHeight: Float,
    gridColor: Color,
    depthRange: Float,
    maxDepth: Float,
    textMeasurer: TextMeasurer,
    textColor: Color
) {
    // Vertical grid lines (depth)
    val depthSteps = 5
    for (i in 0..depthSteps) {
        val x = leftPadding + (i.toFloat() / depthSteps) * graphWidth
        drawLine(
            color = gridColor,
            start = Offset(x, topPadding),
            end = Offset(x, topPadding + graphHeight),
            strokeWidth = 0.5.dp.toPx()
        )

        // Draw depth values
        if (i % 2 == 0) { // Show every other value to avoid crowding
            val depthValue = (i.toFloat() / depthSteps) * maxDepth
            val label = String.format("%.1f", depthValue)
            val textResult = textMeasurer.measure(
                text = AnnotatedString(label),
                style = TextStyle(fontSize = 8.sp, color = textColor)
            )
            drawText(
                textLayoutResult = textResult,
                topLeft = Offset(
                    x - textResult.size.width / 2,
                    topPadding + graphHeight + 4.dp.toPx()
                )
            )
        }
    }

    // Horizontal grid lines (motor position)
    val motorSteps = 4
    for (i in 0..motorSteps) {
        val y = topPadding + graphHeight - (i.toFloat() / motorSteps) * graphHeight
        drawLine(
            color = gridColor,
            start = Offset(leftPadding, y),
            end = Offset(leftPadding + graphWidth, y),
            strokeWidth = 0.5.dp.toPx()
        )

        // Draw motor values
        val motorValue = (i.toFloat() / motorSteps) * 4095
        val label = motorValue.roundToInt().toString()
        val textResult = textMeasurer.measure(
            text = AnnotatedString(label),
            style = TextStyle(fontSize = 8.sp, color = textColor)
        )
        drawText(
            textLayoutResult = textResult,
            topLeft = Offset(
                leftPadding - textResult.size.width - 4.dp.toPx(),
                y - textResult.size.height / 2
            )
        )
    }
}

private fun DrawScope.drawAxes(
    leftPadding: Float,
    topPadding: Float,
    graphWidth: Float,
    graphHeight: Float,
    axisColor: Color
) {
    // X-axis
    drawLine(
        color = axisColor,
        start = Offset(leftPadding, topPadding + graphHeight),
        end = Offset(leftPadding + graphWidth, topPadding + graphHeight),
        strokeWidth = 1.dp.toPx()
    )

    // Y-axis
    drawLine(
        color = axisColor,
        start = Offset(leftPadding, topPadding),
        end = Offset(leftPadding, topPadding + graphHeight),
        strokeWidth = 1.dp.toPx()
    )
}

@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawAxisLabels(
    leftPadding: Float,
    topPadding: Float,
    bottomPadding: Float,
    graphWidth: Float,
    graphHeight: Float,
    textMeasurer: TextMeasurer,
    textColor: Color
) {
    // X-axis label (Depth)
    val xLabel = textMeasurer.measure(
        text = AnnotatedString("Depth (m)"),
        style = TextStyle(
            fontSize = 10.sp,
            color = textColor,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
        )
    )
    drawText(
        textLayoutResult = xLabel,
        topLeft = Offset(
            leftPadding + graphWidth / 2 - xLabel.size.width / 2,
            size.height - 12.dp.toPx()
        )
    )

    // Y-axis label (Motor Position) - Rotated 90 degrees
    val yLabel = textMeasurer.measure(
        text = AnnotatedString("Motor Position"),
        style = TextStyle(
            fontSize = 10.sp,
            color = textColor,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
        )
    )

    // Save the current canvas state and rotate for vertical text
    drawContext.canvas.save()
    rotate(
        degrees = -90f,
        pivot = Offset(
            8.dp.toPx(),
            topPadding + graphHeight / 2
        )
    ) {
        drawText(
            textLayoutResult = yLabel,
            topLeft = Offset(
                8.dp.toPx() - yLabel.size.width / 2,
                topPadding + graphHeight / 2 - yLabel.size.height / 2
            )
        )
    }
    drawContext.canvas.restore()
}

private fun DrawScope.drawCurve(
    mapping: com.selkacraft.alice.comm.autofocus.AutofocusMapping,
    leftPadding: Float,
    topPadding: Float,
    graphWidth: Float,
    graphHeight: Float,
    minDepth: Float,
    maxDepth: Float,
    depthRange: Float,
    curveColor: Color
) {
    val path = Path()
    var firstPoint = true

    // Sample curve at regular intervals for smooth rendering
    val samples = 50
    for (i in 0..samples) {
        val depth = minDepth + (i.toFloat() / samples) * depthRange
        val motorPosition = mapping.getMotorPosition(depth) ?: continue

        val x = leftPadding + (depth / depthRange) * graphWidth
        val y = topPadding + graphHeight - (motorPosition / 4095f) * graphHeight

        if (firstPoint) {
            path.moveTo(x, y)
            firstPoint = false
        } else {
            path.lineTo(x, y)
        }
    }

    drawPath(
        path = path,
        color = curveColor,
        style = Stroke(
            width = 2.dp.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
}

private fun DrawScope.drawPoints(
    points: List<CalibrationPoint>,
    leftPadding: Float,
    topPadding: Float,
    graphWidth: Float,
    graphHeight: Float,
    depthRange: Float,
    pointColor: Color,
    selectedPointId: String? = null,
    selectedPointColor: Color = Color(0xFFFF5722),
    dragOffset: Offset? = null
) {
    points.forEach { point ->
        val isSelected = point.id == selectedPointId

        // Use drag offset for selected point if dragging, otherwise calculate from data
        val (x, y) = if (isSelected && dragOffset != null) {
            dragOffset.x to dragOffset.y
        } else {
            val calcX = leftPadding + (point.depth / depthRange) * graphWidth
            val calcY = topPadding + graphHeight - (point.motorPosition / 4095f) * graphHeight
            calcX to calcY
        }

        if (isSelected) {
            // Draw selection indicator ring (larger when dragging)
            val ringScale = if (dragOffset != null) 1.3f else 1f
            drawCircle(
                color = selectedPointColor.copy(alpha = 0.2f),
                radius = 12.dp.toPx() * ringScale,
                center = Offset(x, y)
            )
            drawCircle(
                color = selectedPointColor.copy(alpha = 0.4f),
                radius = 8.dp.toPx() * ringScale,
                center = Offset(x, y)
            )
            // Draw point with selection color
            drawCircle(
                color = Color.White,
                radius = 6.dp.toPx(),
                center = Offset(x, y)
            )
            drawCircle(
                color = selectedPointColor,
                radius = 5.dp.toPx(),
                center = Offset(x, y)
            )

            // Draw crosshairs when dragging to show precise position
            if (dragOffset != null) {
                val dashPattern = floatArrayOf(4.dp.toPx(), 4.dp.toPx())
                drawLine(
                    color = selectedPointColor.copy(alpha = 0.5f),
                    start = Offset(x, topPadding),
                    end = Offset(x, topPadding + graphHeight),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(dashPattern)
                )
                drawLine(
                    color = selectedPointColor.copy(alpha = 0.5f),
                    start = Offset(leftPadding, y),
                    end = Offset(leftPadding + graphWidth, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(dashPattern)
                )
            }
        } else {
            // Draw normal point with border for better visibility
            drawCircle(
                color = Color.White,
                radius = 4.dp.toPx(),
                center = Offset(x, y)
            )
            drawCircle(
                color = pointColor,
                radius = 3.dp.toPx(),
                center = Offset(x, y)
            )
        }
    }
}

private fun DrawScope.drawCurrentPosition(
    currentDepth: Float,
    currentMotorPosition: Int,
    leftPadding: Float,
    topPadding: Float,
    graphWidth: Float,
    graphHeight: Float,
    minDepth: Float,
    maxDepth: Float,
    depthRange: Float,
    currentPositionColor: Color
) {
    val x = leftPadding + (currentDepth.coerceIn(minDepth, maxDepth) / depthRange) * graphWidth
    val y = topPadding + graphHeight - (currentMotorPosition / 4095f) * graphHeight

    // Draw crosshair lines with dash pattern
    val dashPattern = floatArrayOf(4.dp.toPx(), 4.dp.toPx())

    drawLine(
        color = currentPositionColor.copy(alpha = 0.3f),
        start = Offset(x, topPadding),
        end = Offset(x, topPadding + graphHeight),
        strokeWidth = 1.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(dashPattern)
    )

    drawLine(
        color = currentPositionColor.copy(alpha = 0.3f),
        start = Offset(leftPadding, y),
        end = Offset(leftPadding + graphWidth, y),
        strokeWidth = 1.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(dashPattern)
    )

    // Draw current position marker with glow effect
    drawCircle(
        color = currentPositionColor.copy(alpha = 0.2f),
        radius = 8.dp.toPx(),
        center = Offset(x, y)
    )
    drawCircle(
        color = Color.White,
        radius = 5.dp.toPx(),
        center = Offset(x, y)
    )
    drawCircle(
        color = currentPositionColor,
        radius = 4.dp.toPx(),
        center = Offset(x, y)
    )
}
