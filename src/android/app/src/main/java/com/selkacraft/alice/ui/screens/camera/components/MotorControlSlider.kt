package com.selkacraft.alice.ui.screens.camera.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.selkacraft.alice.comm.core.ConnectionState
import kotlin.math.abs

@Composable
fun MotorControlSlider(
    connectionState: ConnectionState,
    currentPosition: Int,
    onPositionChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var sliderPosition by remember { mutableFloatStateOf(currentPosition.toFloat()) }
    var isUserDragging by remember { mutableStateOf(false) }
    var lastHapticPosition by remember { mutableFloatStateOf(currentPosition.toFloat()) }

    val hapticFeedback = LocalHapticFeedback.current

    LaunchedEffect(currentPosition) {
        if (!isUserDragging) {
            sliderPosition = currentPosition.toFloat()
        }
    }

    val isVisible = when (connectionState) {
        is ConnectionState.Connected,
        is ConnectionState.Active,
        is ConnectionState.Connecting,
        is ConnectionState.AwaitingPermission -> true
        else -> false
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInHorizontally(
            initialOffsetX = { it },
            animationSpec = tween(300, easing = FastOutSlowInEasing)
        ) + fadeIn(animationSpec = tween(300)),
        exit = slideOutHorizontally(
            targetOffsetX = { it },
            animationSpec = tween(300, easing = FastOutSlowInEasing)
        ) + fadeOut(animationSpec = tween(300)),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .width(56.dp)
                .height(280.dp),
            shape = RoundedCornerShape(15.dp),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 2.dp,
            shadowElevation = 2.dp
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Icon(
                        imageVector = Icons.Default.Landscape,
                        contentDescription = "Far focus",
                        modifier = Modifier
                            .size(20.dp)
                            .alpha(0.6f),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Icon(
                        imageVector = Icons.Default.LocalFlorist,
                        contentDescription = "Near focus",
                        modifier = Modifier
                            .size(20.dp)
                            .alpha(0.6f),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Slider(
                        value = sliderPosition,
                        onValueChange = { newValue ->
                            isUserDragging = true
                            sliderPosition = newValue

                            if (abs(newValue - lastHapticPosition) > 50f) {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                lastHapticPosition = newValue
                            }

                            if (connectionState is ConnectionState.Connected ||
                                connectionState is ConnectionState.Active) {
                                onPositionChange(newValue.toInt())
                            }
                        },
                        onValueChangeFinished = {
                            isUserDragging = false
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)

                            if (connectionState is ConnectionState.Connected ||
                                connectionState is ConnectionState.Active) {
                                onPositionChange(sliderPosition.toInt())
                            }
                        },
                        valueRange = 0f..4095f,
                        enabled = connectionState is ConnectionState.Connected ||
                                connectionState is ConnectionState.Active,
                        modifier = Modifier
                            .graphicsLayer {
                                rotationZ = 270f
                            }
                            .layout { measurable, constraints ->
                                val placeable = measurable.measure(
                                    constraints.copy(
                                        maxWidth = constraints.maxHeight,
                                        maxHeight = constraints.maxWidth
                                    )
                                )
                                layout(placeable.height, placeable.width) {
                                    placeable.place(
                                        x = -(placeable.width / 2 - placeable.height / 2),
                                        y = -(placeable.height / 2 - placeable.width / 2)
                                    )
                                }
                            }
                            .fillMaxHeight(),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledThumbColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledActiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledInactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 8.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Surface(
                        modifier = Modifier.size(4.dp),
                        shape = RoundedCornerShape(2.dp),
                        color = when (connectionState) {
                            is ConnectionState.Connected,
                            is ConnectionState.Active -> MaterialTheme.colorScheme.primary
                            is ConnectionState.Connecting -> MaterialTheme.colorScheme.tertiary
                            is ConnectionState.Error -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.outline
                        }
                    ) {}
                }
            }
        }
    }
}
