package com.example.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.PortalStatusCode
import com.example.security.SelfSignedCertManager
import com.example.ui.components.DailyCapDialog
import com.example.ui.components.SetupCredentialsDialog
import com.example.ui.components.UsageRing
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceContainer
import com.example.ui.theme.OnAccent
import com.example.ui.theme.SoftAccent
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusOffline
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CampusWifiScreen(
    viewModel: CampusWifiViewModel,
    uiState: CampusWifiUiState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // App Header
            item {
                HeaderSection(
                    hasCredentials = uiState.hasCredentials,
                    onEditCredentials = { viewModel.openCredentialsDialog(true) }
                )
            }

            // If credentials not set up, show prominent Setup Card
            if (!uiState.hasCredentials) {
                item {
                    SetupRequiredCard(
                        onSetupClick = { viewModel.openCredentialsDialog(true) }
                    )
                }
            } else {
                // Large Auto-Login Master Switch Card
                item {
                    AutoLoginCard(
                        enabled = uiState.isAutoLoginEnabled,
                        onCheckedChange = { viewModel.setAutoLoginEnabled(it) }
                    )
                }

                // Current Status Card
                item {
                    StatusCard(
                        statusCode = uiState.statusCode,
                        statusMessage = uiState.statusMessage,
                        lastTime = uiState.lastStatusTime,
                        isOperating = uiState.isOperating
                    )
                }

                // Action Buttons Row: Log In Now & Log Out
                item {
                    ActionsRow(
                        isOperating = uiState.isOperating,
                        onLoginClick = { viewModel.loginNow() },
                        onLogoutClick = { viewModel.logout() },
                        onOpenPortal = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(SelfSignedCertManager.USER_PORTAL_URL))
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        }
                    )
                }

                // Daily Data Usage Card
                item {
                    if (uiState.hasUsagePermission && uiState.usageStats != null) {
                        UsageRing(
                            stats = uiState.usageStats,
                            dailyCapGb = uiState.dailyCapGb,
                            onAdjustCapClick = { viewModel.openCapDialog(true) }
                        )
                    } else {
                        UsageTrackingPromptCard(
                            onEnableClick = {
                                try {
                                    val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            }
                        )
                    }
                }

                // Reliability Mode Card
                item {
                    ReliabilityModeCard(
                        enabled = uiState.isReliabilityModeEnabled,
                        onCheckedChange = { viewModel.setReliabilityModeEnabled(it) }
                    )
                }

                // Recent Logs Card
                item {
                    RecentActivityCard(logs = uiState.recentLogs)
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        // Credentials Dialog
        if (uiState.showCredentialsDialog) {
            SetupCredentialsDialog(
                initialUsername = "",
                onDismiss = { viewModel.openCredentialsDialog(false) },
                onSave = { u, p -> viewModel.saveCredentials(u, p) }
            )
        }

        // Daily Cap Dialog
        if (uiState.showCapDialog) {
            DailyCapDialog(
                currentCapGb = uiState.dailyCapGb,
                onDismiss = { viewModel.openCapDialog(false) },
                onSave = { viewModel.updateDailyCap(it) }
            )
        }
    }
}

@Composable
private fun HeaderSection(
    hasCredentials: Boolean,
    onEditCredentials: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Campus WiFi",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Medium),
                color = TextPrimary
            )
            Text(
                text = "Sophos portal auto-login • 172.16.16.16",
                style = MaterialTheme.typography.bodyMedium,
                color = TextTertiary
            )
        }

        if (hasCredentials) {
            IconButton(
                onClick = onEditCredentials,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(DarkSurfaceContainer)
                    .testTag("edit_credentials_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit credentials",
                    tint = SoftAccent,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun SetupRequiredCard(
    onSetupClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(DarkSurface)
            .border(1.dp, DarkBorder, RoundedCornerShape(20.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(DarkSurfaceContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Setup credentials",
                tint = SoftAccent,
                modifier = Modifier.size(28.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Set Up Auto-Login",
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Enter your portal ID and password once. Credentials are encrypted on-device via Android Keystore and never transmitted elsewhere.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = onSetupClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = SoftAccent,
                contentColor = OnAccent
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("open_setup_button")
        ) {
            Text("Enter Credentials")
        }
    }
}

@Composable
private fun AutoLoginCard(
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(DarkSurface)
            .border(1.dp, if (enabled) SoftAccent.copy(alpha = 0.3f) else DarkBorder, RoundedCornerShape(20.dp))
            .clickable { onCheckedChange(!enabled) }
            .padding(20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Auto-Login",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (enabled) {
                    "Wakes on Wi-Fi connection • 0% background battery use"
                } else {
                    "Automatic login is paused"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) SoftAccent else TextTertiary
            )
        }

        Switch(
            checked = enabled,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = OnAccent,
                checkedTrackColor = SoftAccent,
                uncheckedThumbColor = TextTertiary,
                uncheckedTrackColor = DarkSurfaceContainer
            ),
            modifier = Modifier.testTag("auto_login_toggle")
        )
    }
}

@Composable
private fun StatusCard(
    statusCode: PortalStatusCode,
    statusMessage: String,
    lastTime: Long,
    isOperating: Boolean
) {
    val (statusLabel, statusColor) = when (statusCode) {
        PortalStatusCode.LOGGED_IN -> Pair("Logged In", StatusSuccess)
        PortalStatusCode.CHECKING -> Pair("Checking Portal", StatusWarning)
        PortalStatusCode.NOT_CAMPUS_WIFI -> Pair("Not on Campus Wi-Fi", StatusOffline)
        PortalStatusCode.FAILED -> Pair("Login Failed", StatusError)
        PortalStatusCode.IDLE -> Pair("Ready", StatusOffline)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(DarkSurface)
            .border(1.dp, DarkBorder, RoundedCornerShape(20.dp))
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = statusColor
                )
            }

            if (isOperating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = SoftAccent,
                    strokeWidth = 2.dp
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = statusMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )

        if (lastTime > 0) {
            Spacer(modifier = Modifier.height(8.dp))
            val timeStr = SimpleDateFormat("h:mm a, MMM d", Locale.getDefault()).format(Date(lastTime))
            Text(
                text = "Last updated: $timeStr",
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary
            )
        }
    }
}

@Composable
private fun ActionsRow(
    isOperating: Boolean,
    onLoginClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onOpenPortal: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Button(
            onClick = onLoginClick,
            enabled = !isOperating,
            colors = ButtonDefaults.buttonColors(
                containerColor = SoftAccent,
                contentColor = OnAccent,
                disabledContainerColor = SoftAccent.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .weight(1.3f)
                .height(48.dp)
                .testTag("login_now_button")
        ) {
            Text("Log in now", fontWeight = FontWeight.Medium)
        }

        OutlinedButton(
            onClick = onLogoutClick,
            enabled = !isOperating,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .testTag("logout_button")
        ) {
            Text("Log out")
        }

        IconButton(
            onClick = onOpenPortal,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(DarkSurfaceContainer)
                .testTag("portal_account_button")
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "Open User Portal Account",
                tint = TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun UsageTrackingPromptCard(
    onEnableClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(DarkSurfaceContainer)
            .padding(20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Wi-Fi Data Tracking",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Monitor your daily quota (13 GB default) lazily on app launch.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextTertiary
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Button(
            onClick = onEnableClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = DarkBorder,
                contentColor = SoftAccent
            ),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.testTag("enable_usage_tracking_button")
        ) {
            Text("Enable", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ReliabilityModeCard(
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(DarkSurface)
            .border(1.dp, DarkBorder, RoundedCornerShape(20.dp))
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Reliability Mode",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Runs a silent low-importance background service for devices that aggressively kill background wakeups. Uses slightly more battery.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextTertiary
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Switch(
                checked = enabled,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = OnAccent,
                    checkedTrackColor = SoftAccent,
                    uncheckedThumbColor = TextTertiary,
                    uncheckedTrackColor = DarkSurfaceContainer
                ),
                modifier = Modifier.testTag("reliability_mode_toggle")
            )
        }
    }
}

@Composable
private fun RecentActivityCard(logs: List<com.example.data.LogEntry>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(DarkSurface)
            .border(1.dp, DarkBorder, RoundedCornerShape(20.dp))
            .padding(20.dp)
    ) {
        Text(
            text = "Activity Log",
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (logs.isEmpty()) {
            Text(
                text = "No events recorded yet. Connect to campus Wi-Fi to start.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextTertiary
            )
        } else {
            val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
            logs.take(5).forEach { log ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (log.isSuccess) SoftAccent else StatusError)
                    )

                    Text(
                        text = timeFormat.format(Date(log.timestamp)),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary,
                        modifier = Modifier.width(55.dp)
                    )

                    Text(
                        text = log.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
