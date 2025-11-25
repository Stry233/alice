package com.selkacraft.alice.ui.screens.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CenterFocusWeak
import androidx.compose.material.icons.filled.ControlCamera
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.selkacraft.alice.ui.screens.settings.components.LogDisplay
import com.selkacraft.alice.ui.screens.settings.tabs.AutofocusSettingsTab
import com.selkacraft.alice.ui.screens.settings.tabs.CameraSettingsTab
import com.selkacraft.alice.ui.screens.settings.tabs.DepthSettingsTab
import com.selkacraft.alice.ui.screens.settings.tabs.MotorSettingsTab
import com.selkacraft.alice.ui.screens.settings.tabs.StatusTab
import com.selkacraft.alice.ui.screens.settings.tabs.SystemSettingsTab
import com.selkacraft.alice.util.CameraViewModel
import com.selkacraft.alice.util.SettingsManager
import kotlinx.coroutines.launch

/**
 * Main settings screen with tabbed navigation.
 * Each tab content is defined in a separate file under the `tabs` package.
 */
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: CameraViewModel
) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    val logMessages by viewModel.logMessages.collectAsState()
    val coordinatorState by viewModel.coordinatorState.collectAsState()

    var selectedTab by remember { mutableStateOf(SettingsTab.AUTOFOCUS) }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Settings Panel (55% width)
            Column(
                modifier = Modifier
                    .weight(0.55f)
                    .fillMaxHeight()
            ) {
                // Header
                SettingsHeader(onBackClick = { navController.popBackStack() })

                // Tab Row
                SettingsTabRow(
                    selectedTab = selectedTab,
                    onTabSelected = { tab ->
                        if (selectedTab != tab) {
                            selectedTab = tab
                            scope.launch { scrollState.animateScrollTo(0) }
                        }
                    }
                )

                HorizontalDivider()

                // Tab Content
                SettingsTabContent(
                    selectedTab = selectedTab,
                    scrollState = scrollState,
                    settingsManager = settingsManager,
                    viewModel = viewModel,
                    navController = navController,
                    coordinatorState = coordinatorState
                )
            }

            // Log Panel (45% width)
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
private fun SettingsHeader(onBackClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        IconButton(onClick = onBackClick) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back to Camera",
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SettingsTabRow(
    selectedTab: SettingsTab,
    onTabSelected: (SettingsTab) -> Unit
) {
    ScrollableTabRow(
        selectedTabIndex = selectedTab.ordinal,
        modifier = Modifier.fillMaxWidth(),
        edgePadding = 8.dp,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary,
        indicator = { tabPositions ->
            TabRowDefaults.Indicator(
                modifier = Modifier
                    .tabIndicatorOffset(tabPositions[selectedTab.ordinal])
                    .height(3.dp)
                    .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)),
                color = MaterialTheme.colorScheme.primary
            )
        }
    ) {
        SettingsTab.entries.forEach { tab ->
            Tab(
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) },
                text = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.title,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = tab.title,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal
                            )
                        )
                    }
                },
                modifier = Modifier.height(48.dp)
            )
        }
    }
}

@Composable
private fun ColumnScope.SettingsTabContent(
    selectedTab: SettingsTab,
    scrollState: androidx.compose.foundation.ScrollState,
    settingsManager: SettingsManager,
    viewModel: CameraViewModel,
    navController: NavController,
    coordinatorState: com.selkacraft.alice.comm.UsbDeviceCoordinator.CoordinatorState
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
    ) {
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                val isMovingForward = targetState.ordinal > initialState.ordinal

                val enterTransition = slideInHorizontally(
                    initialOffsetX = { if (isMovingForward) it else -it },
                    animationSpec = tween(350, easing = FastOutSlowInEasing)
                ) + fadeIn(
                    animationSpec = tween(200, delayMillis = 100, easing = LinearOutSlowInEasing)
                )

                val exitTransition = slideOutHorizontally(
                    targetOffsetX = { if (isMovingForward) -it / 2 else it / 2 },
                    animationSpec = tween(350, easing = FastOutSlowInEasing)
                ) + fadeOut(
                    animationSpec = tween(150, easing = LinearOutSlowInEasing)
                ) + scaleOut(
                    targetScale = 0.95f,
                    animationSpec = tween(350, easing = FastOutSlowInEasing)
                )

                enterTransition togetherWith exitTransition
            },
            label = "tab_content_animation"
        ) { tab ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (tab) {
                    SettingsTab.AUTOFOCUS -> AutofocusSettingsTab(settingsManager, viewModel, navController)
                    SettingsTab.CAMERA -> CameraSettingsTab(settingsManager, viewModel)
                    SettingsTab.DEPTH -> DepthSettingsTab(settingsManager, viewModel)
                    SettingsTab.MOTOR -> MotorSettingsTab(settingsManager, viewModel, navController)
                    SettingsTab.SYSTEM -> SystemSettingsTab(settingsManager, viewModel)
                    SettingsTab.STATUS -> StatusTab(coordinatorState, viewModel)
                }
            }
        }
    }
}

/**
 * Settings tab definitions with icons and titles.
 */
enum class SettingsTab(val title: String, val icon: ImageVector) {
    AUTOFOCUS("Autofocus", Icons.Default.CenterFocusWeak),
    CAMERA("Camera", Icons.Default.CameraAlt),
    MOTOR("Motor", Icons.Default.ControlCamera),
    DEPTH("Depth", Icons.Default.Visibility),
    SYSTEM("System", Icons.Default.Settings),
    STATUS("Status", Icons.Default.Info)
}
