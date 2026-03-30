package com.selkacraft.alice.ui.screens.welcome.pages

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.selkacraft.alice.R
import com.selkacraft.alice.ui.screens.welcome.animations.AliceAcronymAnimation
import com.selkacraft.alice.ui.screens.welcome.components.FeatureItem

// Original app icon background color
private val AppIconBackgroundColor = Color(0xFF17222F)

@Composable
fun WelcomePage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .padding(bottom = 130.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // App logo
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(AppIconBackgroundColor),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.logo_internal),
                contentDescription = "Alice Logo",
                modifier = Modifier.size(48.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Animated ALICE acronym
        AliceAcronymAnimation()

        Spacer(modifier = Modifier.height(28.dp))

        // Feature highlights
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            FeatureItem(
                icon = Icons.Outlined.Videocam,
                title = "External Monitor",
                description = "View your camera feed on this device"
            )
            FeatureItem(
                icon = Icons.Outlined.CenterFocusStrong,
                title = "Smart Autofocus",
                description = "Auto-adjust focus with depth sensing"
            )
            FeatureItem(
                icon = Icons.Outlined.Settings,
                title = "Lens Control",
                description = "Direct control over focus ring"
            )
        }
    }
}
