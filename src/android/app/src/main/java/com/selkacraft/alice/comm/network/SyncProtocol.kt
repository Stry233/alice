package com.selkacraft.alice.comm.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Network sync protocol message types and serialization.
 * Matches the desktop C++ implementation for bidirectional sync.
 */

private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
}

@Serializable
data class SyncMessage(
    val type: String,
    val timestamp: Long = System.currentTimeMillis(),
    val sender: String = "android",
    val payload: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap()
) {
    fun serialize(): String = json.encodeToString(this)

    companion object {
        fun deserialize(data: String): SyncMessage? {
            return try {
                json.decodeFromString<SyncMessage>(data)
            } catch (e: Exception) {
                null
            }
        }

        fun stateUpdate(
            motorPosition: Int,
            depth: Float,
            confidence: Float,
            focusMode: String,
            enabled: Boolean,
            activelyFocusing: Boolean,
            facesDetected: Int,
            motorConnected: Boolean,
            realSenseConnected: Boolean
        ): SyncMessage {
            val payloadMap = buildJsonMap {
                put("motorPosition", motorPosition)
                put("depth", depth)
                put("depthConfidence", confidence)
                put("focusMode", focusMode)
                put("isEnabled", enabled)
                put("isActivelyFocusing", activelyFocusing)
                put("facesDetected", facesDetected)
                put("connectionStates", buildJsonMap {
                    put("motor", if (motorConnected) "Active" else "Disconnected")
                    put("realSense", if (realSenseConnected) "Active" else "Disconnected")
                })
            }
            return SyncMessage(type = "STATE_UPDATE", payload = payloadMap)
        }

        fun motorCommand(position: Int, source: String = "manual", highPriority: Boolean = false): SyncMessage {
            val payloadMap = buildJsonMap {
                put("position", position)
                put("source", source)
                put("priority", if (highPriority) "HIGH" else "NORMAL")
            }
            return SyncMessage(type = "MOTOR_COMMAND", payload = payloadMap)
        }

        fun modeChange(
            mode: String,
            enabled: Boolean,
            confidenceThreshold: Float,
            smoothing: Boolean,
            responseSpeed: Int
        ): SyncMessage {
            val payloadMap = buildJsonMap {
                put("mode", mode)
                put("enabled", enabled)
                put("settings", buildJsonMap {
                    put("confidenceThreshold", confidenceThreshold)
                    put("smoothing", smoothing)
                    put("responseSpeed", responseSpeed)
                })
            }
            return SyncMessage(type = "MODE_CHANGE", payload = payloadMap)
        }

        fun authenticate(token: String): SyncMessage {
            val payloadMap = buildJsonMap {
                put("token", token)
            }
            return SyncMessage(type = "AUTHENTICATE", payload = payloadMap)
        }

        fun calibrationSync(
            action: String,
            mappingJson: kotlinx.serialization.json.JsonElement? = null
        ): SyncMessage {
            val payloadMap = buildJsonMap {
                put("action", action)
                if (mappingJson != null) {
                    putElement("mapping", mappingJson)
                }
            }
            return SyncMessage(type = "CALIBRATION_SYNC", payload = payloadMap)
        }

        fun streamControl(color: Boolean, depth: Boolean, capture: Boolean): SyncMessage {
            val payloadMap = buildJsonMap {
                put("color", color)
                put("depth", depth)
                put("capture", capture)
            }
            return SyncMessage(type = "STREAM_CONTROL", payload = payloadMap)
        }

        fun heartbeat(uptime: Long, deviceInfo: String = ""): SyncMessage {
            val payloadMap = buildJsonMap {
                put("uptime", uptime)
                put("deviceInfo", deviceInfo)
            }
            return SyncMessage(type = "HEARTBEAT", payload = payloadMap)
        }

        fun measurePosition(x: Float, y: Float): SyncMessage {
            val payloadMap = buildJsonMap {
                put("x", x)
                put("y", y)
            }
            return SyncMessage(type = "MEASURE_POSITION", payload = payloadMap)
        }
    }
}

/**
 * Helper to build a JSON map for payload construction.
 */
fun buildJsonMap(
    builder: JsonMapBuilder.() -> Unit
): Map<String, kotlinx.serialization.json.JsonElement> {
    return JsonMapBuilder().apply(builder).build()
}

class JsonMapBuilder {
    private val map = mutableMapOf<String, kotlinx.serialization.json.JsonElement>()

    fun put(key: String, value: String) {
        map[key] = kotlinx.serialization.json.JsonPrimitive(value)
    }
    fun put(key: String, value: Int) {
        map[key] = kotlinx.serialization.json.JsonPrimitive(value)
    }
    fun put(key: String, value: Long) {
        map[key] = kotlinx.serialization.json.JsonPrimitive(value)
    }
    fun put(key: String, value: Float) {
        map[key] = kotlinx.serialization.json.JsonPrimitive(value)
    }
    fun put(key: String, value: Boolean) {
        map[key] = kotlinx.serialization.json.JsonPrimitive(value)
    }
    fun put(key: String, value: Map<String, kotlinx.serialization.json.JsonElement>) {
        map[key] = kotlinx.serialization.json.JsonObject(value)
    }
    fun putElement(key: String, value: kotlinx.serialization.json.JsonElement) {
        map[key] = value
    }

    fun build() = map.toMap()
}

object SyncConstants {
    const val DEFAULT_PORT = 8765
    const val STATE_UPDATE_INTERVAL_MS = 100L // 10 Hz
    const val HEARTBEAT_INTERVAL_MS = 5000L
    const val CONNECTION_TIMEOUT_MS = 10000L
}
