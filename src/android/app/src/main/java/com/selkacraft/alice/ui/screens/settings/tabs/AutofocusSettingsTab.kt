package com.selkacraft.alice.ui.screens.settings.tabs

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.selkacraft.alice.comm.autofocus.AutofocusMapping
import com.selkacraft.alice.comm.autofocus.MappingPreset
import com.selkacraft.alice.comm.autofocus.ValidationResult
import com.selkacraft.alice.ui.screens.settings.components.DropdownSettingItem
import com.selkacraft.alice.ui.screens.settings.components.SettingsCard
import com.selkacraft.alice.ui.screens.settings.components.SliderSettingItem
import com.selkacraft.alice.ui.screens.settings.components.SwitchSettingItem
import com.selkacraft.alice.util.CameraViewModel
import com.selkacraft.alice.util.SettingsManager
import kotlinx.coroutines.launch

@Composable
fun AutofocusSettingsTab(
    settingsManager: SettingsManager,
    viewModel: CameraViewModel,
    navController: NavController
) {
    val autofocusEnabled by settingsManager.autofocusEnabled.collectAsState()
    val autofocusMode by settingsManager.autofocusMode.collectAsState()
    val autofocusSmoothing by settingsManager.autofocusSmoothing.collectAsState()
    val autofocusResponseSpeed by settingsManager.autofocusResponseSpeed.collectAsState()
    val autofocusConfidenceThreshold by settingsManager.autofocusConfidenceThreshold.collectAsState()
    val autofocusFocusHoldTime by settingsManager.autofocusFocusHoldTime.collectAsState()

    val currentMapping by viewModel.autofocusMapping.collectAsState()
    val validationResult by viewModel.autofocusValidation.collectAsState()
    val focusStats by viewModel.autofocusStats.collectAsState()

    val scope = rememberCoroutineScope()

    var isLoadingMapping by remember { mutableStateOf(false) }
    var loadingError by remember { mutableStateOf<String?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            isLoadingMapping = true
            loadingError = null
            scope.launch {
                viewModel.loadAutofocusMapping(it).fold(
                    onSuccess = {
                        isLoadingMapping = false
                        loadingError = null
                    },
                    onFailure = { error ->
                        isLoadingMapping = false
                        loadingError = error.message ?: "Failed to load mapping file"
                    }
                )
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Loading indicator
        AnimatedVisibility(visible = isLoadingMapping) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Loading mapping file...",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        // Error message
        AnimatedVisibility(visible = loadingError != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = "Error",
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Failed to load mapping",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = loadingError ?: "Unknown error",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = { loadingError = null },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        ) {
                            Text("Dismiss")
                        }
                    }
                }
            }
        }

        // Mapping Status Card
        MappingConfigurationCard(
            currentMapping = currentMapping,
            validationResult = validationResult,
            isLoadingMapping = isLoadingMapping,
            onClearMapping = {
                viewModel.clearAutofocusMapping()
                loadingError = null
            },
            onLoadMapping = {
                loadingError = null
                filePickerLauncher.launch("application/json")
            },
            onLoadPreset = { preset ->
                scope.launch {
                    viewModel.loadAutofocusPreset(preset)
                }
            }
        )

        // Calibrator Navigation Card
        CalibratorNavigationCard(navController = navController)

        // Autofocus Control
        SettingsCard(title = "Autofocus Control") {
            SwitchSettingItem(
                label = "Enable Autofocus",
                checked = autofocusEnabled && currentMapping != null,
                onCheckedChange = { settingsManager.setAutofocusEnabled(it) },
                enabled = currentMapping != null
            )

            if (!autofocusEnabled && currentMapping == null) {
                Text(
                    text = "Load a mapping file first",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            AnimatedVisibility(visible = autofocusEnabled && currentMapping != null) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val focusModeDisplayNames = mapOf(
                        "MANUAL" to "MANUAL",
                        "SINGLE_AUTO" to "AF-S",
                        "CONTINUOUS_AUTO" to "AF-C",
                        "FACE_TRACKING" to "AF-F"
                    )
                    val focusModeInternalNames = focusModeDisplayNames.entries.associate { (k, v) -> v to k }

                    DropdownSettingItem(
                        label = "Focus Mode",
                        value = focusModeDisplayNames[autofocusMode] ?: autofocusMode,
                        options = listOf("MANUAL", "AF-S", "AF-C", "AF-F"),
                        onValueChange = { displayName ->
                            val internalName = focusModeInternalNames[displayName] ?: displayName
                            settingsManager.setAutofocusMode(internalName)
                        }
                    )

                    SwitchSettingItem(
                        label = "Focus Smoothing",
                        checked = autofocusSmoothing,
                        onCheckedChange = { settingsManager.setAutofocusSmoothing(it) }
                    )

                    SliderSettingItem(
                        label = "Response Speed",
                        value = autofocusResponseSpeed.toFloat(),
                        onValueChange = { settingsManager.setAutofocusResponseSpeed(it.toInt()) },
                        valueRange = 10f..100f,
                        steps = 90,
                        displayFormat = { "${it.toInt()}%" }
                    )

                    SliderSettingItem(
                        label = "Confidence Threshold",
                        value = autofocusConfidenceThreshold,
                        onValueChange = { settingsManager.setAutofocusConfidenceThreshold(it) },
                        valueRange = 0.3f..1f,
                        steps = 70,
                        displayFormat = { "${(it * 100).toInt()}%" }
                    )

                    SliderSettingItem(
                        label = "Focus Hold Time",
                        value = autofocusFocusHoldTime.toFloat(),
                        onValueChange = { settingsManager.setAutofocusFocusHoldTime(it.toInt()) },
                        valueRange = 100f..2000f,
                        steps = 19,
                        displayFormat = { "${it.toInt()} ms" }
                    )
                }
            }
        }

        // Focus Statistics
        AnimatedVisibility(visible = autofocusEnabled && currentMapping != null && focusStats.totalFocusOperations > 0) {
            SettingsCard(title = "Focus Statistics") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Total Operations:", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "${focusStats.totalFocusOperations}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Last Depth:", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "${String.format("%.2f", focusStats.lastDepth)}m",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Last Position:", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "${focusStats.lastPosition}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Average Depth:", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "${String.format("%.2f", focusStats.averageDepth)}m",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Reset Button
        OutlinedButton(
            onClick = {
                settingsManager.resetAutofocusSettings()
                viewModel.clearAutofocusMapping()
                loadingError = null
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Refresh, contentDescription = "Reset")
            Spacer(Modifier.width(8.dp))
            Text("Reset Autofocus Settings")
        }
    }
}

@Composable
private fun MappingConfigurationCard(
    currentMapping: AutofocusMapping?,
    validationResult: ValidationResult?,
    isLoadingMapping: Boolean,
    onClearMapping: () -> Unit,
    onLoadMapping: () -> Unit,
    onLoadPreset: (MappingPreset) -> Unit
) {
    SettingsCard(
        title = "Mapping Configuration",
        containerColor = if (currentMapping != null)
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        else
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
    ) {
        if (currentMapping != null) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentMapping.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (currentMapping.description.isNotEmpty()) {
                            Text(
                                text = currentMapping.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Mapping loaded",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${currentMapping.mappingPoints.size} points",
                        style = MaterialTheme.typography.labelSmall
                    )
                    val stats = validationResult?.statistics
                    if (stats != null) {
                        Text(
                            text = "Range: ${String.format("%.1f", stats.minDepth)}m - ${String.format("%.1f", stats.maxDepth)}m",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                validationResult?.warnings?.forEach { warning ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Warning",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = warning,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onClearMapping,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                        Spacer(Modifier.width(4.dp))
                        Text("Clear")
                    }
                    Button(
                        onClick = onLoadMapping,
                        modifier = Modifier.weight(1f),
                        enabled = !isLoadingMapping
                    ) {
                        Icon(Icons.Default.FileOpen, contentDescription = "Load")
                        Spacer(Modifier.width(4.dp))
                        Text("Replace")
                    }
                }
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FileOpen,
                    contentDescription = "No mapping",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = "No mapping file loaded",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    text = "Load a mapping file to enable autofocus",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = onLoadMapping,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoadingMapping
                ) {
                    Icon(Icons.Default.FileOpen, contentDescription = "Load")
                    Spacer(Modifier.width(8.dp))
                    Text("Load Mapping File")
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Info",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Place your .json mapping file in Downloads or Documents folder for easy access",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = "Or use a preset:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onLoadPreset(MappingPreset.LINEAR) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Linear", style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(
                        onClick = { onLoadPreset(MappingPreset.LOGARITHMIC) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Log", style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(
                        onClick = { onLoadPreset(MappingPreset.PORTRAIT) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Portrait", style = MaterialTheme.typography.labelSmall)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onLoadPreset(MappingPreset.LANDSCAPE) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Landscape", style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(
                        onClick = { onLoadPreset(MappingPreset.MACRO) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Macro", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun CalibratorNavigationCard(navController: NavController) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Straighten,
                contentDescription = "Calibrator",
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.tertiary
            )
            Text(
                text = "Create Custom Calibration",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Use the calibrator tool to generate custom autofocus mappings for your camera and lens setup",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = { navController.navigate("calibration") },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary
                )
            ) {
                Icon(Icons.Default.Straighten, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Open Calibrator")
            }
        }
    }
}
