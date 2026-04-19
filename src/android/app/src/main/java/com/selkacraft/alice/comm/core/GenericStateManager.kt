package com.selkacraft.alice.comm.core

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Generic state manager that consolidates common state management patterns
 * Replaces BaseStateManager with a more elegant and type-safe approach
 *
 * @param T The device-specific data type implementing DeviceStateData
 * @param scope Coroutine scope for state management
 * @param tag Logging tag for this manager
 */
abstract class GenericStateManager<T : DeviceStateData>(
    protected val scope: CoroutineScope,
    protected val tag: String
) {
    // Single state flow replacing multiple boolean flags
    private val _deviceState = MutableStateFlow<DeviceState>(DeviceState.Idle)
    val deviceState: StateFlow<DeviceState> = _deviceState.asStateFlow()

    // Device-specific data in a single flow
    private val _deviceData = MutableStateFlow<T?>(null)
    val deviceData: StateFlow<T?> = _deviceData.asStateFlow()

    // Connection tracking
    @Volatile
    var currentConnectionId: Int? = null
        protected set

    private var connectionCounter = 0

    // Lifecycle management
    private val isDestroyed = AtomicBoolean(false)
    protected val activeJobs = mutableListOf<Job>()

    /**
     * Generate a new connection ID
     */
    fun generateConnectionId(): Int = ++connectionCounter

    /**
     * Update device state with logging
     */
    fun updateState(newState: DeviceState) {
        val oldState = _deviceState.value

        // Validate state transition
        if (!isValidTransition(oldState, newState)) {
            Log.w(tag, "Invalid state transition: ${oldState::class.simpleName} -> ${newState::class.simpleName}, ignoring")
            return
        }

        _deviceState.value = newState
        Log.d(tag, "State transition: ${oldState::class.simpleName} -> ${newState::class.simpleName}")

        // Handle state-specific logic - only clear connection on true disconnection
        when (newState) {
            is DeviceState.Connected,
            is DeviceState.Active -> {
                if (currentConnectionId == null) {
                    currentConnectionId = generateConnectionId()
                }
            }
            is DeviceState.Idle,
            is DeviceState.PhysicallyDisconnected -> {
                // Only clear connection ID for true disconnection states
                currentConnectionId = null
            }
            is DeviceState.Disconnecting -> {
                // Don't clear connection ID yet - wait for actual disconnection
            }
            else -> {
                // Preserve connection ID for other states
            }
        }
    }

    /**
     * Validate if a state transition is allowed
     */
    protected open fun isValidTransition(from: DeviceState, to: DeviceState): Boolean {
        // Allow same state (no-op)
        if (from::class == to::class) return true

        // Allow transitions from Error or Idle to any state (for reconnection)
        if (from is DeviceState.Error || from is DeviceState.Idle) return true

        // Allow transitions to Idle from any state (for reset)
        if (to is DeviceState.Idle) return true

        // Don't allow transitions from PhysicallyDisconnected except to Idle
        if (from is DeviceState.PhysicallyDisconnected && to !is DeviceState.Idle) {
            return false
        }

        // Allow all other transitions by default - subclasses can override for stricter rules
        return true
    }

    /**
     * Update device-specific data
     */
    fun updateData(transform: (T?) -> T?) {
        _deviceData.update { currentData ->
            transform(currentData)
        }
    }

    /**
     * Set device data directly
     */
    fun setData(data: T?) {
        _deviceData.value = data
    }

    /**
     * Get current data or create default
     */
    fun getDataOrDefault(default: () -> T): T {
        return _deviceData.value ?: default().also { setData(it) }
    }

    /**
     * Handle physical disconnection
     */
    open fun handlePhysicalDisconnection() {
        updateState(DeviceState.Idle)  // Use Idle to allow reconnection
        currentConnectionId = null
        onPhysicalDisconnection()
    }

    /**
     * Reset to initial state
     */
    open fun reset() {
        if (isDestroyed.get()) {
            Log.w(tag, "Cannot reset - destroyed")
            return
        }

        // Cancel all tracked jobs
        activeJobs.forEach { it.cancel() }
        activeJobs.clear()

        // Reset state to Idle (always allow reconnection from Idle)
        updateState(DeviceState.Idle)
        currentConnectionId = null
        _deviceData.value = null

        Log.d(tag, "Reset")
        onReset()
    }

    /**
     * Destroy and clean up
     */
    open fun destroy() {
        if (isDestroyed.getAndSet(true)) return

        activeJobs.forEach { it.cancel() }
        activeJobs.clear()
        reset()

        Log.d(tag, "Destroyed")
        onDestroy()
    }

    /**
     * Track a job for lifecycle management
     */
    protected fun trackJob(job: Job) {
        activeJobs.add(job)
        job.invokeOnCompletion { activeJobs.remove(job) }
    }

    /**
     * Check if destroyed
     */
    fun isDestroyed(): Boolean = isDestroyed.get()

    // Convenience state checks
    val isOperational: Boolean
        get() = deviceState.value.isOperational

    val isTransitioning: Boolean
        get() = deviceState.value.isTransitioning

    val isDisconnected: Boolean
        get() = deviceState.value.isDisconnected

    // Template methods for device-specific behavior
    protected abstract fun onPhysicalDisconnection()
    protected abstract fun onReset()
    protected abstract fun onDestroy()

    // Abstract method to create default data
    protected abstract fun createDefaultData(): T
}