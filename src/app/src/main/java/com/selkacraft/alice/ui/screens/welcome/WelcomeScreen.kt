package com.selkacraft.alice.ui.screens.welcome

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.selkacraft.alice.ui.screens.welcome.components.BottomNavigationBar
import com.selkacraft.alice.ui.screens.welcome.pages.PermissionsPage
import com.selkacraft.alice.ui.screens.welcome.pages.RotateDevicePage
import com.selkacraft.alice.ui.screens.welcome.pages.WelcomePage
import kotlinx.coroutines.launch

/**
 * Welcome/onboarding screen with a 3-page horizontal pager.
 *
 * Pages:
 * 1. WelcomePage - App introduction with animated ALICE acronym
 * 2. PermissionsPage - Camera and microphone permission requests
 * 3. RotateDevicePage - Device rotation instruction to landscape
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WelcomeScreen(
    onPermissionsGranted: () -> Unit,
    onGetStarted: () -> Unit,
    onRequestPermissions: () -> Unit,
    hasAllPermissions: Boolean,
    permissionWasDenied: Boolean = false,
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
        ) {
            // Pager content
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> WelcomePage()
                    1 -> PermissionsPage(
                        permissionsGranted = hasAllPermissions,
                        permissionsDenied = permissionWasDenied,
                        onRequestPermissions = onRequestPermissions
                    )
                    2 -> RotateDevicePage(
                        permissionsGranted = hasAllPermissions,
                        onComplete = onGetStarted
                    )
                }
            }

            // Bottom navigation bar
            BottomNavigationBar(
                currentPage = pagerState.currentPage,
                pageCount = 3,
                canProceedFromCurrent = pagerState.currentPage != 1 || hasAllPermissions,
                isLastPage = pagerState.currentPage == 2,
                permissionsGranted = hasAllPermissions,
                onNext = {
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                },
                onGetStarted = onGetStarted,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}
