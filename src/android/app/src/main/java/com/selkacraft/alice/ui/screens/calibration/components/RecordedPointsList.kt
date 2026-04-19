package com.selkacraft.alice.ui.screens.calibration.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.selkacraft.alice.autofocus.CalibrationPoint

@Composable
fun CompactPointsList(
    points: List<CalibrationPoint>,
    onDelete: (CalibrationPoint) -> Unit,
    modifier: Modifier = Modifier,
    selectedPointId: String? = null,
    onEdit: ((CalibrationPoint) -> Unit)? = null
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
                .padding(8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Points (${points.size})",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (onEdit != null) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Tap to edit",
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        )
                    }
                }

                if (points.size < 3) {
                    Text(
                        text = "${3 - points.size} more",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Points list
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(
                    items = points,
                    key = { it.id }
                ) { point ->
                    CompactPointItem(
                        point = point,
                        index = points.indexOf(point) + 1,
                        isSelected = point.id == selectedPointId,
                        onDelete = { onDelete(point) },
                        onEdit = onEdit?.let { { it(point) } }
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactPointItem(
    point: CalibrationPoint,
    index: Int,
    isSelected: Boolean = false,
    onDelete: () -> Unit,
    onEdit: (() -> Unit)? = null
) {
    val hapticFeedback = LocalHapticFeedback.current

    val backgroundColor = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainer
    }

    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        Color.Transparent
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onEdit != null) {
                    Modifier.clickable {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        onEdit()
                    }
                } else Modifier
            ),
        shape = RoundedCornerShape(4.dp),
        color = backgroundColor,
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(1.dp, borderColor)
        } else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Point number
            Text(
                text = "#$index",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.width(24.dp)
            )

            // Depth
            Text(
                text = point.getFormattedDepth(),
                style = MaterialTheme.typography.bodySmall,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.weight(1f)
            )

            // Motor position
            Text(
                text = point.motorPosition.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                } else {
                    MaterialTheme.colorScheme.secondary
                },
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )

            // Confidence dot
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(
                        when {
                            point.confidence > 0.8f -> Color(0xFF4CAF50)
                            point.confidence > 0.5f -> Color(0xFFFF9800)
                            else -> Color(0xFFFF5252)
                        },
                        CircleShape
                    )
            )

            Spacer(modifier = Modifier.width(4.dp))

            // Edit indicator (only shown if onEdit is available and not selected)
            if (onEdit != null && !isSelected) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit",
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                )
            }

            // Delete button (always available)
            IconButton(
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    onDelete()
                },
                modifier = Modifier.size(18.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Delete",
                    modifier = Modifier.size(12.dp),
                    tint = if (isSelected) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                    }
                )
            }
        }
    }
}
