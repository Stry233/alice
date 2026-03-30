package com.selkacraft.alice.util

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages all application settings with reactive StateFlow-based API.
 * Uses SharedPreferences for persistence with asynchronous state updates.
 */
class SettingsManager(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("alice_settings", Context.MODE_PRIVATE)

    // ====================
    // Camera Settings
    // ====================

    private val _cameraResolution = MutableStateFlow(
        prefs.getString(KEY_CAMERA_RESOLUTION, DEFAULT_CAMERA_RESOLUTION) ?: DEFAULT_CAMERA_RESOLUTION
    )
    val cameraResolution: StateFlow<String> = _cameraResolution.asStateFlow()

    private val _videoFormat = MutableStateFlow(
        prefs.getString(KEY_VIDEO_FORMAT, DEFAULT_VIDEO_FORMAT) ?: DEFAULT_VIDEO_FORMAT
    )
    val videoFormat: StateFlow<String> = _videoFormat.asStateFlow()

    // ====================
    // Autofocus Settings
    // ====================

    private val _autofocusEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_AUTOFOCUS_ENABLED, DEFAULT_AUTOFOCUS_ENABLED)
    )
    val autofocusEnabled: StateFlow<Boolean> = _autofocusEnabled.asStateFlow()

    private val _autofocusMode = MutableStateFlow(
        prefs.getString(KEY_AUTOFOCUS_MODE, DEFAULT_AUTOFOCUS_MODE) ?: DEFAULT_AUTOFOCUS_MODE
    )
    val autofocusMode: StateFlow<String> = _autofocusMode.asStateFlow()

    private val _autofocusConfidenceThreshold = MutableStateFlow(
        prefs.getFloat(KEY_AUTOFOCUS_CONFIDENCE_THRESHOLD, DEFAULT_AUTOFOCUS_CONFIDENCE_THRESHOLD)
    )
    val autofocusConfidenceThreshold: StateFlow<Float> = _autofocusConfidenceThreshold.asStateFlow()

    private val _autofocusSmoothing = MutableStateFlow(
        prefs.getBoolean(KEY_AUTOFOCUS_SMOOTHING, DEFAULT_AUTOFOCUS_SMOOTHING)
    )
    val autofocusSmoothing: StateFlow<Boolean> = _autofocusSmoothing.asStateFlow()

    private val _autofocusResponseSpeed = MutableStateFlow(
        prefs.getInt(KEY_AUTOFOCUS_RESPONSE_SPEED, DEFAULT_AUTOFOCUS_RESPONSE_SPEED)
    )
    val autofocusResponseSpeed: StateFlow<Int> = _autofocusResponseSpeed.asStateFlow()

    private val _autofocusTapToFocus = MutableStateFlow(
        prefs.getBoolean(KEY_AUTOFOCUS_TAP_TO_FOCUS, DEFAULT_AUTOFOCUS_TAP_TO_FOCUS)
    )
    val autofocusTapToFocus: StateFlow<Boolean> = _autofocusTapToFocus.asStateFlow()

    private val _autofocusFocusHoldTime = MutableStateFlow(
        prefs.getInt(KEY_AUTOFOCUS_FOCUS_HOLD_TIME, DEFAULT_AUTOFOCUS_FOCUS_HOLD_TIME)
    )
    val autofocusFocusHoldTime: StateFlow<Int> = _autofocusFocusHoldTime.asStateFlow()

    // ====================
    // Motor Settings
    // ====================

    private val _motorSpeed = MutableStateFlow(
        prefs.getInt(KEY_MOTOR_SPEED, DEFAULT_MOTOR_SPEED)
    )
    val motorSpeed: StateFlow<Int> = _motorSpeed.asStateFlow()

    private val _motorAcceleration = MutableStateFlow(
        prefs.getInt(KEY_MOTOR_ACCELERATION, DEFAULT_MOTOR_ACCELERATION)
    )
    val motorAcceleration: StateFlow<Int> = _motorAcceleration.asStateFlow()

    private val _motorSmoothing = MutableStateFlow(
        prefs.getBoolean(KEY_MOTOR_SMOOTHING, DEFAULT_MOTOR_SMOOTHING)
    )
    val motorSmoothing: StateFlow<Boolean> = _motorSmoothing.asStateFlow()

    private val _motorReverseDirection = MutableStateFlow(
        prefs.getBoolean(KEY_MOTOR_REVERSE_DIRECTION, DEFAULT_MOTOR_REVERSE_DIRECTION)
    )
    val motorReverseDirection: StateFlow<Boolean> = _motorReverseDirection.asStateFlow()

    private val _motorCalibrationOffset = MutableStateFlow(
        prefs.getInt(KEY_MOTOR_CALIBRATION_OFFSET, DEFAULT_MOTOR_CALIBRATION_OFFSET)
    )
    val motorCalibrationOffset: StateFlow<Int> = _motorCalibrationOffset.asStateFlow()

    private val _motorDestinationAddress = MutableStateFlow(
        prefs.getInt(KEY_MOTOR_DESTINATION_ADDRESS, DEFAULT_MOTOR_DESTINATION_ADDRESS)
    )
    val motorDestinationAddress: StateFlow<Int> = _motorDestinationAddress.asStateFlow()

    private val _motorDestinationDiscovered = MutableStateFlow(
        prefs.getBoolean(KEY_MOTOR_DESTINATION_DISCOVERED, DEFAULT_MOTOR_DESTINATION_DISCOVERED)
    )
    val motorDestinationDiscovered: StateFlow<Boolean> = _motorDestinationDiscovered.asStateFlow()

    // ====================
    // Depth Settings
    // ====================

    private val _depthConfidenceThreshold = MutableStateFlow(
        prefs.getFloat(KEY_DEPTH_CONFIDENCE_THRESHOLD, DEFAULT_DEPTH_CONFIDENCE_THRESHOLD)
    )
    val depthConfidenceThreshold: StateFlow<Float> = _depthConfidenceThreshold.asStateFlow()

    private val _depthFilterSmoothing = MutableStateFlow(
        prefs.getFloat(KEY_DEPTH_FILTER_SMOOTHING, DEFAULT_DEPTH_FILTER_SMOOTHING)
    )
    val depthFilterSmoothing: StateFlow<Float> = _depthFilterSmoothing.asStateFlow()

    private val _depthMinDistance = MutableStateFlow(
        prefs.getInt(KEY_DEPTH_MIN_DISTANCE, DEFAULT_DEPTH_MIN_DISTANCE)
    )
    val depthMinDistance: StateFlow<Int> = _depthMinDistance.asStateFlow()

    private val _depthMaxDistance = MutableStateFlow(
        prefs.getInt(KEY_DEPTH_MAX_DISTANCE, DEFAULT_DEPTH_MAX_DISTANCE)
    )
    val depthMaxDistance: StateFlow<Int> = _depthMaxDistance.asStateFlow()

    // ====================
    // Onboarding Settings
    // ====================

    private val _hasCompletedOnboarding = MutableStateFlow(
        prefs.getBoolean(KEY_HAS_COMPLETED_ONBOARDING, DEFAULT_HAS_COMPLETED_ONBOARDING)
    )
    val hasCompletedOnboarding: StateFlow<Boolean> = _hasCompletedOnboarding.asStateFlow()

    // ====================
    // System Settings
    // ====================

    private val _devicePriority = MutableStateFlow(
        prefs.getString(KEY_DEVICE_PRIORITY, DEFAULT_DEVICE_PRIORITY) ?: DEFAULT_DEVICE_PRIORITY
    )
    val devicePriority: StateFlow<String> = _devicePriority.asStateFlow()

    private val _usbBandwidthLimit = MutableStateFlow(
        prefs.getInt(KEY_USB_BANDWIDTH_LIMIT, DEFAULT_USB_BANDWIDTH_LIMIT)
    )
    val usbBandwidthLimit: StateFlow<Int> = _usbBandwidthLimit.asStateFlow()

    private val _autoReconnect = MutableStateFlow(
        prefs.getBoolean(KEY_AUTO_RECONNECT, DEFAULT_AUTO_RECONNECT)
    )
    val autoReconnect: StateFlow<Boolean> = _autoReconnect.asStateFlow()

    private val _reconnectDelay = MutableStateFlow(
        prefs.getInt(KEY_RECONNECT_DELAY, DEFAULT_RECONNECT_DELAY)
    )
    val reconnectDelay: StateFlow<Int> = _reconnectDelay.asStateFlow()

    private val _logVerbosity = MutableStateFlow(
        prefs.getString(KEY_LOG_VERBOSITY, DEFAULT_LOG_VERBOSITY) ?: DEFAULT_LOG_VERBOSITY
    )
    val logVerbosity: StateFlow<String> = _logVerbosity.asStateFlow()

    // ====================
    // Setter Methods
    // ====================

    // Camera
    fun setCameraResolution(value: String) = updateString(KEY_CAMERA_RESOLUTION, value, _cameraResolution)
    fun setVideoFormat(value: String) = updateString(KEY_VIDEO_FORMAT, value, _videoFormat)

    // Autofocus
    fun setAutofocusEnabled(value: Boolean) = updateBoolean(KEY_AUTOFOCUS_ENABLED, value, _autofocusEnabled)
    fun setAutofocusMode(value: String) = updateString(KEY_AUTOFOCUS_MODE, value, _autofocusMode)
    fun setAutofocusConfidenceThreshold(value: Float) = updateFloat(KEY_AUTOFOCUS_CONFIDENCE_THRESHOLD, value, _autofocusConfidenceThreshold)
    fun setAutofocusSmoothing(value: Boolean) = updateBoolean(KEY_AUTOFOCUS_SMOOTHING, value, _autofocusSmoothing)
    fun setAutofocusResponseSpeed(value: Int) = updateInt(KEY_AUTOFOCUS_RESPONSE_SPEED, value, _autofocusResponseSpeed)
    fun setAutofocusTapToFocus(value: Boolean) = updateBoolean(KEY_AUTOFOCUS_TAP_TO_FOCUS, value, _autofocusTapToFocus)
    fun setAutofocusFocusHoldTime(value: Int) = updateInt(KEY_AUTOFOCUS_FOCUS_HOLD_TIME, value, _autofocusFocusHoldTime)

    // Additional autofocus methods used by CameraViewModel
    fun setAutofocusMappingName(value: String) = updateString(KEY_AUTOFOCUS_MAPPING_NAME, value, null)
    fun setAutofocusMappingPreset(value: String) = updateString(KEY_AUTOFOCUS_MAPPING_PRESET, value, null)

    // Motor
    fun setMotorSpeed(value: Int) = updateInt(KEY_MOTOR_SPEED, value, _motorSpeed)
    fun setMotorAcceleration(value: Int) = updateInt(KEY_MOTOR_ACCELERATION, value, _motorAcceleration)
    fun setMotorSmoothing(value: Boolean) = updateBoolean(KEY_MOTOR_SMOOTHING, value, _motorSmoothing)
    fun setMotorReverseDirection(value: Boolean) = updateBoolean(KEY_MOTOR_REVERSE_DIRECTION, value, _motorReverseDirection)
    fun setMotorCalibrationOffset(value: Int) = updateInt(KEY_MOTOR_CALIBRATION_OFFSET, value, _motorCalibrationOffset)
    fun setMotorDestinationAddress(value: Int) = updateInt(KEY_MOTOR_DESTINATION_ADDRESS, value, _motorDestinationAddress)
    fun setMotorDestinationDiscovered(value: Boolean) = updateBoolean(KEY_MOTOR_DESTINATION_DISCOVERED, value, _motorDestinationDiscovered)

    // Depth
    fun setDepthConfidenceThreshold(value: Float) = updateFloat(KEY_DEPTH_CONFIDENCE_THRESHOLD, value, _depthConfidenceThreshold)
    fun setDepthFilterSmoothing(value: Float) = updateFloat(KEY_DEPTH_FILTER_SMOOTHING, value, _depthFilterSmoothing)
    fun setDepthMinDistance(value: Int) = updateInt(KEY_DEPTH_MIN_DISTANCE, value, _depthMinDistance)
    fun setDepthMaxDistance(value: Int) = updateInt(KEY_DEPTH_MAX_DISTANCE, value, _depthMaxDistance)

    // System
    fun setDevicePriority(value: String) = updateString(KEY_DEVICE_PRIORITY, value, _devicePriority)
    fun setUsbBandwidthLimit(value: Int) = updateInt(KEY_USB_BANDWIDTH_LIMIT, value, _usbBandwidthLimit)
    fun setAutoReconnect(value: Boolean) = updateBoolean(KEY_AUTO_RECONNECT, value, _autoReconnect)
    fun setReconnectDelay(value: Int) = updateInt(KEY_RECONNECT_DELAY, value, _reconnectDelay)
    fun setLogVerbosity(value: String) = updateString(KEY_LOG_VERBOSITY, value, _logVerbosity)

    // Onboarding
    fun setHasCompletedOnboarding(value: Boolean) = updateBoolean(KEY_HAS_COMPLETED_ONBOARDING, value, _hasCompletedOnboarding)
    fun setFirstLaunchCompleted() = setHasCompletedOnboarding(true)
    fun isFirstLaunch(): Boolean = !_hasCompletedOnboarding.value

    // ====================
    // Getter Methods (Synchronous - for backward compatibility)
    // ====================

    fun getCameraResolution(): String = _cameraResolution.value
    fun getAutoReconnect(): Boolean = _autoReconnect.value
    fun getReconnectDelay(): Int = _reconnectDelay.value
    fun getMotorCalibrationOffset(): Int = _motorCalibrationOffset.value
    fun getMotorReverseDirection(): Boolean = _motorReverseDirection.value
    fun getMotorDestinationAddress(): Int = _motorDestinationAddress.value
    fun getMotorDestinationDiscovered(): Boolean = _motorDestinationDiscovered.value

    // ====================
    // Reset Methods
    // ====================

    fun resetCameraSettings() {
        setCameraResolution(DEFAULT_CAMERA_RESOLUTION)
        setVideoFormat(DEFAULT_VIDEO_FORMAT)
    }

    fun resetAutofocusSettings() {
        setAutofocusEnabled(DEFAULT_AUTOFOCUS_ENABLED)
        setAutofocusMode(DEFAULT_AUTOFOCUS_MODE)
        setAutofocusConfidenceThreshold(DEFAULT_AUTOFOCUS_CONFIDENCE_THRESHOLD)
        setAutofocusSmoothing(DEFAULT_AUTOFOCUS_SMOOTHING)
        setAutofocusResponseSpeed(DEFAULT_AUTOFOCUS_RESPONSE_SPEED)
        setAutofocusTapToFocus(DEFAULT_AUTOFOCUS_TAP_TO_FOCUS)
        setAutofocusFocusHoldTime(DEFAULT_AUTOFOCUS_FOCUS_HOLD_TIME)
    }

    fun resetMotorSettings() {
        setMotorSpeed(DEFAULT_MOTOR_SPEED)
        setMotorAcceleration(DEFAULT_MOTOR_ACCELERATION)
        setMotorSmoothing(DEFAULT_MOTOR_SMOOTHING)
        setMotorReverseDirection(DEFAULT_MOTOR_REVERSE_DIRECTION)
        setMotorCalibrationOffset(DEFAULT_MOTOR_CALIBRATION_OFFSET)
        setMotorDestinationAddress(DEFAULT_MOTOR_DESTINATION_ADDRESS)
        setMotorDestinationDiscovered(DEFAULT_MOTOR_DESTINATION_DISCOVERED)
    }

    fun resetDepthSettings() {
        setDepthConfidenceThreshold(DEFAULT_DEPTH_CONFIDENCE_THRESHOLD)
        setDepthFilterSmoothing(DEFAULT_DEPTH_FILTER_SMOOTHING)
        setDepthMinDistance(DEFAULT_DEPTH_MIN_DISTANCE)
        setDepthMaxDistance(DEFAULT_DEPTH_MAX_DISTANCE)
    }

    fun resetSystemSettings() {
        setDevicePriority(DEFAULT_DEVICE_PRIORITY)
        setUsbBandwidthLimit(DEFAULT_USB_BANDWIDTH_LIMIT)
        setAutoReconnect(DEFAULT_AUTO_RECONNECT)
        setReconnectDelay(DEFAULT_RECONNECT_DELAY)
        setLogVerbosity(DEFAULT_LOG_VERBOSITY)
    }

    fun resetAllSettings() {
        resetCameraSettings()
        resetAutofocusSettings()
        resetMotorSettings()
        resetDepthSettings()
        resetSystemSettings()
    }

    // ====================
    // Private Helper Methods
    // ====================

    private fun updateString(key: String, value: String, stateFlow: MutableStateFlow<String>?) {
        prefs.edit().putString(key, value).apply()
        stateFlow?.value = value
    }

    private fun updateInt(key: String, value: Int, stateFlow: MutableStateFlow<Int>?) {
        prefs.edit().putInt(key, value).apply()
        stateFlow?.value = value
    }

    private fun updateFloat(key: String, value: Float, stateFlow: MutableStateFlow<Float>?) {
        prefs.edit().putFloat(key, value).apply()
        stateFlow?.value = value
    }

    private fun updateBoolean(key: String, value: Boolean, stateFlow: MutableStateFlow<Boolean>?) {
        prefs.edit().putBoolean(key, value).apply()
        stateFlow?.value = value
    }

    companion object {
        // Keys
        private const val KEY_CAMERA_RESOLUTION = "camera_resolution"
        private const val KEY_VIDEO_FORMAT = "video_format"

        private const val KEY_AUTOFOCUS_ENABLED = "autofocus_enabled"
        private const val KEY_AUTOFOCUS_MODE = "autofocus_mode"
        private const val KEY_AUTOFOCUS_CONFIDENCE_THRESHOLD = "autofocus_confidence_threshold"
        private const val KEY_AUTOFOCUS_SMOOTHING = "autofocus_smoothing"
        private const val KEY_AUTOFOCUS_RESPONSE_SPEED = "autofocus_response_speed"
        private const val KEY_AUTOFOCUS_TAP_TO_FOCUS = "autofocus_tap_to_focus"
        private const val KEY_AUTOFOCUS_FOCUS_HOLD_TIME = "autofocus_focus_hold_time"
        private const val KEY_AUTOFOCUS_MAPPING_NAME = "autofocus_mapping_name"
        private const val KEY_AUTOFOCUS_MAPPING_PRESET = "autofocus_mapping_preset"

        private const val KEY_MOTOR_SPEED = "motor_speed"
        private const val KEY_MOTOR_ACCELERATION = "motor_acceleration"
        private const val KEY_MOTOR_SMOOTHING = "motor_smoothing"
        private const val KEY_MOTOR_REVERSE_DIRECTION = "motor_reverse_direction"
        private const val KEY_MOTOR_CALIBRATION_OFFSET = "motor_calibration_offset"
        private const val KEY_MOTOR_DESTINATION_ADDRESS = "motor_destination_address"
        private const val KEY_MOTOR_DESTINATION_DISCOVERED = "motor_destination_discovered"

        private const val KEY_DEPTH_CONFIDENCE_THRESHOLD = "depth_confidence_threshold"
        private const val KEY_DEPTH_FILTER_SMOOTHING = "depth_filter_smoothing"
        private const val KEY_DEPTH_MIN_DISTANCE = "depth_min_distance"
        private const val KEY_DEPTH_MAX_DISTANCE = "depth_max_distance"

        private const val KEY_DEVICE_PRIORITY = "device_priority"
        private const val KEY_USB_BANDWIDTH_LIMIT = "usb_bandwidth_limit"
        private const val KEY_AUTO_RECONNECT = "auto_reconnect"
        private const val KEY_RECONNECT_DELAY = "reconnect_delay"
        private const val KEY_LOG_VERBOSITY = "log_verbosity"
        private const val KEY_HAS_COMPLETED_ONBOARDING = "has_completed_onboarding"

        // Default values
        private const val DEFAULT_CAMERA_RESOLUTION = "1920x1080"
        private const val DEFAULT_VIDEO_FORMAT = "MJPEG"

        private const val DEFAULT_AUTOFOCUS_ENABLED = false
        private const val DEFAULT_AUTOFOCUS_MODE = "MANUAL"
        private const val DEFAULT_AUTOFOCUS_CONFIDENCE_THRESHOLD = 0.7f
        private const val DEFAULT_AUTOFOCUS_SMOOTHING = true
        private const val DEFAULT_AUTOFOCUS_RESPONSE_SPEED = 50
        private const val DEFAULT_AUTOFOCUS_TAP_TO_FOCUS = true
        private const val DEFAULT_AUTOFOCUS_FOCUS_HOLD_TIME = 500

        private const val DEFAULT_MOTOR_SPEED = 50
        private const val DEFAULT_MOTOR_ACCELERATION = 50
        private const val DEFAULT_MOTOR_SMOOTHING = true
        private const val DEFAULT_MOTOR_REVERSE_DIRECTION = false
        private const val DEFAULT_MOTOR_CALIBRATION_OFFSET = 0
        private const val DEFAULT_MOTOR_DESTINATION_ADDRESS = 0xFFFF  // Broadcast address
        private const val DEFAULT_MOTOR_DESTINATION_DISCOVERED = false

        private const val DEFAULT_DEPTH_CONFIDENCE_THRESHOLD = 0.7f
        private const val DEFAULT_DEPTH_FILTER_SMOOTHING = 0.3f
        private const val DEFAULT_DEPTH_MIN_DISTANCE = 200
        private const val DEFAULT_DEPTH_MAX_DISTANCE = 5000

        private const val DEFAULT_DEVICE_PRIORITY = "Balanced"
        private const val DEFAULT_USB_BANDWIDTH_LIMIT = 3200
        private const val DEFAULT_AUTO_RECONNECT = true
        private const val DEFAULT_RECONNECT_DELAY = 2000
        private const val DEFAULT_LOG_VERBOSITY = "INFO"
        private const val DEFAULT_HAS_COMPLETED_ONBOARDING = false
    }
}
