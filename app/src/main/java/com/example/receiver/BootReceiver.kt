package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.AppPreferences
import com.example.service.NetworkHelper
import com.example.service.ReliabilityService

/**
 * Re-registers zero-overhead ConnectivityManager NetworkCallback after device reboot.
 * Marked for on-device testing.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val prefs = AppPreferences(context)
            NetworkHelper.createNotificationChannels(context)

            if (prefs.isAutoLoginEnabled && prefs.hasCredentials) {
                NetworkHelper.registerPortalNetworkCallback(context)

                if (prefs.isReliabilityModeEnabled) {
                    ReliabilityService.startService(context)
                }
            }
        }
    }
}
