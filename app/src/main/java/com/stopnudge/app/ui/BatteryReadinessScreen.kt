package com.stopnudge.app.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

@Composable
fun BatteryReadinessScreen(viewModel: StopNudgeViewModel) {
    val context = LocalContext.current
    val isIgnoringBattery by viewModel.isIgnoringBatteryOptimizations.collectAsState()

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Reliability & Battery Readiness",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Aggressive Android battery managers will kill background tracking if not configured.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Readiness Scorecard
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "System Health Checklist",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(12.dp))

                ChecklistItem(
                    title = "Precise Location Permission",
                    description = "Required for accurate distance tracking to bus/metro stops",
                    isGranted = hasLocationPermission
                )

                Spacer(modifier = Modifier.height(8.dp))

                ChecklistItem(
                    title = "Notification Permission",
                    description = "Required to run foreground tracking service and wake-up alarm",
                    isGranted = hasNotificationPermission
                )

                Spacer(modifier = Modifier.height(8.dp))

                ChecklistItem(
                    title = "Battery Optimization Exemption",
                    description = "Prevents OS from killing the GPS tracker when screen turns off",
                    isGranted = isIgnoringBattery
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Direct Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = { requestIgnoreBatteryOptimizations(context) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Allow Battery", fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = { openAppSettings(context) },
                modifier = Modifier.weight(1f)
            ) {
                Text("App Permissions")
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // OEM-Specific Tips Card
        Text(
            text = "Manufacturer-Specific Settings",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        OemGuideCard(
            oemName = "Xiaomi / Redmi / POCO (MIUI / HyperOS)",
            steps = "1. Settings > Apps > Manage Apps > StopNudge > Enable 'Autostart'\n2. Battery saver > Choose 'No restrictions'"
        )

        Spacer(modifier = Modifier.height(8.dp))

        OemGuideCard(
            oemName = "Samsung (One UI)",
            steps = "1. Settings > Battery and device care > Battery\n2. Background usage limits > 'Never sleeping apps' > Add StopNudge"
        )

        Spacer(modifier = Modifier.height(8.dp))

        OemGuideCard(
            oemName = "OnePlus / Realme / Oppo (OxygenOS / ColorOS)",
            steps = "1. Long-press StopNudge > App info > Battery usage\n2. Enable 'Allow background activity' and 'Allow auto-launch'"
        )

        Spacer(modifier = Modifier.height(8.dp))

        OemGuideCard(
            oemName = "Vivo / iQOO (Funtouch OS)",
            steps = "1. Settings > Battery > Background power consumption management\n2. Locate StopNudge > Select 'Allow high background power consumption'"
        )
    }
}

@Composable
fun ChecklistItem(title: String, description: String, isGranted: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (isGranted) Color(0xFF2E7D32) else Color(0xFFC62828)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isGranted) Icons.Default.Check else Icons.Default.Close,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
    }
}

@Composable
fun OemGuideCard(oemName: String, steps: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(oemName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(steps, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@SuppressLint("BatteryLife")
private fun requestIgnoreBatteryOptimizations(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        openAppSettings(context)
    }
}

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.parse("package:${context.packageName}")
    }
    context.startActivity(intent)
}
