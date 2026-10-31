package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.receiver.NetworkChangeReceiver

object NetworkHelper {
    const val CHANNEL_ID_STATUS = "campus_wifi_status_channel"
    const val CHANNEL_ID_SERVICE = "campus_wifi_service_channel"
    private const val NOTIFICATION_ID_STATUS = 101
    private const val CALLBACK_REQUEST_CODE = 2001

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Low importance status notification channel: silent, no vibration, minimal distraction
            val statusChannel = NotificationChannel(
                CHANNEL_ID_STATUS,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.notification_channel_desc)
                enableVibration(false)
                enableLights(false)
                setShowBadge(false)
            }
            nm.createNotificationChannel(statusChannel)

            // Silent foreground service channel (min importance)
            val serviceChannel = NotificationChannel(
                CHANNEL_ID_SERVICE,
                context.getString(R.string.foreground_service_channel_name),
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = context.getString(R.string.foreground_service_channel_desc)
                enableVibration(false)
                enableLights(false)
                setShowBadge(false)
            }
            nm.createNotificationChannel(serviceChannel)
        }
    }

    /**
     * Registers a zero-battery-overhead NetworkCallback with a PendingIntent.
     * The OS wakes up NetworkChangeReceiver only when Wi-Fi state changes.
     */
    fun registerPortalNetworkCallback(context: Context) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        val intent = Intent(context, NetworkChangeReceiver::class.java).apply {
            action = NetworkChangeReceiver.ACTION_NETWORK_CALLBACK
        }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getBroadcast(context, CALLBACK_REQUEST_CODE, intent, flags)

        try {
            // Note: On Android 8.0+ (API 26+), registerNetworkCallback(request, pendingIntent)
            // allows background wakeup without any persistent service or polling
            cm.registerNetworkCallback(request, pendingIntent)
        } catch (_: Exception) {}
    }

    fun unregisterPortalNetworkCallback(context: Context) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val intent = Intent(context, NetworkChangeReceiver::class.java).apply {
            action = NetworkChangeReceiver.ACTION_NETWORK_CALLBACK
        }
        val flags = PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getBroadcast(context, CALLBACK_REQUEST_CODE, intent, flags)
        if (pendingIntent != null) {
            try {
                cm.unregisterNetworkCallback(pendingIntent)
            } catch (_: Exception) {}
        }
    }

    /**
     * Checks if given network or current active network is Wi-Fi and needs captive portal login:
     * (a) Reports NET_CAPABILITY_CAPTIVE_PORTAL or lacks NET_CAPABILITY_VALIDATED
     */
    fun needsPortalLogin(cm: ConnectivityManager, network: Network?): Boolean {
        val net = network ?: cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false

        val isWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        if (!isWifi) return false

        val hasCaptivePortal = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)
        val isValidated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

        return hasCaptivePortal || !isValidated
    }

    /**
     * Posts a quiet, low-importance status notification.
     */
    fun postStatusNotification(context: Context, title: String, content: String, isSuccess: Boolean) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val appIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_STATUS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setSilent(true)
            .build()

        try {
            nm.notify(NOTIFICATION_ID_STATUS, notification)
        } catch (_: SecurityException) {
            // Notifications permission not granted
        }
    }
}
