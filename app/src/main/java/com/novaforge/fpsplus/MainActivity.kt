package com.novaforge.fpsplus

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.app.NotificationManagerCompat
import com.novaforge.fpsplus.monitor.MonitorService
import com.novaforge.fpsplus.monitor.PreferenceStore
import com.novaforge.fpsplus.ui.FPSPlusApp
import com.novaforge.fpsplus.ui.FPSPlusTheme
import rikka.shizuku.Shizuku

 data class PermissionSnapshot(
    val overlay: Boolean,
    val background: Boolean,
    val shizuku: Boolean,
    val shizukuRunning: Boolean,
    val notifications: Boolean
) {
    val allReady: Boolean get() = overlay && background && shizuku && notifications
}

class MainActivity : ComponentActivity() {
    private val permissionState = mutableStateOf(PermissionSnapshot(false, false, false, false, false))
    private lateinit var notificationLauncher: androidx.activity.result.ActivityResultLauncher<String>

    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == SHIZUKU_REQUEST_CODE) runOnUiThread { refreshPermissions() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PreferenceStore.load(this)
        notificationLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            refreshPermissions()
        }
        try { Shizuku.addRequestPermissionResultListener(shizukuPermissionListener) } catch (_: Throwable) { }

        window.statusBarColor = android.graphics.Color.rgb(8, 12, 20)
        window.navigationBarColor = android.graphics.Color.rgb(8, 12, 20)
        refreshPermissions()

        setContent {
            FPSPlusTheme {
                FPSPlusApp(
                    permissions = permissionState.value,
                    onRequestOverlay = ::requestOverlayPermission,
                    onRequestBackground = ::requestBackgroundPermission,
                    onRequestShizuku = ::requestShizukuPermission,
                    onRequestNotifications = ::requestNotificationPermission,
                    onContinue = {
                        getSharedPreferences("fpsplus_onboarding", MODE_PRIVATE).edit().putBoolean("complete", true).apply()
                    },
                    onStartMonitor = ::startMonitor,
                    onStopMonitor = ::stopMonitor
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissions()
    }

    override fun onDestroy() {
        try { Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener) } catch (_: Throwable) { }
        super.onDestroy()
    }

    private fun refreshPermissions() {
        val overlay = Settings.canDrawOverlays(this)
        val power = getSystemService(POWER_SERVICE) as PowerManager
        val background = power.isIgnoringBatteryOptimizations(packageName)
        val shizukuRunning = try { Shizuku.pingBinder() } catch (_: Throwable) { false }
        val shizukuGranted = if (shizukuRunning) {
            try { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED } catch (_: Throwable) { false }
        } else false
        val notification = if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED &&
                NotificationManagerCompat.from(this).areNotificationsEnabled()
        } else NotificationManagerCompat.from(this).areNotificationsEnabled()
        permissionState.value = PermissionSnapshot(overlay, background, shizukuGranted, shizukuRunning, notification)
    }

    private fun requestOverlayPermission() {
        try {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        } catch (_: Throwable) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
        }
    }

    private fun requestBackgroundPermission() {
        try {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        } catch (_: Throwable) {
            try { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } catch (_: Throwable) { }
        }
    }

    private fun requestShizukuPermission() {
        val running = try { Shizuku.pingBinder() } catch (_: Throwable) { false }
        if (running) {
            try {
                if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                    refreshPermissions()
                } else {
                    Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
                }
            } catch (_: Throwable) { openShizukuAppOrGuide() }
        } else openShizukuAppOrGuide()
    }

    private fun openShizukuAppOrGuide() {
        val launch = packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
        if (launch != null) {
            startActivity(launch)
        } else {
            startActivity(Intent(Intent.ACTION_VIEW, "https://shizuku.rikka.app/download/".toUri()))
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            try {
                startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
            } catch (_: Throwable) { refreshPermissions() }
        }
    }

    private fun startMonitor() {
        if (!Settings.canDrawOverlays(this)) {
            requestOverlayPermission()
            return
        }
        val intent = Intent(this, MonitorService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopMonitor() {
        stopService(Intent(this, MonitorService::class.java))
    }

    companion object { private const val SHIZUKU_REQUEST_CODE = 4172 }
}
