package com.example.ui

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppPreferences
import com.example.data.DailyUsageStats
import com.example.data.DataUsageTracker
import com.example.data.LogEntry
import com.example.data.PortalRepository
import com.example.data.PortalStatusCode
import com.example.service.NetworkHelper
import com.example.service.ReliabilityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CampusWifiUiState(
    val hasCredentials: Boolean = false,
    val isAutoLoginEnabled: Boolean = true,
    val isReliabilityModeEnabled: Boolean = false,
    val statusCode: PortalStatusCode = PortalStatusCode.IDLE,
    val statusMessage: String = "Ready",
    val lastStatusTime: Long = 0L,
    val isOperating: Boolean = false,
    val dailyCapGb: Float = 13.0f,
    val usageStats: DailyUsageStats? = null,
    val hasUsagePermission: Boolean = false,
    val recentLogs: List<LogEntry> = emptyList(),
    val showCredentialsDialog: Boolean = false,
    val showCapDialog: Boolean = false,
    val errorMessage: String? = null
)

class CampusWifiViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = AppPreferences(application)
    private val _uiState = MutableStateFlow(CampusWifiUiState())
    val uiState: StateFlow<CampusWifiUiState> = _uiState.asStateFlow()

    init {
        NetworkHelper.createNotificationChannels(application)
        loadState()
        // Register the zero-overhead network callback whenever app opens
        if (prefs.isAutoLoginEnabled && prefs.hasCredentials) {
            NetworkHelper.registerPortalNetworkCallback(application)
        }
    }

    fun onResume() {
        loadState()
        refreshUsageStats()
    }

    private fun loadState() {
        val hasUsage = DataUsageTracker.hasUsagePermission(getApplication())
        val usage = if (hasUsage) {
            DataUsageTracker.getUsageStats(getApplication(), prefs.dailyCapGb)
        } else null

        _uiState.update {
            it.copy(
                hasCredentials = prefs.hasCredentials,
                isAutoLoginEnabled = prefs.isAutoLoginEnabled,
                isReliabilityModeEnabled = prefs.isReliabilityModeEnabled,
                statusCode = prefs.lastStatusCode,
                statusMessage = prefs.lastStatusMessage,
                lastStatusTime = prefs.lastStatusTime,
                dailyCapGb = prefs.dailyCapGb,
                usageStats = usage,
                hasUsagePermission = hasUsage,
                recentLogs = prefs.getRecentLogs()
            )
        }
    }

    fun saveCredentials(username: String, pass: String) {
        if (username.isBlank() || pass.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Username and password cannot be empty") }
            return
        }
        prefs.username = username.trim()
        prefs.password = pass.trim()
        prefs.isAutoLoginEnabled = true

        NetworkHelper.registerPortalNetworkCallback(getApplication())

        _uiState.update {
            it.copy(
                hasCredentials = true,
                isAutoLoginEnabled = true,
                showCredentialsDialog = false,
                errorMessage = null
            )
        }
        prefs.addLog("Credentials saved securely in Keystore", true)
        refreshLogs()

        // Automatically trigger check and login
        loginNow()
    }

    fun setAutoLoginEnabled(enabled: Boolean) {
        prefs.isAutoLoginEnabled = enabled
        if (enabled) {
            NetworkHelper.registerPortalNetworkCallback(getApplication())
        } else {
            NetworkHelper.unregisterPortalNetworkCallback(getApplication())
        }
        _uiState.update { it.copy(isAutoLoginEnabled = enabled) }
        prefs.addLog("Auto-login ${if (enabled) "enabled" else "disabled"}", true)
        refreshLogs()
    }

    fun setReliabilityModeEnabled(enabled: Boolean) {
        prefs.isReliabilityModeEnabled = enabled
        if (enabled) {
            ReliabilityService.startService(getApplication())
        } else {
            ReliabilityService.stopService(getApplication())
        }
        _uiState.update { it.copy(isReliabilityModeEnabled = enabled) }
        prefs.addLog("Reliability mode ${if (enabled) "enabled" else "disabled"}", true)
        refreshLogs()
    }

    fun updateDailyCap(newCapGb: Float) {
        val validCap = newCapGb.coerceIn(1.0f, 100.0f)
        prefs.dailyCapGb = validCap
        _uiState.update { it.copy(dailyCapGb = validCap, showCapDialog = false) }
        refreshUsageStats()
    }

    fun openCredentialsDialog(open: Boolean) {
        _uiState.update { it.copy(showCredentialsDialog = open, errorMessage = null) }
    }

    fun openCapDialog(open: Boolean) {
        _uiState.update { it.copy(showCapDialog = open) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun refreshUsageStats() {
        viewModelScope.launch(Dispatchers.IO) {
            val hasUsage = DataUsageTracker.hasUsagePermission(getApplication())
            val stats = if (hasUsage) {
                DataUsageTracker.getUsageStats(getApplication(), prefs.dailyCapGb)
            } else null

            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        hasUsagePermission = hasUsage,
                        usageStats = stats
                    )
                }
            }
        }
    }

    fun loginNow() {
        if (!prefs.hasCredentials) {
            _uiState.update { it.copy(showCredentialsDialog = true) }
            return
        }

        _uiState.update {
            it.copy(
                isOperating = true,
                statusCode = PortalStatusCode.CHECKING,
                statusMessage = "Probing campus portal (172.16.16.16:8090)..."
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            val cm = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val network = cm?.activeNetwork

            val reachable = PortalRepository.isPortalReachable(network)
            if (!reachable) {
                withContext(Dispatchers.Main) {
                    val msg = "Campus portal (172.16.16.16) is not reachable on this network."
                    prefs.updateStatus(PortalStatusCode.NOT_CAMPUS_WIFI, msg)
                    _uiState.update {
                        it.copy(
                            isOperating = false,
                            statusCode = PortalStatusCode.NOT_CAMPUS_WIFI,
                            statusMessage = msg,
                            lastStatusTime = System.currentTimeMillis()
                        )
                    }
                    refreshLogs()
                }
                return@launch
            }

            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(statusMessage = "Logging into Sophos portal...") }
            }

            val result = PortalRepository.performLogin(
                username = prefs.username,
                password = prefs.password,
                network = network,
                maxRetries = 2
            )

            withContext(Dispatchers.Main) {
                val code = if (result.isSuccess) PortalStatusCode.LOGGED_IN else PortalStatusCode.FAILED
                prefs.updateStatus(code, result.message)
                _uiState.update {
                    it.copy(
                        isOperating = false,
                        statusCode = code,
                        statusMessage = result.message,
                        lastStatusTime = System.currentTimeMillis()
                    )
                }
                NetworkHelper.postStatusNotification(
                    getApplication(),
                    if (result.isSuccess) "Campus WiFi Connected" else "Campus WiFi Login Failed",
                    result.message,
                    result.isSuccess
                )
                refreshLogs()
                refreshUsageStats()
            }
        }
    }

    fun logout() {
        if (!prefs.hasCredentials) return

        _uiState.update {
            it.copy(
                isOperating = true,
                statusMessage = "Logging out..."
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            val cm = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val network = cm?.activeNetwork

            val result = PortalRepository.performLogout(prefs.username, network)

            withContext(Dispatchers.Main) {
                val code = if (result.isSuccess) PortalStatusCode.IDLE else PortalStatusCode.FAILED
                val msg = "Logged out: ${result.message}"
                prefs.updateStatus(code, msg)
                _uiState.update {
                    it.copy(
                        isOperating = false,
                        statusCode = code,
                        statusMessage = msg,
                        lastStatusTime = System.currentTimeMillis()
                    )
                }
                refreshLogs()
            }
        }
    }

    private fun refreshLogs() {
        _uiState.update { it.copy(recentLogs = prefs.getRecentLogs()) }
    }
}
