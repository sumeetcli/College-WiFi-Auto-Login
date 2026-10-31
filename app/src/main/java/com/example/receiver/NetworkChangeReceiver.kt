package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import com.example.data.AppPreferences
import com.example.data.PortalRepository
import com.example.data.PortalStatusCode
import com.example.service.NetworkHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Short-lived BroadcastReceiver woken by Android OS when Wi-Fi state changes.
 * Executes on a short background coroutine, verifies captive status and reachability,
 * logs into the portal using the specific Network handle, posts a quiet status notification,
 * and immediately exits. Consumes virtually zero battery.
 */
class NetworkChangeReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_NETWORK_CALLBACK = "com.example.campuswifi.ACTION_NETWORK_CALLBACK"
        const val ACTION_TRIGGER_LOGIN = "com.example.campuswifi.ACTION_TRIGGER_LOGIN"
        const val ACTION_TRIGGER_LOGOUT = "com.example.campuswifi.ACTION_TRIGGER_LOGOUT"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val prefs = AppPreferences(context)

        // Manual actions from UI / shortcuts
        if (intent.action == ACTION_TRIGGER_LOGOUT) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    handleLogout(context, prefs)
                } finally {
                    pendingResult.finish()
                }
            }
            return
        }

        // If auto-login is disabled and this wasn't a manual login request, do nothing
        if (!prefs.isAutoLoginEnabled && intent.action != ACTION_TRIGGER_LOGIN) {
            return
        }

        if (!prefs.hasCredentials) {
            return
        }

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val targetNetwork: Network? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK, Network::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK)
        } ?: cm.activeNetwork

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handleAutoLogin(context, cm, targetNetwork, prefs, isManual = (intent.action == ACTION_TRIGGER_LOGIN))
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun handleAutoLogin(
        context: Context,
        cm: ConnectivityManager,
        network: Network?,
        prefs: AppPreferences,
        isManual: Boolean
    ) {
        // Step 1: Verify network is Wi-Fi and needs captive portal authorization
        val needsLogin = NetworkHelper.needsPortalLogin(cm, network)
        if (!needsLogin && !isManual) {
            // Already validated internet or not Wi-Fi
            return
        }

        // Step 2: Probe reachability of 172.16.16.16:8090 (3-second timeout)
        // This uniquely identifies the campus Wi-Fi network without requiring location/SSID permissions.
        val reachable = PortalRepository.isPortalReachable(network)
        if (!reachable) {
            if (isManual) {
                prefs.updateStatus(
                    PortalStatusCode.NOT_CAMPUS_WIFI,
                    "Campus portal (172.16.16.16) is not reachable on this network."
                )
            }
            return
        }

        // Step 3: Perform login through that specific Network handle with max 2 retries
        val username = prefs.username
        val password = prefs.password
        if (username.isBlank() || password.isBlank()) {
            return
        }

        val result = PortalRepository.performLogin(
            username = username,
            password = password,
            network = network,
            maxRetries = 2
        )

        if (result.isSuccess) {
            prefs.updateStatus(PortalStatusCode.LOGGED_IN, result.message)
            NetworkHelper.postStatusNotification(
                context,
                "Campus WiFi Connected",
                result.message,
                isSuccess = true
            )
        } else {
            prefs.updateStatus(PortalStatusCode.FAILED, "Login failed: ${result.message}")
            NetworkHelper.postStatusNotification(
                context,
                "Campus WiFi Login Failed",
                result.message,
                isSuccess = false
            )
        }
    }

    private fun handleLogout(context: Context, prefs: AppPreferences) {
        val username = prefs.username
        if (username.isBlank()) return

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val network = cm?.activeNetwork

        val result = PortalRepository.performLogout(username, network)
        prefs.updateStatus(
            if (result.isSuccess) PortalStatusCode.IDLE else PortalStatusCode.FAILED,
            "Logged out: ${result.message}"
        )
        NetworkHelper.postStatusNotification(
            context,
            "Campus WiFi",
            "Logged out: ${result.message}",
            isSuccess = result.isSuccess
        )
    }
}
