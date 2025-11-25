package com.selkacraft.alice.ui.screens.camera

import android.util.Log
import android.view.SurfaceHolder
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.selkacraft.alice.comm.autofocus.FocusMode
import com.selkacraft.alice.comm.core.ConnectionState
import com.selkacraft.alice.ui.screens.camera.components.CameraPreview
import com.selkacraft.alice.ui.screens.camera.components.DeviceStatusIndicator
import com.selkacraft.alice.ui.screens.camera.components.MotorControlSlider
import com.selkacraft.alice.ui.screens.camera.components.RealSenseOverlay
import com.selkacraft.alice.util.CameraViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG_CS = "CameraScreen"

@Composable
fun CameraScreen(
    videoAspectRatio: Float?,
    onSurfaceHolderAvailable: (SurfaceHolder) -> Unit,
    onSurfaceHolderDestroyed: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: CameraViewModel,
    modifier: Modifier = Modifier
) {
    Log.d(TAG_CS, "Composing with videoAspectRatio: $videoAspectRatio")

    // Device connection states
    val motorConnectionState by viewModel.motorConnectionState.collectAsState()
    val motorPosition by viewModel.motorPosition.collectAsState()
    val coordinatorState by viewModel.coordinatorState.collectAsState()
    val cameraState by viewModel.cameraState.collectAsState()
    val cameraConnectionState by viewModel.cameraConnectionState.collectAsState()
    val isChangingResolution by viewModel.isChangingResolution.collectAsState()

    // RealSense states
    val realSenseConnectionState by viewModel.realSenseConnectionState.collectAsState()
    val realSenseCenterDepth by viewModel.realSenseCenterDepth.collectAsState()
    val realSenseDepthConfidence by viewModel.realSenseDepthConfidence.collectAsState()
    val realSenseDepthBitmap by viewModel.realSenseDepthBitmap.collectAsState()

    // Autofocus states
    val autofocusEnabled by viewModel.autofocusEnabled.collectAsState()
    val autofocusMode by viewModel.autofocusMode.collectAsState()
    val autofocusMapping by viewModel.autofocusMapping.collectAsState()
    val isAutofocusActive by viewModel.isAutofocusActive.collectAsState()

    // Face detection states
    val faceDetectionState by viewModel.faceDetectionState.collectAsState()
    val colorBitmap by viewModel.realSenseColorBitmap.collectAsState()

    // Check if autofocus is available (mapping loaded + both devices connected)
    val isAutofocusAvailable = remember(autofocusMapping, motorConnectionState, realSenseConnectionState) {
        autofocusMapping != null &&
                (motorConnectionState is ConnectionState.Connected || motorConnectionState is ConnectionState.Active) &&
                (realSenseConnectionState is ConnectionState.Connected || realSenseConnectionState is ConnectionState.Active)
    }

    // Keep track of navigation state
    var isNavigatingToSettings by remember { mutableStateOf(false) }

    // Store current surface holder reference
    var currentSurfaceHolder by remember { mutableStateOf<SurfaceHolder?>(null) }

    // Expandable FAB state
    var isFabExpanded by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    // Only register once when the screen is first displayed
    DisposableEffect(Unit) {
        Log.d(TAG_CS, "CameraScreen appeared, ensuring managers are registered")
        // Don't re-register if already registered
        if (!viewModel.isRegistered()) {
            viewModel.register()
        }

        onDispose {
            Log.d(TAG_CS, "CameraScreen disappearing")
            // Don't unregister when navigating to settings
            if (!isNavigatingToSettings) {
                Log.d(TAG_CS, "Not navigating to settings, will unregister if needed")
            }
        }
    }

    // Monitor camera connection state and provide surface when ready
    LaunchedEffect(cameraConnectionState, isChangingResolution) {
        // Don't provide surface while resolution is changing
        if (isChangingResolution) {
            Log.d(TAG_CS, "Resolution change in progress, not providing surface")
            return@LaunchedEffect
        }

        when (cameraConnectionState) {
            is ConnectionState.Connected,
            is ConnectionState.Active -> {
                Log.d(TAG_CS, "Camera is connected/active, checking if surface is needed")

                // Small delay to ensure camera is ready
                delay(500)

                // If we have a surface holder and camera doesn't have preview, provide it
                currentSurfaceHolder?.let { holder ->
                    if (holder.surface.isValid && videoAspectRatio == null) {
                        Log.d(TAG_CS, "Camera connected but no preview, providing surface")
                        onSurfaceHolderAvailable(holder)
                        viewModel.setSurface(holder.surface)
                    }
                }
            }
            else -> {
                Log.d(TAG_CS, "Camera state: ${cameraConnectionState::class.simpleName}")
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            Column(
                modifier = Modifier,
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Motor control slider (appears above FAB when connected)
                MotorControlSlider(
                    connectionState = motorConnectionState,
                    currentPosition = motorPosition,
                    onPositionChange = { position ->
                        // When user manually controls motor, it will automatically disable autofocus
                        viewModel.setMotorPosition(position, isManualControl = true)
                    }
                )

                // Expandable FAB menu when autofocus is available
                if (isAutofocusAvailable) {
                    ExpandableAutofocusFab(
                        isExpanded = isFabExpanded,
                        onExpandChange = { isFabExpanded = it },
                        currentMode = autofocusMode,
                        onModeChange = { mode ->
                            viewModel.settingsManager.setAutofocusMode(mode.name)
                            viewModel.settingsManager.setAutofocusEnabled(mode != FocusMode.MANUAL)
                        },
                        onSettingsClick = {
                            if (!isChangingResolution) {
                                Log.d(TAG_CS, "Settings clicked from expandable menu")
                                isNavigatingToSettings = true
                                isFabExpanded = false

                                // Before navigating, ensure we have the current surface saved
                                currentSurfaceHolder?.let { holder ->
                                    if (holder.surface.isValid) {
                                        viewModel.setSurface(holder.surface)
                                    }
                                }

                                onNavigateToSettings()

                                // Reset navigation flag after delay
                                coroutineScope.launch {
                                    delay(1000)
                                    isNavigatingToSettings = false
                                }
                            }
                        },
                        isChangingResolution = isChangingResolution
                    )
                } else {
                    // Regular settings FAB when autofocus is not available
                    FloatingActionButton(
                        onClick = {
                            if (!isChangingResolution) {
                                Log.d(TAG_CS, "Settings FAB clicked.")
                                isNavigatingToSettings = true

                                // Before navigating, ensure we have the current surface saved
                                currentSurfaceHolder?.let { holder ->
                                    if (holder.surface.isValid) {
                                        viewModel.setSurface(holder.surface)
                                    }
                                }

                                onNavigateToSettings()

                                // Reset navigation flag after delay
                                coroutineScope.launch {
                                    delay(1000)
                                    isNavigatingToSettings = false
                                }
                            }
                        },
                        containerColor = if (isChangingResolution) {
                            MaterialTheme.colorScheme.surfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = if (isChangingResolution) {
                                "Settings (Resolution changing...)"
                            } else {
                                "Open Settings"
                            }
                        )
                    }
                }
            }
        },
        floatingActionButtonPosition = FabPosition.End
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            // Camera preview
            CameraPreview(
                videoAspectRatio = if (isChangingResolution) null else videoAspectRatio,
                onSurfaceHolderAvailable = { holder ->
                    Log.d(TAG_CS, "Surface holder available")
                    currentSurfaceHolder = holder

                    // Don't provide surface if resolution is changing
                    if (!isChangingResolution) {
                        onSurfaceHolderAvailable(holder)

                        // Also update the view model directly
                        if (holder.surface.isValid) {
                            viewModel.setSurface(holder.surface)
                        }
                    }
                },
                onSurfaceHolderDestroyed = {
                    Log.d(TAG_CS, "Surface holder destroyed")
                    // Don't clear the reference if we're navigating to settings or changing resolution
                    if (!isNavigatingToSettings && !isChangingResolution) {
                        currentSurfaceHolder = null
                    }
                    onSurfaceHolderDestroyed()
                },
                modifier = Modifier.fillMaxSize()
            )

            // Show loading indicator when changing resolution
            if (isChangingResolution) {
                Card(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(
                            "Changing Resolution...",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "Camera will reconnect automatically",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Device status overlay (top-left corner)
            DeviceStatusIndicator(
                connectedDevices = coordinatorState.connectedDevices.size,
                activeDevices = coordinatorState.activeDevices.size,
                cameraActive = viewModel.isCameraActive(),
                motorActive = viewModel.isMotorActive(),
                realSenseActive = viewModel.isRealSenseActive(),
                totalBandwidthUsed = coordinatorState.totalBandwidthUsed,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
            )

            // Conflicts overlay - below DeviceStatusIndicator
            if (coordinatorState.conflicts.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 16.dp, top = 100.dp)
                        .width(200.dp)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                "Conflicts:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.Bold
                            )
                            coordinatorState.conflicts.forEach { conflict ->
                                Text(
                                    "• $conflict",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }

            // RealSense overlay with autofocus support (bottom-left corner)
            RealSenseOverlay(
                connectionState = realSenseConnectionState,
                centerDepth = realSenseCenterDepth,
                depthConfidence = realSenseDepthConfidence,
                depthBitmap = realSenseDepthBitmap,
                onMeasurementPositionChanged = { x, y ->
                    // This triggers tap-to-focus when enabled
                    viewModel.setRealSenseMeasurementPosition(x, y)
                },
                isAutofocusActive = isAutofocusActive,
                autofocusMode = autofocusMode.name,
                faceDetectionState = faceDetectionState,
                onFaceTap = { x, y ->
                    // Handle face selection tap in FACE_TRACKING mode
                    val width = colorBitmap?.width ?: 0
                    val height = colorBitmap?.height ?: 0
                    if (width > 0 && height > 0) {
                        viewModel.selectFaceForFocus(x, y, width, height)
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomStart)
            )
        }
    }
}

@Composable
private fun ExpandableAutofocusFab(
    isExpanded: Boolean,
    onExpandChange: (Boolean) -> Unit,
    currentMode: FocusMode,
    onModeChange: (FocusMode) -> Unit,
    onSettingsClick: () -> Unit,
    isChangingResolution: Boolean,
    modifier: Modifier = Modifier
) {
    val hapticFeedback = LocalHapticFeedback.current

    // Animation for rotation and scale
    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = spring(
            dampingRatio = 0.8f,
            stiffness = Spring.StiffnessLow
        ),
        label = "fab_rotation"
    )

    val fabScale by animateFloatAsState(
        targetValue = if (isExpanded) 0.9f else 1f,
        animationSpec = spring(
            dampingRatio = 0.7f,
            stiffness = Spring.StiffnessMedium
        ),
        label = "fab_scale"
    )

    // Fixed size box with unbounded content to allow overflow
    Box(
        modifier = modifier
            .size(56.dp)
            .wrapContentSize(unbounded = true),
        contentAlignment = Alignment.Center
    ) {
        // Expandable menu items - positioned to the left
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn(animationSpec = tween(200)) +
                    scaleIn(initialScale = 0.8f, animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(200)) +
                    scaleOut(targetScale = 0.8f, animationSpec = tween(200))
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .wrapContentWidth()
                    .offset(x = (-220).dp, y = 0.dp)
            ) {
                // Autofocus mode selector - Material You segmented button style
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                    tonalElevation = 2.dp,
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier
                            .height(48.dp)
                            .padding(2.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Manual Focus
                        FocusModeSegment(
                            icon = Icons.Default.PanTool,
                            label = "MF",
                            isSelected = currentMode == FocusMode.MANUAL,
                            position = SegmentPosition.START,
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                onModeChange(FocusMode.MANUAL)
                                onExpandChange(false)
                            }
                        )

                        // Single Auto
                        FocusModeSegment(
                            icon = Icons.Default.CenterFocusWeak,
                            label = "AF-S",
                            isSelected = currentMode == FocusMode.SINGLE_AUTO,
                            position = SegmentPosition.MIDDLE,
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                onModeChange(FocusMode.SINGLE_AUTO)
                                onExpandChange(false)
                            }
                        )

                        // Continuous Auto
                        FocusModeSegment(
                            icon = Icons.Default.CenterFocusStrong,
                            label = "AF-C",
                            isSelected = currentMode == FocusMode.CONTINUOUS_AUTO,
                            position = SegmentPosition.MIDDLE,
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                onModeChange(FocusMode.CONTINUOUS_AUTO)
                                onExpandChange(false)
                            }
                        )

                        // Face Tracking
                        FocusModeSegment(
                            icon = Icons.Default.Face,
                            label = "AF-F",
                            isSelected = currentMode == FocusMode.FACE_TRACKING,
                            position = SegmentPosition.END,
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                onModeChange(FocusMode.FACE_TRACKING)
                                onExpandChange(false)
                            }
                        )
                    }
                }

                // Divider
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(32.dp)
                        .background(
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                )

                // Settings button
                FilledTonalIconButton(
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSettingsClick()
                    },
                    modifier = Modifier.size(48.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // Main FAB - always centered
        FloatingActionButton(
            onClick = {
                if (!isChangingResolution) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    onExpandChange(!isExpanded)
                }
            },
            containerColor = when {
                isChangingResolution -> MaterialTheme.colorScheme.surfaceVariant
                isExpanded -> MaterialTheme.colorScheme.secondaryContainer
                currentMode != FocusMode.MANUAL -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.primaryContainer
            },
            modifier = Modifier.scale(fabScale)
        ) {
            Icon(
                imageVector = if (isExpanded) Icons.Default.Close else Icons.Default.Tune,
                contentDescription = if (isExpanded) "Close Menu" else "Autofocus Menu",
                modifier = Modifier.rotate(rotation)
            )
        }
    }
}

private enum class SegmentPosition { START, MIDDLE, END }

@Composable
private fun FocusModeSegment(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    position: SegmentPosition,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }

    // Animated background for selection
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            Color.Transparent
        },
        animationSpec = tween(200),
        label = "segment_bg"
    )

    val contentColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(200),
        label = "segment_color"
    )

    Surface(
        onClick = onClick,
        modifier = modifier
            .height(44.dp)
            .width(72.dp),
        shape = when (position) {
            SegmentPosition.START -> RoundedCornerShape(
                topStart = 26.dp,
                bottomStart = 26.dp,
                topEnd = 4.dp,
                bottomEnd = 4.dp
            )
            SegmentPosition.END -> RoundedCornerShape(
                topStart = 4.dp,
                bottomStart = 4.dp,
                topEnd = 26.dp,
                bottomEnd = 26.dp
            )
            SegmentPosition.MIDDLE -> RoundedCornerShape(4.dp)
        },
        color = backgroundColor,
        interactionSource = interactionSource
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = contentColor
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = contentColor
            )
        }
    }
}
