package com.selkacraft.alice.ui.screens.calibration

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.collectAsState
import androidx.navigation.NavController
import com.selkacraft.alice.autofocus.CalibrationPoint
import com.selkacraft.alice.comm.core.ConnectionState
import com.selkacraft.alice.ui.screens.calibration.components.*
import com.selkacraft.alice.util.CalibrationViewModel
import com.selkacraft.alice.util.CameraViewModel
import kotlinx.coroutines.launch

/**
 * Full-screen Calibration tool
 * Provides a dedicated interface for creating autofocus calibration mappings
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalibrationScreen(
    navController: NavController,
    cameraViewModel: CameraViewModel
) {
    val context = LocalContext.current

    // Create calibration viewmodel that reuses Alice's device managers
    val calibrationViewModel = remember {
        CalibrationViewModel(
            context.applicationContext as Application,
            cameraViewModel
        )
    }

    val motorConnectionState by calibrationViewModel.motorConnectionState.collectAsStateWithLifecycle()
    val realSenseConnectionState by calibrationViewModel.realSenseConnectionState.collectAsStateWithLifecycle()
    val cameraConnectionState by calibrationViewModel.cameraConnectionState.collectAsStateWithLifecycle()
    val calibrationPoints by calibrationViewModel.calibrationPoints.collectAsStateWithLifecycle()
    val isTestMode by calibrationViewModel.isTestMode.collectAsStateWithLifecycle()
    val canExport by calibrationViewModel.canExport.collectAsStateWithLifecycle()
    val exportStatus by calibrationViewModel.exportStatus.collectAsStateWithLifecycle()

    val depthValue by calibrationViewModel.depthValue.collectAsStateWithLifecycle()
    val depthConfidence by calibrationViewModel.depthConfidence.collectAsStateWithLifecycle()
    val motorPosition by calibrationViewModel.motorPosition.collectAsStateWithLifecycle()

    // Disable autofocus when entering calibration screen
    val autofocusEnabled by cameraViewModel.autofocusEnabled.collectAsState()
    val autofocusMode by cameraViewModel.autofocusMode.collectAsState()
    DisposableEffect(Unit) {
        // Save current state
        val wasAutofocusEnabled = autofocusEnabled
        val previousMode = autofocusMode

        // Disable autofocus for calibration
        if (autofocusEnabled) {
            cameraViewModel.settingsManager.setAutofocusEnabled(false)
            cameraViewModel.settingsManager.setAutofocusMode(com.selkacraft.alice.comm.autofocus.FocusMode.MANUAL.name)
        }

        onDispose {
            // Restore previous autofocus state when leaving
            if (wasAutofocusEnabled) {
                cameraViewModel.settingsManager.setAutofocusEnabled(true)
                cameraViewModel.settingsManager.setAutofocusMode(previousMode.name)
            }
        }
    }

    var showExportDialog by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var selectedPointForEdit by remember { mutableStateOf<CalibrationPoint?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // Calculate depth range from calibration points
    val depthRange = remember(calibrationPoints) {
        if (calibrationPoints.isNotEmpty()) {
            val depths = calibrationPoints.map { it.depth }
            Pair(depths.minOrNull() ?: 0f, depths.maxOrNull() ?: 0f)
        } else null
    }

    // Handle export status
    LaunchedEffect(exportStatus) {
        when (exportStatus) {
            is CalibrationViewModel.ExportStatus.Success -> {
                snackbarHostState.showSnackbar(
                    message = "Calibration exported successfully",
                    duration = SnackbarDuration.Long
                )
                calibrationViewModel.resetExportStatus()
            }
            is CalibrationViewModel.ExportStatus.Error -> {
                snackbarHostState.showSnackbar(
                    message = "Export failed: ${(exportStatus as CalibrationViewModel.ExportStatus.Error).message}",
                    duration = SnackbarDuration.Long
                )
                calibrationViewModel.resetExportStatus()
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            CalibrationTopBar(
                navController = navController,
                cameraConnected = cameraConnectionState is ConnectionState.Connected ||
                        cameraConnectionState is ConnectionState.Active,
                motorConnected = motorConnectionState is ConnectionState.Connected ||
                        motorConnectionState is ConnectionState.Active,
                realSenseConnected = realSenseConnectionState is ConnectionState.Connected ||
                        realSenseConnectionState is ConnectionState.Active,
                pointCount = calibrationPoints.size,
                isTestMode = isTestMode,
                canExport = canExport,
                onTestModeToggle = { calibrationViewModel.toggleTestMode() },
                onExport = { showExportDialog = true },
                onClear = { showClearConfirmDialog = true }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(innerPadding)
        ) {
            // Main calibration content - adaptive landscape layout
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize()
            ) {
                val availableHeight = maxHeight - 20.dp // Account for padding
                // Calculate max height for each preview (16:9 ratio + spacing)
                val maxPreviewHeight = (availableHeight - 10.dp) / 2 // Divide by 2 for two previews plus spacing

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Motor Control & Points (22%)
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(0.22f),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                    ImprovedMotorControl(
                        position = motorPosition,
                        isConnected = motorConnectionState is ConnectionState.Connected ||
                                motorConnectionState is ConnectionState.Active,
                        isTestMode = isTestMode,
                        onPositionChange = { calibrationViewModel.setMotorPosition(it) }
                    )

                    // Record button
                    RecordButton(
                        enabled = (motorConnectionState is ConnectionState.Connected ||
                                motorConnectionState is ConnectionState.Active) &&
                                (realSenseConnectionState is ConnectionState.Connected ||
                                        realSenseConnectionState is ConnectionState.Active) &&
                                depthValue > 0 &&
                                depthValue <= 10.0f &&
                                depthConfidence >= 0.5f &&
                                !isTestMode,
                        onRecord = { calibrationViewModel.recordCalibrationPoint() }
                    )

                    // Progress indicator (only if needed)
                    if (calibrationPoints.size < 3 && calibrationPoints.isNotEmpty()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Text(
                                text = "${3 - calibrationPoints.size} more needed",
                                modifier = Modifier.padding(8.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Compact points list
                    if (calibrationPoints.isNotEmpty()) {
                        CompactPointsList(
                            points = calibrationPoints,
                            onDelete = { calibrationViewModel.deleteCalibrationPoint(it) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            selectedPointId = selectedPointForEdit?.id,
                            onEdit = { point ->
                                selectedPointForEdit = point
                            }
                        )
                    }
                }

                    // Center: Preview Panels - Adaptive to match side panel heights
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .wrapContentWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // UVC Camera Preview (16:9 aspect ratio, height-based sizing)
                        Card(
                            modifier = Modifier
                                .height(maxPreviewHeight)
                                .aspectRatio(16f / 9f),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            ZoomableUvcPreviewWithDepth(
                                viewModel = calibrationViewModel,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // Depth Preview with tap-to-measure (16:9 aspect ratio, height-based sizing)
                        Card(
                            modifier = Modifier
                                .height(maxPreviewHeight)
                                .aspectRatio(16f / 9f),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            SimplifiedDepthDisplay(
                                viewModel = calibrationViewModel,
                                connectionState = realSenseConnectionState,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    // Right: Calibration Graph (22%)
                    Card(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(0.22f),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        CompactGraphPanel(
                            viewModel = calibrationViewModel,
                            modifier = Modifier.fillMaxSize(),
                            selectedPointId = selectedPointForEdit?.id,
                            onPointSelected = { point ->
                                selectedPointForEdit = point
                            },
                            onPointDragged = { pointId, newDepth, newMotorPosition ->
                                calibrationViewModel.updateCalibrationPoint(pointId, newDepth, newMotorPosition)
                            },
                            onPointDeleted = { point ->
                                val pointIndex = calibrationPoints.indexOf(point) + 1
                                calibrationViewModel.deleteCalibrationPoint(point)
                                selectedPointForEdit = null
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Point #$pointIndex deleted")
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Export Dialog
    if (showExportDialog) {
        ExportDialog(
            onDismiss = { showExportDialog = false },
            onExport = { name, description, cameraModel, lensModel ->
                calibrationViewModel.exportCalibration(name, description, cameraModel, lensModel)
                showExportDialog = false
            },
            pointCount = calibrationPoints.size,
            depthRange = depthRange
        )
    }

    // Clear Confirmation Dialog
    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("Clear All Points?") },
            text = {
                Text("This will delete all ${calibrationPoints.size} calibration points. This action cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        calibrationViewModel.clearAllPoints()
                        showClearConfirmDialog = false
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("All calibration points cleared")
                        }
                    }
                ) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalibrationTopBar(
    navController: NavController,
    cameraConnected: Boolean,
    motorConnected: Boolean,
    realSenseConnected: Boolean,
    pointCount: Int,
    isTestMode: Boolean,
    canExport: Boolean,
    onTestModeToggle: () -> Unit,
    onExport: () -> Unit,
    onClear: () -> Unit
) {
    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Calibrator",
                    fontWeight = FontWeight.Bold
                )

                // Connection indicators - consistent for all devices
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StatusBadge(label = "Camera", connected = cameraConnected)
                    StatusBadge(label = "Depth", connected = realSenseConnected)
                    StatusBadge(label = "Motor", connected = motorConnected)

                    if (pointCount > 0) {
                        Badge(
                            containerColor = if (pointCount >= 3) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.tertiary
                            }
                        ) {
                            Text(pointCount.toString())
                        }
                    }
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }
        },
        actions = {
            // Test Mode Toggle
            if (canExport) {
                IconButton(onClick = onTestModeToggle) {
                    Icon(
                        imageVector = if (isTestMode) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = if (isTestMode) "Stop Test" else "Test",
                        tint = if (isTestMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Export
            IconButton(
                onClick = onExport,
                enabled = canExport
            ) {
                Icon(
                    imageVector = Icons.Default.Save,
                    contentDescription = "Export"
                )
            }

            // Clear
            if (pointCount > 0) {
                IconButton(onClick = onClear) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear"
                    )
                }
            }
        }
    )
}

@Composable
fun StatusBadge(label: String, connected: Boolean) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (connected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        }
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (connected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            },
            fontWeight = if (connected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
fun RecordButton(enabled: Boolean, onRecord: () -> Unit) {
    Button(
        onClick = onRecord,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = if (enabled) 3.dp else 0.dp
        )
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Record Point",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
    }
}
