package com.selkacraft.alice

import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.selkacraft.alice.ui.screens.calibration.CalibrationScreen
import com.selkacraft.alice.ui.screens.camera.CameraScreen
import com.selkacraft.alice.ui.screens.motordiscovery.MotorDiscoveryScreen
import com.selkacraft.alice.ui.screens.settings.SettingsScreen
import com.selkacraft.alice.ui.screens.welcome.WelcomeScreen
import com.selkacraft.alice.ui.theme.AliceTheme
import com.selkacraft.alice.util.CameraViewModel
import com.selkacraft.alice.util.SettingsManager
import android.Manifest

class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var settingsManager: SettingsManager

    // Permission state observable from Compose
    private val _hasAllPermissions = mutableStateOf(false)
    private val hasAllPermissions: Boolean get() = _hasAllPermissions.value

    // Track if permissions were explicitly denied (only set after permission dialog result)
    private val _permissionWasDenied = mutableStateOf(false)


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "onCreate: Activity creating.")

        settingsManager = SettingsManager(this)

        // Set initial orientation based on onboarding status
        updateOrientation()

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val windowInsetsController = WindowInsetsControllerCompat(window, window.decorView)
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        Log.d(TAG, "onCreate: Fullscreen mode configured.")

        // Check initial permission state
        _hasAllPermissions.value = checkPermissionsGranted()

        setContent {
            AliceTheme {
                Log.d(TAG, "setContent: AliceTheme applied.")
                val viewModel: CameraViewModel = viewModel()
                val lifecycleOwner = LocalLifecycleOwner.current
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                // Track permission state - observe the activity's state directly
                val permissionsGranted by remember { _hasAllPermissions }

                // Determine start destination
                val isFirstLaunch = settingsManager.isFirstLaunch()
                val startDestination = if (isFirstLaunch) "welcome" else "camera"

                Log.d(TAG, "setContent: ViewModel, LifecycleOwner, NavController initialized. First launch: $isFirstLaunch")

                // Update orientation based on current route
                LaunchedEffect(currentRoute) {
                    if (currentRoute == "welcome") {
                        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    } else if (currentRoute != null) {
                        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    }
                }

                DisposableEffect(lifecycleOwner, currentRoute) {
                    Log.d(TAG, "DisposableEffect: Setting up LifecycleEventObserver.")
                    val observer = LifecycleEventObserver { _, event ->
                        Log.d(TAG, "LifecycleEvent: $event")
                        // Only register camera when not on welcome screen
                        val shouldManageCamera = currentRoute != "welcome" && currentRoute != null
                        when (event) {
                            Lifecycle.Event.ON_CREATE -> {
                                // Register once when activity is created (if not on welcome)
                                if (shouldManageCamera) {
                                    Log.i(TAG, "Lifecycle.Event.ON_CREATE: Initial registration.")
                                    viewModel.register()
                                }
                            }
                            Lifecycle.Event.ON_RESUME -> {
                                // Ensure registered when resuming (but won't duplicate)
                                if (shouldManageCamera) {
                                    Log.i(TAG, "Lifecycle.Event.ON_RESUME: Ensuring camera is registered.")
                                    viewModel.register()
                                }
                            }
                            Lifecycle.Event.ON_PAUSE -> {
                                // Only pause, don't unregister when navigating within app
                                Log.i(TAG, "Lifecycle.Event.ON_PAUSE: Pausing camera.")
                                viewModel.onPause()
                            }
                            Lifecycle.Event.ON_DESTROY -> {
                                // Only unregister when activity is being destroyed
                                Log.i(TAG, "Lifecycle.Event.ON_DESTROY: Unregistering camera.")
                                viewModel.unregister()
                            }
                            else -> { /* Other events like ON_START, ON_STOP */ }
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        Log.d(TAG, "DisposableEffect: onDispose: Removing lifecycle observer.")
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Log.d(TAG, "NavHost: Setting up navigation graph.")
                    NavHost(
                        navController = navController,
                        startDestination = startDestination,
                    ) {
                        val slideAnimSpec = tween<IntOffset>(durationMillis = 350, easing = FastOutSlowInEasing)
                        val fadeSpecShort = tween<Float>(durationMillis = 150, easing = LinearOutSlowInEasing)
                        val fadeSpecLong = tween<Float>(durationMillis = 300, delayMillis = 50, easing = LinearOutSlowInEasing)
                        val scaleSpec = tween<Float>(durationMillis = 350, easing = FastOutSlowInEasing)

                        val cameraExitTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition?) = {
                            slideOutHorizontally(targetOffsetX = { -it / 5 }, animationSpec = slideAnimSpec) +
                                    fadeOut(animationSpec = fadeSpecShort) +
                                    scaleOut(animationSpec = scaleSpec, targetScale = 0.95f)
                        }

                        val settingsEnterTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition?) = {
                            slideInHorizontally(initialOffsetX = { it / 3 }, animationSpec = slideAnimSpec) +
                                    fadeIn(animationSpec = fadeSpecLong) +
                                    scaleIn(animationSpec = scaleSpec, initialScale = 0.95f)
                        }

                        val settingsPopExitTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition?) = {
                            slideOutHorizontally(targetOffsetX = { it / 3 }, animationSpec = slideAnimSpec) +
                                    fadeOut(animationSpec = fadeSpecShort) +
                                    scaleOut(animationSpec = scaleSpec, targetScale = 0.95f)
                        }

                        val cameraPopEnterTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition?) = {
                            slideInHorizontally(initialOffsetX = { -it / 5 }, animationSpec = slideAnimSpec) +
                                    fadeIn(animationSpec = fadeSpecLong) +
                                    scaleIn(animationSpec = scaleSpec, initialScale = 0.95f)
                        }

                        // Welcome/Onboarding screen
                        composable(
                            "welcome",
                            enterTransition = { fadeIn(animationSpec = fadeSpecLong) },
                            exitTransition = { fadeOut(animationSpec = fadeSpecShort) }
                        ) {
                            val permissionWasDenied by remember { _permissionWasDenied }
                            WelcomeScreen(
                                onPermissionsGranted = { /* State is observed directly */ },
                                onGetStarted = {
                                    // Mark onboarding as complete
                                    settingsManager.setHasCompletedOnboarding(true)
                                    // Navigate to camera screen
                                    navController.navigate("camera") {
                                        popUpTo("welcome") { inclusive = true }
                                    }
                                    // Register camera when entering main screen
                                    viewModel.register()
                                },
                                onRequestPermissions = {
                                    requestPermissions()
                                },
                                hasAllPermissions = permissionsGranted,
                                permissionWasDenied = permissionWasDenied
                            )
                        }

                        composable(
                            "camera",
                            enterTransition = cameraPopEnterTransition,
                            exitTransition = cameraExitTransition,
                            popEnterTransition = cameraPopEnterTransition,
                            popExitTransition = cameraExitTransition
                        ) {
                            Log.i(TAG, "Navigating to 'camera' screen.")
                            CameraScreenWrapper(
                                viewModel = viewModel,
                                navController = navController
                            )
                        }

                        composable(
                            "settings",
                            enterTransition = settingsEnterTransition,
                            exitTransition = settingsPopExitTransition,
                            popEnterTransition = settingsEnterTransition,
                            popExitTransition = settingsPopExitTransition
                        ) {
                            Log.i(TAG, "Navigating to 'settings' screen.")
                            SettingsScreen(
                                navController = navController,
                                viewModel = viewModel
                            )
                        }

                        composable(
                            "calibration",
                            enterTransition = settingsEnterTransition,
                            exitTransition = settingsPopExitTransition,
                            popEnterTransition = settingsEnterTransition,
                            popExitTransition = settingsPopExitTransition
                        ) {
                            Log.i(TAG, "Navigating to 'calibration' screen.")
                            CalibrationScreen(
                                navController = navController,
                                cameraViewModel = viewModel
                            )
                        }

                        composable(
                            "motor_discovery",
                            enterTransition = settingsEnterTransition,
                            exitTransition = settingsPopExitTransition,
                            popEnterTransition = settingsEnterTransition,
                            popExitTransition = settingsPopExitTransition
                        ) {
                            Log.i(TAG, "Navigating to 'motor_discovery' screen.")
                            MotorDiscoveryScreen(
                                navController = navController,
                                viewModel = viewModel
                            )
                        }
                    }
                }
            }
        }

        permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { _ ->
            // Always check actual system permission status after dialog closes
            val cameraGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            val micGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

            _hasAllPermissions.value = cameraGranted && micGranted

            if (!cameraGranted || !micGranted) {
                _permissionWasDenied.value = true
                Toast.makeText(this, "Camera and microphone permissions are required for Alice to function.", Toast.LENGTH_LONG).show()
            } else {
                _permissionWasDenied.value = false
                Log.i(TAG, "All required permissions granted")
            }
        }

        // Only auto-request permissions if not first launch (welcome screen will handle it)
        if (!settingsManager.isFirstLaunch()) {
            checkAndRequestPermissions()
        }
    }

    private fun updateOrientation() {
        requestedOrientation = if (settingsManager.isFirstLaunch()) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    private fun checkPermissionsGranted(): Boolean {
        val cameraGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val micGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        return cameraGranted && micGranted
    }

    private fun requestPermissions() {
        val missingPermissions = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            missingPermissions.add(Manifest.permission.CAMERA)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            missingPermissions.add(Manifest.permission.RECORD_AUDIO)
        }

        if (missingPermissions.isNotEmpty()) {
            // Reset denied state when making a new request
            _permissionWasDenied.value = false
            permissionLauncher.launch(missingPermissions.toTypedArray())
        } else {
            // Already have all permissions
            _hasAllPermissions.value = true
            _permissionWasDenied.value = false
        }
    }

    private fun checkAndRequestPermissions() {
        val missingPermissions = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            missingPermissions.add(Manifest.permission.CAMERA)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            missingPermissions.add(Manifest.permission.RECORD_AUDIO)
        }

        if (missingPermissions.isNotEmpty()) {
            permissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    override fun onStart() {
        super.onStart()
        Log.i(TAG, "onStart: Activity starting.")
        // Force the screen to stay on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        Log.d(TAG, "onCreate: Screen wake lock flag set.")

        // Set screen brightness to maximum
        val layoutParams = window.attributes
        layoutParams.screenBrightness = 1.0f // 1.0f is maximum brightness
        window.attributes = layoutParams
        Log.d(TAG, "onCreate: Screen brightness set to maximum.")
    }

    override fun onResume() {
        super.onResume()
        Log.i(TAG, "onResume: Activity resuming.")
    }

    override fun onPause() {
        super.onPause()
        Log.i(TAG, "onPause: Activity pausing.")
    }

    override fun onStop() {
        super.onStop()
        Log.i(TAG, "onStop: Activity stopping.")
        // Restore the system's default brightness setting
        val layoutParams = window.attributes
        // A value of -1f tells the system to use the preferred screen brightness.
        layoutParams.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window.attributes = layoutParams
        Log.d(TAG, "onStop: Screen brightness reset to system default.")

        // The FLAG_KEEP_SCREEN_ON is automatically cleared when the activity is not visible,
        // but clearing it explicitly is also fine.
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "onDestroy: Activity destroying.")
    }
}

@Composable
fun CameraScreenWrapper(
    viewModel: CameraViewModel,
    navController: NavController
) {
    val videoAspectRatio by viewModel.videoAspectRatio.collectAsState()
    Log.d("CameraScreenWrapper", "Composing with videoAspectRatio: $videoAspectRatio")

    // Track surface lifecycle
    DisposableEffect(Unit) {
        Log.d("CameraScreenWrapper", "CameraScreenWrapper created")
        onDispose {
            Log.d("CameraScreenWrapper", "CameraScreenWrapper disposed")
        }
    }

    CameraScreen(
        videoAspectRatio = videoAspectRatio,
        onNavigateToSettings = {
            Log.d("CameraScreenWrapper", "onNavigateToSettings: Triggered.")
            navController.navigate("settings")
        },
        onSurfaceHolderAvailable = { holder ->
            Log.d("CameraScreenWrapper", "SurfaceHolder available: $holder, Surface: ${holder.surface}, isValid: ${holder.surface.isValid}")
            if (holder.surface.isValid) {
                viewModel.setSurface(holder.surface)
            } else {
                Log.w("CameraScreenWrapper", "SurfaceHolder available but surface is invalid. Setting null surface in VM.")
                viewModel.setSurface(null)
            }
        },
        onSurfaceHolderDestroyed = {
            Log.d("CameraScreenWrapper", "SurfaceHolder destroyed. Setting null surface in VM.")
            viewModel.setSurface(null)
        },
        viewModel = viewModel
    )
}