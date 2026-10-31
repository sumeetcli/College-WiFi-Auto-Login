package com.example.data

import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Process
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class DayUsage(
    val dayLabel: String,
    val dateEpochMillis: Long,
    val bytesUsed: Long
)

data class DailyUsageStats(
    val bytesUsedToday: Long,
    val dailyCapBytes: Long,
    val bytesLeftToday: Long,
    val usageRatio: Float,
    val last7Days: List<DayUsage>
)

object DataUsageTracker {

    /**
     * Checks if the app has been granted Usage Access (PACKAGE_USAGE_STATS).
     */
    fun hasUsagePermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        @Suppress("DEPRECATION")
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Lazily computes Wi-Fi data usage for today and the last 7 days.
     * ONLY called when the user opens the app, never running in the background.
     */
    fun getUsageStats(context: Context, capGb: Float): DailyUsageStats? {
        if (!hasUsagePermission(context)) return null

        val networkStatsManager = context.getSystemService(Context.NETWORK_STATS_SERVICE) as? NetworkStatsManager
            ?: return null

        val capBytes = (capGb * 1024L * 1024L * 1024L).toLong()

        // Local midnight today
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfToday = cal.timeInMillis
        val now = System.currentTimeMillis()

        val usedToday = queryWifiBytes(networkStatsManager, startOfToday, now)
        val bytesLeft = (capBytes - usedToday).coerceAtLeast(0L)
        val ratio = if (capBytes > 0) (usedToday.toFloat() / capBytes.toFloat()).coerceIn(0f, 1f) else 0f

        // Compute past 7 days
        val history = mutableListOf<DayUsage>()
        val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())

        for (i in 6 downTo 0) {
            val dayCal = Calendar.getInstance().apply {
                timeInMillis = startOfToday
                add(Calendar.DAY_OF_YEAR, -i)
            }
            val dayStart = dayCal.timeInMillis
            dayCal.add(Calendar.DAY_OF_YEAR, 1)
            val dayEnd = if (i == 0) now else dayCal.timeInMillis

            val bytes = queryWifiBytes(networkStatsManager, dayStart, dayEnd)
            val label = if (i == 0) "Today" else dayFormat.format(Date(dayStart))
            history.add(DayUsage(dayLabel = label, dateEpochMillis = dayStart, bytesUsed = bytes))
        }

        return DailyUsageStats(
            bytesUsedToday = usedToday,
            dailyCapBytes = capBytes,
            bytesLeftToday = bytesLeft,
            usageRatio = ratio,
            last7Days = history
        )
    }

    private fun queryWifiBytes(manager: NetworkStatsManager, startMillis: Long, endMillis: Long): Long {
        if (startMillis >= endMillis) return 0L
        return try {
            val bucket = manager.querySummaryForDevice(
                NetworkCapabilities.TRANSPORT_WIFI,
                null,
                startMillis,
                endMillis
            )
            val rx = bucket?.rxBytes ?: 0L
            val tx = bucket?.txBytes ?: 0L
            (rx + tx).coerceAtLeast(0L)
        } catch (_: Exception) {
            0L
        }
    }

    fun formatBytes(bytes: Long): String {
        if (bytes < 1024L) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024.0) return "%.1f KB".format(kb)
        val mb = kb / 1024.0
        if (mb < 1024.0) return "%.1f MB".format(mb)
        val gb = mb / 1024.0
        return "%.2f GB".format(gb)
    }
}
