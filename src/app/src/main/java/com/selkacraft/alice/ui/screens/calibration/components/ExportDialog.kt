package com.selkacraft.alice.ui.screens.calibration.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun ExportDialog(
    onDismiss: () -> Unit,
    onExport: (name: String, description: String, cameraModel: String, lensModel: String) -> Unit,
    pointCount: Int = 0,
    depthRange: Pair<Float, Float>? = null
) {
    var name by remember { mutableStateOf("Calibration_${System.currentTimeMillis()}") }
    var description by remember { mutableStateOf("") }
    var cameraModel by remember { mutableStateOf("") }
    var lensModel by remember { mutableStateOf("") }

    var currentStep by remember { mutableStateOf(0) }
    val totalSteps = 2

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 8.dp
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(0.dp)
            ) {
                // Header with gradient background
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                )
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Save,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(28.dp)
                                )
                                Text(
                                    text = "Export Calibration",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }

                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                                )
                            }
                        }

                        // Summary info
                        if (pointCount > 0 || depthRange != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                if (pointCount > 0) {
                                    InfoChip(
                                        icon = Icons.Default.Timeline,
                                        text = "$pointCount points",
                                        tint = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                                depthRange?.let { (min, max) ->
                                    InfoChip(
                                        icon = Icons.Default.Straighten,
                                        text = "${String.format("%.1f", min)}-${String.format("%.1f", max)}m",
                                        tint = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                        }
                    }
                }

                // Step indicator
                StepIndicator(
                    currentStep = currentStep,
                    totalSteps = totalSteps,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                )

                // Content area with scroll
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        when (currentStep) {
                            0 -> {
                                // Step 1: Basic Information
                                SectionHeader(
                                    icon = Icons.Default.Description,
                                    title = "Basic Information",
                                    subtitle = "Required fields for identification"
                                )

                                StyledTextField(
                                    value = name,
                                    onValueChange = { name = it },
                                    label = "Calibration Name",
                                    placeholder = "Enter a unique name",
                                    leadingIcon = Icons.Default.Label,
                                    isRequired = true,
                                    supportingText = "This will be the filename"
                                )

                                StyledTextField(
                                    value = description,
                                    onValueChange = { description = it },
                                    label = "Description",
                                    placeholder = "Optional notes about this calibration",
                                    leadingIcon = Icons.Default.Notes,
                                    isRequired = false,
                                    singleLine = false,
                                    minLines = 3
                                )
                            }

                            1 -> {
                                // Step 2: Equipment Information
                                SectionHeader(
                                    icon = Icons.Default.CameraAlt,
                                    title = "Equipment Information",
                                    subtitle = "Optional details for reference"
                                )

                                StyledTextField(
                                    value = cameraModel,
                                    onValueChange = { cameraModel = it },
                                    label = "Camera Model",
                                    placeholder = "e.g., Canon R5, Sony A7IV",
                                    leadingIcon = Icons.Default.PhotoCamera,
                                    isRequired = false
                                )

                                StyledTextField(
                                    value = lensModel,
                                    onValueChange = { lensModel = it },
                                    label = "Lens Model",
                                    placeholder = "e.g., 24-70mm f/2.8",
                                    leadingIcon = Icons.Default.CameraAlt,
                                    isRequired = false
                                )

                                // Preview card
                                PreviewCard(
                                    name = name,
                                    description = description,
                                    cameraModel = cameraModel,
                                    lensModel = lensModel,
                                    pointCount = pointCount
                                )
                            }
                        }

                        // Add bottom padding for scroll
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                // Bottom actions
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (currentStep > 0) {
                            OutlinedButton(
                                onClick = { currentStep-- },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Back")
                            }
                        }

                        Button(
                            onClick = {
                                if (currentStep < totalSteps - 1) {
                                    currentStep++
                                } else {
                                    onExport(name, description, cameraModel, lensModel)
                                }
                            },
                            modifier = Modifier.weight(if (currentStep > 0) 1f else 2f),
                            enabled = name.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (currentStep == totalSteps - 1) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.secondary
                                }
                            )
                        ) {
                            if (currentStep < totalSteps - 1) {
                                Text("Next")
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.Default.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Save,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Export", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepIndicator(
    currentStep: Int,
    totalSteps: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until totalSteps) {
            val isActive = i <= currentStep
            val isCurrentStep = i == currentStep

            Box(
                modifier = Modifier
                    .size(if (isCurrentStep) 10.dp else 8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        when {
                            isCurrentStep -> MaterialTheme.colorScheme.primary
                            isActive -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
            )

            if (i < totalSteps - 1) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(2.dp)
                        .background(
                            if (i < currentStep) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StyledTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    leadingIcon: ImageVector,
    isRequired: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    supportingText: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label)
                if (isRequired) {
                    Text(
                        text = "*",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        },
        placeholder = {
            Text(
                placeholder,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        },
        leadingIcon = {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
            )
        },
        supportingText = supportingText?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = if (!singleLine) minLines else 1,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        ),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = if (singleLine) ImeAction.Next else ImeAction.Default
        )
    )
}

@Composable
private fun PreviewCard(
    name: String,
    description: String,
    cameraModel: String,
    lensModel: String,
    pointCount: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Preview,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Export Preview",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                thickness = 0.5.dp
            )

            // Preview content
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PreviewRow("Name:", name.ifBlank { "Not specified" })
                if (description.isNotBlank()) {
                    PreviewRow("Description:", description)
                }
                if (cameraModel.isNotBlank()) {
                    PreviewRow("Camera:", cameraModel)
                }
                if (lensModel.isNotBlank()) {
                    PreviewRow("Lens:", lensModel)
                }
                PreviewRow("Points:", "$pointCount calibration points")
                PreviewRow("Format:", "JSON (Alice Compatible)")
            }
        }
    }
}

@Composable
private fun PreviewRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(max = 80.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun InfoChip(
    icon: ImageVector,
    text: String,
    tint: Color
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = tint.copy(alpha = 0.2f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = tint
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = tint,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
