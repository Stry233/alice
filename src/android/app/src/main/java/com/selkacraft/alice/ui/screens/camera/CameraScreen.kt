package com.selkacraft.alice.ui.screens.camera

import android.util.Log
import android.view.SurfaceHolder
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.selkacraft.alice.comm.autofocus.FocusMode
import com.selkacraft.alice.comm.core.ConnectionState
import com.selkacraft.alice.ui.screens.camera.components.CameraPreview
import com.selkacraft.alice.ui.screens.camera.components.DeviceStatusIndicator
import com.selkacraft.alice.ui.screens.camera.components.MotorControlSlider
import com.selkacraft.alice.ui.screens.camera.components.RealSenseOverlay
import com.selkacraft.alice.util.CameraViewModel
import com.selkacraft.alice.util.CameraViewModel.DataSource
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
    val motorPosition by viewModel.effectiveMotorPosition.collectAsState()
    val coordinatorState by viewModel.coordinatorState.collectAsState()
    val cameraState by viewModel.cameraState.collectAsState()
    val cameraConnectionState by viewModel.cameraConnectionState.collectAsState()
    val isChangingResolution by viewModel.isChangingResolution.collectAsState()

    // Effective (unified) states — local hardware or remote desktop
    val effectiveMotorConnected by viewModel.effectiveMotorConnected.collectAsState()
    val effectiveRealSenseConnected by viewModel.effectiveRealSenseConnected.collectAsState()
    val effectiveDataSource by viewModel.effectiveDataSource.collectAsState()

    // RealSense states (unified)
    val realSenseConnectionState by viewModel.realSenseConnectionState.collectAsState()
    val realSenseCenterDepth by viewModel.effectiveDepth.collectAsState()
    val realSenseDepthConfidence by viewModel.effectiveDepthConfidence.collectAsState()
    val realSenseDepthBitmap by viewModel.effectiveDepthBitmap.collectAsState()

    // Autofocus states
    val autofocusEnabled by viewModel.effectiveAutofocusEnabled.collectAsState()
    val autofocusMode by viewModel.autofocusMode.collectAsState()
    val autofocusMapping by viewModel.autofocusMapping.collectAsState()
    val isAutofocusActive by viewModel.isAutofocusActive.collectAsState()

    // Face detection states
    val faceDetectionState by viewModel.faceDetectionState.collectAsState()
    val colorBitmap by viewModel.effectiveColorBitmap.collectAsState()

    // Effective camera frame (null = local Surface active, Bitmap = remote stream)
    val effectiveCameraFrame by viewModel.effectiveCameraFrame.collectAsState()

    // Check if autofocus is available (mapping loaded + both devices connected locally or remotely)
    val isAutofocusAvailable = remember(autofocusMapping, effectiveMotorConnected, effectiveRealSenseConnected) {
        autofocusMapping != null && effectiveMotorConnected && effectiveRealSenseConnected
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
                // Motor control slider (appears above FAB when connected — local or remote)
                MotorControlSlider(
                    connectionState = motorConnectionState,
                    currentPosition = motorPosition,
                    onPositionChange = { position ->
                        // When user manually controls motor, it will automatically disable autofocus
                        viewModel.setMotorPosition(position, isManualControl = true)
                    },
                    isEffectivelyConnected = effectiveMotorConnected
                )

                // Single FAB that adapts behavior based on AF state
                val showAfFab = isAutofocusAvailable && autofocusEnabled
                ExpandableAutofocusFab(
                    isExpanded = isFabExpanded && showAfFab,
                    onExpandChange = { expanded ->
                        if (showAfFab) {
                            isFabExpanded = expanded
                        } else if (!isChangingResolution) {
                            // When AF not available, FAB acts as settings button
                            Log.d(TAG_CS, "Settings FAB clicked.")
                            isNavigatingToSettings = true
                            currentSurfaceHolder?.let { holder ->
                                if (holder.surface.isValid) {
                                    viewModel.setSurface(holder.surface)
                                }
                            }
                            onNavigateToSettings()
                            coroutineScope.launch {
                                delay(1000)
                                isNavigatingToSettings = false
                            }
                        }
                    },
                    currentMode = if (showAfFab) autofocusMode else FocusMode.MANUAL,
                    onModeChange = { mode ->
                        viewModel.settingsManager.setAutofocusMode(mode.name)
                        viewModel.settingsManager.setAutofocusEnabled(mode != FocusMode.MANUAL)
                    },
                    onSettingsClick = {
                        if (!isChangingResolution) {
                            Log.d(TAG_CS, "Settings clicked from expandable menu")
                            isNavigatingToSettings = true
                            isFabExpanded = false
                            currentSurfaceHolder?.let { holder ->
                                if (holder.surface.isValid) {
                                    viewModel.setSurface(holder.surface)
                                }
                            }
                            onNavigateToSettings()
                            coroutineScope.launch {
                                delay(1000)
                                isNavigatingToSettings = false
                            }
                        }
                    },
                    isChangingResolution = isChangingResolution,
                    isAfAvailable = showAfFab
                )
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

            // Remote camera frame (shown when local camera isn't active)
            effectiveCameraFrame?.let { frame ->
                var remoteScale by remember { mutableFloatStateOf(1f) }
                var remoteOffsetX by remember { mutableFloatStateOf(0f) }
                var remoteOffsetY by remember { mutableFloatStateOf(0f) }

                Image(
                    bitmap = frame.asImageBitmap(),
                    contentDescription = "Camera Preview",
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = remoteScale
                            scaleY = remoteScale
                            translationX = remoteOffsetX
                            translationY = remoteOffsetY
                        }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                remoteScale = (remoteScale * zoom).coerceIn(1f, 5f)
                                if (remoteScale > 1f) {
                                    remoteOffsetX += pan.x
                                    remoteOffsetY += pan.y
                                } else {
                                    remoteOffsetX = 0f
                                    remoteOffsetY = 0f
                                }
                            }
                        },
                    contentScale = ContentScale.Fit
                )
            }

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
                cameraActive = viewModel.isCameraActive() || effectiveCameraFrame != null,
                motorActive = effectiveMotorConnected,
                realSenseActive = effectiveRealSenseConnected,
                totalBandwidthUsed = coordinatorState.totalBandwidthUsed,
                dataSource = effectiveDataSource,
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
                depthBitmap = colorBitmap ?: realSenseDepthBitmap,
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
                isEffectivelyConnected = effectiveRealSenseConnected,
                remoteMeasureX = viewModel.remoteMeasureX.collectAsState().value,
                remoteMeasureY = viewModel.remoteMeasureY.collectAsState().value,
                onVisibilityChanged = { visible ->
                    // Only pause depth/color when the depth panel is hidden.
                    // Capture card stream stays on — it's the main camera preview.
                    viewModel.setRemoteStreamEnabled(
                        color = visible,
                        depth = visible,
                        capture = true
                    )
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
    isAfAvailable: Boolean = true,
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

    // FAB with menu panel rendered in a Popup (bypasses all parent clipping)
    Box(modifier = modifier) {
        // Menu panel in a Popup — floats above the layout hierarchy, no parent clipping
        val menuSlide by animateDpAsState(
            targetValue = if (isExpanded) 0.dp else 48.dp,
            animationSpec = spring(
                dampingRatio = 0.85f,
                stiffness = Spring.StiffnessMediumLow
            ),
            label = "menu_slide"
        )
        val menuAlpha by animateFloatAsState(
            targetValue = if (isExpanded) 1f else 0f,
            animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
            label = "menu_alpha"
        )
        val density = LocalDensity.current

        if (isExpanded || menuAlpha > 0.01f) {
            Popup(
                alignment = Alignment.CenterEnd,
                offset = IntOffset(
                    with(density) { ((-80).dp + menuSlide).roundToPx() },
                    0
                ),
                properties = PopupProperties(clippingEnabled = false)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .wrapContentSize()
                        .graphicsLayer { alpha = menuAlpha }
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
        }

        // Main FAB - always centered, with animated color transitions
        val targetColor = when {
            isChangingResolution -> MaterialTheme.colorScheme.surfaceVariant
            isExpanded -> MaterialTheme.colorScheme.secondaryContainer
            !isAfAvailable -> MaterialTheme.colorScheme.primaryContainer
            currentMode != FocusMode.MANUAL -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.primaryContainer
        }
        val animatedColor by animateColorAsState(
            targetValue = targetColor,
            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
            label = "fab_color"
        )

        val targetIcon = when {
            isExpanded -> Icons.Default.Close
            !isAfAvailable -> Icons.Default.Settings
            else -> Icons.Default.Tune
        }

        FloatingActionButton(
            onClick = {
                if (!isChangingResolution) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    onExpandChange(!isExpanded)
                }
            },
            containerColor = animatedColor,
            modifier = Modifier.scale(fabScale)
        ) {
            Crossfade(
                targetState = targetIcon,
                animationSpec = tween(durationMillis = 200),
                label = "fab_icon"
            ) { icon ->
                Icon(
                    imageVector = icon,
                    contentDescription = when {
                        isExpanded -> "Close Menu"
                        !isAfAvailable -> "Open Settings"
                        else -> "Autofocus Menu"
                    },
                    modifier = Modifier.rotate(rotation)
                )
            }
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
