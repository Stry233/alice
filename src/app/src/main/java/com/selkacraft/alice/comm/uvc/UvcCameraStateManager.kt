package com.selkacraft.alice.comm.uvc

import android.util.Log
import android.view.Surface
import com.selkacraft.alice.comm.core.GenericStateManager
import com.selkacraft.alice.comm.core.DeviceState
import com.selkacraft.alice.comm.core.CameraState
import com.selkacraft.alice.comm.core.Resolution
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*

/**
 * Simplified UVC camera state manager using generic state management
 */
class UvcCameraStateManager(
    scope: CoroutineScope = CoroutineScope(kotlinx.coroutines.Dispatchers.Default)
) : GenericStateManager<UvcCameraStateData>(scope, "UvcCameraStateManager") {

    // Device profiling for optimization
    private val deviceProfiles = mutableMapOf<String, DeviceProfile>()
    private var connectionStartTime = 0L
    var lastSuccessfulDeviceKey: String? = null
        private set

    // Expose specific state properties as flows for compatibility
    val cameraAspectRatio: StateFlow<Float?> = deviceData.map {
        it?.aspectRatio
    }.stateIn(scope, SharingStarted.Eagerly, null)

    val isPreviewActive: StateFlow<Boolean> = deviceState.map {
        it is CameraState.PreviewActive || it is DeviceState.Active
    }.stateIn(scope, SharingStarted.Eagerly, false)

    val isClosing: StateFlow<Boolean> = deviceState.map {
        it is DeviceState.Disconnecting
    }.stateIn(scope, SharingStarted.Eagerly, false)

    val isPhysicallyDisconnected: StateFlow<Boolean> = deviceState.map {
        it is DeviceState.PhysicallyDisconnected
    }.stateIn(scope, SharingStarted.Eagerly, false)

    val isConnecting: StateFlow<Boolean> = deviceState.map {
        it is DeviceState.Connecting
    }.stateIn(scope, SharingStarted.Eagerly, false)

    val isChangingResolution: StateFlow<Boolean> = deviceState.map {
        it is CameraState.ChangingResolution
    }.stateIn(scope, SharingStarted.Eagerly, false)

    // Aliases for consistency - check both Connected and Active states
    val isCameraValid: StateFlow<Boolean> = deviceState.map { state ->
        when (state) {
            is DeviceState.Connected,
            is DeviceState.Active,
            is CameraState.PreviewStarting,
            is CameraState.PreviewActive,
            is CameraState.PreviewStopping -> true
            else -> false
        }
    }.stateIn(scope, SharingStarted.Eagerly, false)

    // Direct access to certain properties (maintained for compatibility)
    val currentSurface: Surface?
        get() = deviceData.value?.surface

    var pendingCtrlBlock: com.serenegiant.usb.USBMonitor.UsbControlBlock?
        get() = deviceData.value?.pendingCtrlBlock
        set(value) {
            updateData { current ->
                (current ?: createDefaultData()).copy(pendingCtrlBlock = value)
            }
        }

    val pendingResolutionChange: Resolution?
        get() = deviceData.value?.pendingResolutionChange

    val resolutionChangeCallback: ((Boolean) -> Unit)?
        get() = deviceData.value?.resolutionChangeCallback

    init {
        setData(createDefaultData())
    }

    override fun createDefaultData(): UvcCameraStateData {
        return UvcCameraStateData()
    }

    /**
     * Update aspect ratio based on resolution
     */
    fun updateAspectRatio(resolution: Resolution?) {
        val aspectRatio = resolution?.let {
            it.width.toFloat() / it.height.toFloat()
        }
        updateData { current ->
            (current ?: createDefaultData()).copy(aspectRatio = aspectRatio)
        }
        Log.d(tag, "Aspect ratio: $aspectRatio")
    }

    /**
     * Clear aspect ratio
     */
    fun clearAspectRatio() {
        updateAspectRatio(null)
    }

    /**
     * Set preview active state
     */
    fun setPreviewActive(active: Boolean) {
        val currentState = deviceState.value

        when {
            active && currentState is DeviceState.Connected -> {
                // Starting preview from connected state
                updateState(CameraState.PreviewStarting)
                // Then transition to active
                updateState(CameraState.PreviewActive)
            }
            active && currentState !is CameraState.PreviewActive -> {
                // Already in some other state, transition to preview active
                updateState(CameraState.PreviewActive)
            }
            !active && currentState is CameraState.PreviewActive -> {
                // Stopping preview
                updateState(CameraState.PreviewStopping)
                // Then back to connected (not idle!)
                updateState(DeviceState.Connected)
            }
            !active && currentState is CameraState.PreviewStarting -> {
                // Preview start was cancelled
                updateState(DeviceState.Connected)
            }
        }

        Log.d(tag, "Preview active: $active (from state: ${currentState::class.simpleName})")
    }

    /**
     * Set current surface
     */
    fun setSurface(surface: Surface?) {
        updateData { current ->
            (current ?: createDefaultData()).copy(surface = surface)
        }
        Log.d(tag, "Surface: ${surface?.let { "valid=${it.isValid}" } ?: "null"}")
    }

    /**
     * Set closing state
     */
    fun setClosing(closing: Boolean) {
        if (closing) {
            updateState(DeviceState.Disconnecting)
        } else {
            // Only transition back to Idle if currently in Disconnecting state
            val currentState = deviceState.value
            if (currentState is DeviceState.Disconnecting) {
                updateState(DeviceState.Idle)
                Log.d(tag, "Cleared Disconnecting state -> Idle")
            }
        }
    }

    /**
     * Set connecting state
     */
    fun setConnecting(connecting: Boolean) {
        if (connecting) {
            updateState(DeviceState.Connecting)
        } else {
            // Only transition back to Idle if currently in Connecting state
            // This prevents blocking future connection attempts
            val currentState = deviceState.value
            if (currentState is DeviceState.Connecting) {
                updateState(DeviceState.Idle)
                Log.d(tag, "Cleared Connecting state -> Idle")
            }
        }
    }

    /**
     * Mark camera as valid
     */
    fun setCameraValid(connectionId: Int) {
        currentConnectionId = connectionId
        // Only update state if not already in a valid state
        val currentState = deviceState.value
        if (!currentState.isOperational && currentState !is CameraState) {
            updateState(DeviceState.Connected)
        }
    }

    /**
     * Mark camera as invalid
     */
    fun setCameraInvalid() {
        val currentState = deviceState.value
        // Only mark as error if actually in an operational state
        // Don't invalidate if just changing preview states
        if (currentState.isOperational &&
            currentState !is CameraState.PreviewStopping &&
            currentState !is CameraState.ChangingResolution) {
            updateState(DeviceState.Error("Camera invalid", false))
        }
    }

    /**
     * Start resolution change
     */
    fun startResolutionChange(resolution: Resolution, callback: (Boolean) -> Unit) {
        updateState(CameraState.ChangingResolution)
        updateData { current ->
            (current ?: createDefaultData()).copy(
                pendingResolutionChange = resolution,
                resolutionChangeCallback = callback
            )
        }
        Log.d(tag, "Started resolution change to ${resolution.width}x${resolution.height}")
    }

    /**
     * Complete resolution change
     */
    fun completeResolutionChange(success: Boolean) {
        val callback = resolutionChangeCallback
        updateData { current ->
            current?.copy(
                pendingResolutionChange = null,
                resolutionChangeCallback = null
            )
        }
        updateState(if (success) DeviceState.Connected else DeviceState.Error("Resolution change failed", true))
        callback?.invoke(success)
        Log.d(tag, "Resolution change completed: $success")
    }

    // Device profiling methods

    /**
     * Start connection timing
     */
    fun startConnectionTiming() {
        connectionStartTime = System.currentTimeMillis()
    }

    /**
     * Check if device has profile
     */
    fun hasDeviceProfile(vendorId: Int, productId: Int): Boolean {
        val key = "$vendorId:$productId"
        val profile = deviceProfiles[key]
        return profile != null &&
                (System.currentTimeMillis() - profile.lastConnectionTime) < 3600000 // 1 hour cache
    }

    /**
     * Get device profile
     */
    fun getDeviceProfile(vendorId: Int, productId: Int): DeviceProfile? {
        val key = "$vendorId:$productId"
        return deviceProfiles[key]?.takeIf {
            (System.currentTimeMillis() - it.lastConnectionTime) < 3600000
        }
    }

    /**
     * Save device profile
     */
    fun saveDeviceProfile(vendorId: Int, productId: Int, resolution: Resolution) {
        val key = "$vendorId:$productId"
        val connectionTime = if (connectionStartTime > 0) {
            System.currentTimeMillis() - connectionStartTime
        } else 0L

        val existing = deviceProfiles[key]
        val avgTime = if (existing != null) {
            (existing.averageConnectionTime + connectionTime) / 2
        } else connectionTime

        deviceProfiles[key] = DeviceProfile(
            vendorId = vendorId,
            productId = productId,
            lastSuccessfulResolution = resolution,
            lastConnectionTime = System.currentTimeMillis(),
            averageConnectionTime = avgTime
        )

        lastSuccessfulDeviceKey = key
        Log.d(tag, "Saved device profile for $key - avg connection time: ${avgTime}ms")
    }

    override fun onPhysicalDisconnection() {
        updateData { current ->
            current?.copy(
                aspectRatio = null,
                surface = null,
                pendingCtrlBlock = null,
                pendingResolutionChange = null,
                resolutionChangeCallback = null
            )
        }
    }

    override fun onReset() {
        setData(createDefaultData())
        connectionStartTime = 0
        // Don't clear device profiles - they're useful for reconnection
    }

    override fun onDestroy() {
        deviceProfiles.clear()
    }

    /**
     * Override to allow camera-specific state transitions
     */
    override fun isValidTransition(from: DeviceState, to: DeviceState): Boolean {
        // Allow base transitions
        if (super.isValidTransition(from, to)) return true

        // Allow camera-specific transitions
        return when {
            // Allow preview state transitions
            from is DeviceState.Connected && to is CameraState.PreviewStarting -> true
            from is CameraState.PreviewStarting && to is CameraState.PreviewActive -> true
            from is CameraState.PreviewActive && to is CameraState.PreviewStopping -> true
            from is CameraState.PreviewStopping && to is DeviceState.Connected -> true

            // Allow resolution change transitions
            from is DeviceState.Connected && to is CameraState.ChangingResolution -> true
            from is CameraState.ChangingResolution && to is DeviceState.Connected -> true

            // Allow transitions from camera states back to connected
            from is CameraState && to is DeviceState.Connected -> true

            else -> false
        }
    }
}