package com.selkacraft.alice.ui.screens.motordiscovery

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.selkacraft.alice.comm.core.ConnectionState
import com.selkacraft.alice.comm.motor.MotorProtocol
import com.selkacraft.alice.ui.screens.settings.components.LogDisplay
import com.selkacraft.alice.util.CameraViewModel
import com.selkacraft.alice.util.SettingsManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Discovery state for the binary search process
 */
sealed class DiscoveryState {
    object Idle : DiscoveryState()
    object Scanning : DiscoveryState()
    data class WaitingForConfirmation(val currentAddress: Int) : DiscoveryState()
    data class Found(val address: Int) : DiscoveryState()
    object NotFound : DiscoveryState()
    data class Error(val message: String) : DiscoveryState()
}

@Composable
fun MotorDiscoveryScreen(
    navController: NavController,
    viewModel: CameraViewModel
) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    val scope = rememberCoroutineScope()

    val motorConnectionState by viewModel.motorConnectionState.collectAsState()
    val logMessages by viewModel.logMessages.collectAsState()
    val savedAddress by settingsManager.motorDestinationAddress.collectAsState()
    val isDiscovered by settingsManager.motorDestinationDiscovered.collectAsState()

    // Binary search state
    var discoveryState by remember { mutableStateOf<DiscoveryState>(DiscoveryState.Idle) }
    var searchLow by remember { mutableIntStateOf(0x0000) }
    var searchHigh by remember { mutableIntStateOf(0xFFFF) }
    var currentTestAddress by remember { mutableIntStateOf(0x8000) }
    var searchIteration by remember { mutableIntStateOf(0) }
    val maxIterations = 16 // log2(65536) = 16

    // Calculate progress
    val progress by animateFloatAsState(
        targetValue = searchIteration.toFloat() / maxIterations.toFloat(),
        label = "search_progress"
    )

    // Function to start/reset the search
    fun startSearch() {
        searchLow = 0x0000
        searchHigh = 0xFFFF
        currentTestAddress = 0x8000
        searchIteration = 0
        discoveryState = DiscoveryState.Scanning
    }

    // Function to test an address
    fun testAddress(address: Int, onComplete: () -> Unit) {
        scope.launch {
            viewModel.scanMotorAddress(address)
            delay(500) // Wait for motor response
            onComplete()
        }
    }

    // Function to handle user response (Yes = motor moved)
    fun handleUserResponse(motorMoved: Boolean) {
        if (motorMoved) {
            // Motor responded - narrow down to lower half (including current)
            searchHigh = currentTestAddress
        } else {
            // Motor didn't respond - narrow down to upper half
            searchLow = currentTestAddress + 1
        }

        searchIteration++

        // Check if we've found the address
        if (searchLow >= searchHigh || searchIteration >= maxIterations) {
            // Found it!
            val foundAddress = if (motorMoved) currentTestAddress else searchHigh
            settingsManager.setMotorDestinationAddress(foundAddress)
            settingsManager.setMotorDestinationDiscovered(true)
            // Send the destination to the firmware
            viewModel.setMotorDestination(foundAddress)
            discoveryState = DiscoveryState.Found(foundAddress)
        } else {
            // Continue searching
            currentTestAddress = (searchLow + searchHigh) / 2
            discoveryState = DiscoveryState.Scanning
        }
    }

    // Trigger scan when in scanning state
    LaunchedEffect(discoveryState) {
        if (discoveryState is DiscoveryState.Scanning) {
            testAddress(currentTestAddress) {
                discoveryState = DiscoveryState.WaitingForConfirmation(currentTestAddress)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Main Content Panel (55%)
            Column(
                modifier = Modifier
                    .weight(0.55f)
                    .fillMaxHeight()
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Motor Discovery",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Find your focus motor's address using binary search",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider()

                // Content
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Connection Status Card
                    ConnectionStatusCard(motorConnectionState)

                    // Current Address Card
                    CurrentAddressCard(
                        savedAddress = savedAddress,
                        isDiscovered = isDiscovered,
                        onClear = {
                            settingsManager.setMotorDestinationAddress(0xFFFF)
                            settingsManager.setMotorDestinationDiscovered(false)
                        }
                    )

                    // Discovery Card
                    DiscoveryCard(
                        state = discoveryState,
                        searchLow = searchLow,
                        searchHigh = searchHigh,
                        currentAddress = currentTestAddress,
                        iteration = searchIteration,
                        maxIterations = maxIterations,
                        progress = progress,
                        isMotorConnected = motorConnectionState is ConnectionState.Connected ||
                                motorConnectionState is ConnectionState.Active,
                        onStart = { startSearch() },
                        onYes = { handleUserResponse(true) },
                        onNo = { handleUserResponse(false) },
                        onReset = {
                            discoveryState = DiscoveryState.Idle
                            searchIteration = 0
                        },
                        onApply = { address ->
                            settingsManager.setMotorDestinationAddress(address)
                            settingsManager.setMotorDestinationDiscovered(true)
                            viewModel.setMotorDestination(address)
                            navController.popBackStack()
                        }
                    )

                    // Instructions Card
                    InstructionsCard()
                }
            }

            // Log Panel (45%)
            Column(
                modifier = Modifier
                    .weight(0.45f)
                    .fillMaxHeight()
            ) {
                LogDisplay(
                    logMessages = logMessages,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun ConnectionStatusCard(connectionState: ConnectionState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (connectionState) {
                is ConnectionState.Connected, is ConnectionState.Active ->
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                is ConnectionState.Error ->
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                else ->
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = when (connectionState) {
                        is ConnectionState.Connected, is ConnectionState.Active -> Icons.Default.CheckCircle
                        is ConnectionState.Error -> Icons.Default.Error
                        else -> Icons.Default.Warning
                    },
                    contentDescription = null,
                    tint = when (connectionState) {
                        is ConnectionState.Connected, is ConnectionState.Active ->
                            MaterialTheme.colorScheme.primary
                        is ConnectionState.Error ->
                            MaterialTheme.colorScheme.error
                        else ->
                            MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Column {
                    Text(
                        text = "Motor Controller",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = when (connectionState) {
                            is ConnectionState.Connected, is ConnectionState.Active -> "Connected"
                            is ConnectionState.Connecting -> "Connecting..."
                            is ConnectionState.Error -> "Error"
                            else -> "Not Connected"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun CurrentAddressCard(
    savedAddress: Int,
    isDiscovered: Boolean,
    onClear: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isDiscovered)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Current Address",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (isDiscovered) {
                    OutlinedButton(
                        onClick = onClear,
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Clear", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "0x${MotorProtocol.formatAddressHex(savedAddress)}",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
                if (isDiscovered) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "DISCOVERED",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                } else {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "DEFAULT",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoveryCard(
    state: DiscoveryState,
    searchLow: Int,
    searchHigh: Int,
    currentAddress: Int,
    iteration: Int,
    maxIterations: Int,
    progress: Float,
    isMotorConnected: Boolean,
    onStart: () -> Unit,
    onYes: () -> Unit,
    onNo: () -> Unit,
    onReset: () -> Unit,
    onApply: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Binary Search Discovery",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            HorizontalDivider()

            when (state) {
                is DiscoveryState.Idle -> {
                    IdleContent(isMotorConnected, onStart)
                }

                is DiscoveryState.Scanning -> {
                    ScanningContent(currentAddress, iteration, maxIterations, progress)
                }

                is DiscoveryState.WaitingForConfirmation -> {
                    ConfirmationContent(
                        currentAddress = state.currentAddress,
                        searchLow = searchLow,
                        searchHigh = searchHigh,
                        iteration = iteration,
                        maxIterations = maxIterations,
                        progress = progress,
                        onYes = onYes,
                        onNo = onNo,
                        onCancel = onReset
                    )
                }

                is DiscoveryState.Found -> {
                    FoundContent(state.address, onApply, onReset)
                }

                is DiscoveryState.NotFound -> {
                    NotFoundContent(onReset)
                }

                is DiscoveryState.Error -> {
                    ErrorContent(state.message, onReset)
                }
            }
        }
    }
}

@Composable
private fun IdleContent(isMotorConnected: Boolean, onStart: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "This tool will help you find your motor's IEEE 802.15.4 address using binary search. " +
                    "The process takes approximately 16 steps.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth(),
            enabled = isMotorConnected
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Start Discovery")
        }

        if (!isMotorConnected) {
            Text(
                text = "Connect motor controller to start discovery",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun ScanningContent(
    currentAddress: Int,
    iteration: Int,
    maxIterations: Int,
    progress: Float
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CircularProgressIndicator(modifier = Modifier.size(48.dp))

        Text(
            text = "Testing Address",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "0x${MotorProtocol.formatAddressHex(currentAddress)}",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.primary
        )

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
        )

        Text(
            text = "Step $iteration of $maxIterations",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ConfirmationContent(
    currentAddress: Int,
    searchLow: Int,
    searchHigh: Int,
    iteration: Int,
    maxIterations: Int,
    progress: Float,
    onYes: () -> Unit,
    onNo: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Testing Address",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "0x${MotorProtocol.formatAddressHex(currentAddress)}",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.primary
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Did the motor move?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Watch your focus motor and confirm if it made any movement",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onYes,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Yes, it moved")
            }
            OutlinedButton(
                onClick = onNo,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Cancel, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("No movement")
            }
        }

        // Progress info
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Step $iteration of $maxIterations",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Range: 0x${MotorProtocol.formatAddressHex(searchLow)} - 0x${MotorProtocol.formatAddressHex(searchHigh)}",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cancel Discovery")
        }
    }
}

@Composable
private fun FoundContent(
    address: Int,
    onApply: (Int) -> Unit,
    onReset: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp)
        )

        Text(
            text = "Motor Found!",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Text(
            text = "0x${MotorProtocol.formatAddressHex(address)}",
            style = MaterialTheme.typography.headlineLarge.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.primary
        )

        Text(
            text = "This address has been saved and will be used for motor communication.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Button(
            onClick = { onApply(address) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Apply & Return to Settings")
        }

        OutlinedButton(
            onClick = onReset,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Search Again")
        }
    }
}

@Composable
private fun NotFoundContent(onReset: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(64.dp)
        )

        Text(
            text = "Motor Not Found",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error
        )

        Text(
            text = "Could not locate a motor address. Please ensure your motor is powered on and within range.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Button(
            onClick = onReset,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Try Again")
        }
    }
}

@Composable
private fun ErrorContent(message: String, onReset: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(64.dp)
        )

        Text(
            text = "Error",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error
        )

        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Button(
            onClick = onReset,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Try Again")
        }
    }
}

@Composable
private fun InstructionsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "How It Works",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "1. Ensure your motor is powered on and the lens is attached",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "2. Connect the nRF52840 dongle to your device",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "3. Start the discovery and watch your motor for any movement",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "4. Answer honestly - the binary search will find the correct address in ~16 steps",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "5. Once found, the address will be saved and used automatically",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
