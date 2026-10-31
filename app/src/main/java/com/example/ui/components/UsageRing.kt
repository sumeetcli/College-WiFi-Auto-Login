package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.DailyUsageStats
import com.example.data.DataUsageTracker
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurfaceContainer
import com.example.ui.theme.SoftAccent
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary

@Composable
fun UsageRing(
    stats: DailyUsageStats,
    dailyCapGb: Float,
    onAdjustCapClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val usedFormatted = DataUsageTracker.formatBytes(stats.bytesUsedToday)
    val leftFormatted = DataUsageTracker.formatBytes(stats.bytesLeftToday)
    val capFormatted = "%.1f GB".format(dailyCapGb)
    val ratio = stats.usageRatio

    val arcColor = when {
        ratio >= 1.0f -> StatusError
        ratio >= 0.85f -> StatusWarning
        else -> SoftAccent
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(DarkSurfaceContainer)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Daily Wi-Fi Data",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Text(
                    text = "Resets tonight at 12:00 AM",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkBorder)
                    .clickable(onClick = onAdjustCapClick)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .testTag("adjust_cap_button")
            ) {
                Text(
                    text = "Cap: $capFormatted",
                    style = MaterialTheme.typography.labelLarge,
                    color = SoftAccent
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Minimalist circular ring
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(170.dp)
        ) {
            Canvas(modifier = Modifier.size(170.dp)) {
                val strokeWidth = 12.dp.toPx()
                val diameter = size.minDimension - strokeWidth
                val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)
                val arcSize = Size(diameter, diameter)

                // Background track
                drawArc(
                    color = Color(0xFF26262D),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                // Progress arc
                val sweep = (ratio * 360f).coerceIn(0f, 360f)
                if (sweep > 0f) {
                    drawArc(
                        color = arcColor,
                        startAngle = -90f,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = usedFormatted,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Light,
                        fontSize = 24.sp
                    ),
                    color = TextPrimary
                )
                Text(
                    text = "of $capFormatted",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (stats.bytesLeftToday > 0) "$leftFormatted left" else "Cap reached",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = if (stats.bytesLeftToday > 0) SoftAccent else StatusError,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Past 7 days mini bar chart
        if (stats.last7Days.isNotEmpty()) {
            Text(
                text = "Last 7 Days Usage",
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            )

            val maxBytes = stats.last7Days.maxOfOrNull { it.bytesUsed }?.coerceAtLeast(1024L * 1024L) ?: (1L)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                stats.last7Days.forEach { day ->
                    val barRatio = (day.bytesUsed.toFloat() / maxBytes.toFloat()).coerceIn(0.08f, 1f)
                    val isToday = day.dayLabel == "Today"

                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.55f)
                                .fillMaxHeight(barRatio)
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(if (isToday) SoftAccent else Color(0xFF33333C))
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = day.dayLabel.take(3),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = if (isToday) TextPrimary else TextTertiary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
