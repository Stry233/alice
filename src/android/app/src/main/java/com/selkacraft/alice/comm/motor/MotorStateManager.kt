package com.selkacraft.alice.comm.motor

import android.content.Context
import android.content.SharedPreferences
import com.selkacraft.alice.comm.core.GenericStateManager
import com.selkacraft.alice.comm.core.DeviceState
import com.selkacraft.alice.comm.core.MotorState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import android.util.Log

/**
 * Simplified motor state manager using generic state management
 */
class MotorStateManager(
    private val context: Context,
    scope: CoroutineScope
) : GenericStateManager<MotorStateData>(scope, "MotorStateManager") {

    companion object {
        private const val PREFS_NAME = "motor_prefs"
        private const val KEY_LAST_POSITION = "last_position"
        private const val DEFAULT_POSITION = 2048
        private const val POSITION_SAVE_DEBOUNCE_MS = 500L
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var positionSaveJob: Job? = null

    // Expose specific state properties as flows for compatibility
    val currentPosition: StateFlow<Int> = deviceData.map {
        it?.position ?: loadSavedPosition()
    }.stateIn(scope, SharingStarted.Eagerly, loadSavedPosition())

    val isCalibrated: StateFlow<Boolean> = deviceData.map {
        it?.isCalibrated ?: false
    }.stateIn(scope, SharingStarted.Eagerly, false)

    init {
        // Initialize with saved position
        setData(createDefaultData())
    }

    override fun createDefaultData(): MotorStateData {
        return MotorStateData(position = loadSavedPosition())
    }

    /**
     * Load saved position from preferences
     */
    private fun loadSavedPosition(): Int =
        prefs.getInt(KEY_LAST_POSITION, DEFAULT_POSITION)

    /**
     * Update motor position
     */
    fun updatePosition(position: Int) {
        val clampedPosition = position.coerceIn(0, 4095)
        updateData { current ->
            (current ?: createDefaultData()).copy(position = clampedPosition)
        }
        debounceSavePosition(clampedPosition)
    }

    /**
     * Check if position command should be sent (deduplication)
     */
    fun shouldSendPositionCommand(position: Int): Boolean {
        val currentData = deviceData.value ?: return true
        val now = System.currentTimeMillis()
        val command = "POS $position"

        val shouldSend = currentData.shouldSendPositionCommand(position)

        if (shouldSend) {
            updateData { current ->
                current?.copy(
                    lastPositionCommand = command,
                    lastCommandTime = now
                )
            }
        }

        return shouldSend
    }

    /**
     * Update calibration state
     */
    fun setCalibrated(calibrated: Boolean) {
        updateData { current ->
            (current ?: createDefaultData()).copy(isCalibrated = calibrated)
        }

        if (calibrated) {
            updateState(DeviceState.Active)
        }

        Log.d(tag, "Calibration: $calibrated")
    }

    /**
     * Save position with debouncing
     */
    private fun debounceSavePosition(position: Int) {
        positionSaveJob?.cancel()
        positionSaveJob = scope.launch {
            delay(POSITION_SAVE_DEBOUNCE_MS)
            withContext(Dispatchers.IO) {
                prefs.edit().putInt(KEY_LAST_POSITION, position).apply()
                Log.d(tag, "Position saved: $position")
            }
        }
        positionSaveJob?.let { trackJob(it) }
    }

    override fun onPhysicalDisconnection() {
        updateData { current ->
            current?.copy(isCalibrated = false)
        }
    }

    override fun onReset() {
        val savedPosition = loadSavedPosition()
        setData(MotorStateData(position = savedPosition))
    }

    override fun onDestroy() {
        positionSaveJob?.cancel()
        positionSaveJob = null
    }
}