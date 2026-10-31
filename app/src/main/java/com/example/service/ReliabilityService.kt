package com.example.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.AppPreferences
import com.example.receiver.NetworkChangeReceiver

/**
 * Minimal low-importance Foreground Service for OEM devices (MIUI, ColorOS, etc.)
 * that aggressively kill background PendingIntent callbacks.
 * Uses a static, silent notification without wake locks or live speed updates.
 */
class ReliabilityService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 901

        fun startService(context: Context) {
            val intent = Intent(context, ReliabilityService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, ReliabilityService::class.java)
            context.stopService(intent)
        }
    }

    private var connectivityManager: ConnectivityManager? = null
    private var liveCallback: ConnectivityManager.NetworkCallback? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        NetworkHelper.createNotificationChannels(this)
        startForegroundWithStaticNotification()
        registerLiveCallback()
    }

    private fun startForegroundWithStaticNotification() {
        val appIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, NetworkHelper.CHANNEL_ID_SERVICE)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Campus WiFi Active")
            .setContentText("Reliability mode active: monitoring campus Wi-Fi")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(pendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun registerLiveCallback() {
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val cm = connectivityManager ?: return

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        liveCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                val prefs = AppPreferences(this@ReliabilityService)
                if (prefs.isAutoLoginEnabled && prefs.hasCredentials) {
                    val triggerIntent = Intent(this@ReliabilityService, NetworkChangeReceiver::class.java).apply {
                        action = NetworkChangeReceiver.ACTION_NETWORK_CALLBACK
                        putExtra(ConnectivityManager.EXTRA_NETWORK, network)
                    }
                    sendBroadcast(triggerIntent)
                }
            }
        }

        try {
            cm.registerNetworkCallback(request, liveCallback!!)
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        liveCallback?.let {
            try {
                connectivityManager?.unregisterNetworkCallback(it)
            } catch (_: Exception) {}
        }
    }
}
