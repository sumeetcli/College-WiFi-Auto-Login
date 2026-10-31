package com.example.data

import android.content.Context
import com.example.security.CryptoManager
import org.json.JSONArray
import org.json.JSONObject

enum class PortalStatusCode {
    IDLE,
    CHECKING,
    LOGGED_IN,
    NOT_CAMPUS_WIFI,
    FAILED
}

data class LogEntry(
    val timestamp: Long,
    val message: String,
    val isSuccess: Boolean
)

class AppPreferences(context: Context) {
    companion object {
        private const val PREFS_NAME = "campus_wifi_prefs"
        private const val KEY_ENCRYPTED_USERNAME = "enc_username"
        private const val KEY_ENCRYPTED_PASSWORD = "enc_password"
        private const val KEY_AUTO_LOGIN_ENABLED = "auto_login_enabled"
        private const val KEY_RELIABILITY_MODE = "reliability_mode"
        private const val KEY_DAILY_CAP_GB = "daily_cap_gb"
        private const val KEY_LAST_STATUS_CODE = "last_status_code"
        private const val KEY_LAST_STATUS_MESSAGE = "last_status_message"
        private const val KEY_LAST_STATUS_TIME = "last_status_time"
        private const val KEY_RECENT_LOGS = "recent_logs"
        private const val KEY_USAGE_TRACKING_ENABLED = "usage_tracking_enabled"
        private const val MAX_LOGS = 10
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var username: String
        get() = CryptoManager.decrypt(prefs.getString(KEY_ENCRYPTED_USERNAME, "") ?: "")
        set(value) {
            val enc = CryptoManager.encrypt(value)
            prefs.edit().putString(KEY_ENCRYPTED_USERNAME, enc).apply()
        }

    var password: String
        get() = CryptoManager.decrypt(prefs.getString(KEY_ENCRYPTED_PASSWORD, "") ?: "")
        set(value) {
            val enc = CryptoManager.encrypt(value)
            prefs.edit().putString(KEY_ENCRYPTED_PASSWORD, enc).apply()
        }

    val hasCredentials: Boolean
        get() = username.isNotBlank() && password.isNotBlank()

    fun clearCredentials() {
        prefs.edit()
            .remove(KEY_ENCRYPTED_USERNAME)
            .remove(KEY_ENCRYPTED_PASSWORD)
            .apply()
    }

    var isAutoLoginEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_LOGIN_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_LOGIN_ENABLED, value).apply()

    var isReliabilityModeEnabled: Boolean
        get() = prefs.getBoolean(KEY_RELIABILITY_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_RELIABILITY_MODE, value).apply()

    var dailyCapGb: Float
        get() = prefs.getFloat(KEY_DAILY_CAP_GB, 13.0f)
        set(value) = prefs.edit().putFloat(KEY_DAILY_CAP_GB, value).apply()

    var isUsageTrackingEnabled: Boolean
        get() = prefs.getBoolean(KEY_USAGE_TRACKING_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_USAGE_TRACKING_ENABLED, value).apply()

    var lastStatusCode: PortalStatusCode
        get() {
            val raw = prefs.getString(KEY_LAST_STATUS_CODE, PortalStatusCode.IDLE.name)
            return try {
                PortalStatusCode.valueOf(raw ?: PortalStatusCode.IDLE.name)
            } catch (_: Exception) {
                PortalStatusCode.IDLE
            }
        }
        set(value) = prefs.edit().putString(KEY_LAST_STATUS_CODE, value.name).apply()

    var lastStatusMessage: String
        get() = prefs.getString(KEY_LAST_STATUS_MESSAGE, "Ready") ?: "Ready"
        set(value) = prefs.edit().putString(KEY_LAST_STATUS_MESSAGE, value).apply()

    var lastStatusTime: Long
        get() = prefs.getLong(KEY_LAST_STATUS_TIME, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_STATUS_TIME, value).apply()

    fun updateStatus(code: PortalStatusCode, message: String) {
        lastStatusCode = code
        lastStatusMessage = message
        lastStatusTime = System.currentTimeMillis()
        addLog(message, code == PortalStatusCode.LOGGED_IN)
    }

    fun getRecentLogs(): List<LogEntry> {
        val jsonStr = prefs.getString(KEY_RECENT_LOGS, null) ?: return emptyList()
        return try {
            val array = JSONArray(jsonStr)
            val list = mutableListOf<LogEntry>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    LogEntry(
                        timestamp = obj.optLong("time", 0L),
                        message = obj.optString("msg", ""),
                        isSuccess = obj.optBoolean("success", false)
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun addLog(message: String, isSuccess: Boolean) {
        try {
            val existing = getRecentLogs().toMutableList()
            existing.add(0, LogEntry(System.currentTimeMillis(), message, isSuccess))
            val trimmed = existing.take(MAX_LOGS)
            val array = JSONArray()
            for (item in trimmed) {
                val obj = JSONObject().apply {
                    put("time", item.timestamp)
                    put("msg", item.message)
                    put("success", item.isSuccess)
                }
                array.put(obj)
            }
            prefs.edit().putString(KEY_RECENT_LOGS, array.toString()).apply()
        } catch (_: Exception) {}
    }
}
