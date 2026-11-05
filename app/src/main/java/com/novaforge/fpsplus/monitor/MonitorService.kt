package com.novaforge.fpsplus.monitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.PixelFormat
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import com.novaforge.fpsplus.MainActivity
import com.novaforge.fpsplus.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

class MonitorService : Service() {
    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(serviceJob + Dispatchers.Main.immediate)
    private lateinit var windowManager: WindowManager
    private var overlayView: HudOverlayView? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var monitorJob: Job? = null

    @Volatile private var frameProbe: IFrameProbe? = null
    @Volatile private var serviceBound = false
    private var userServiceArgs: Shizuku.UserServiceArgs? = null
    private var lastBindAttempt = 0L
    private var lastThermalSampleAt = 0L
    private var cachedChipTemperature: ChipTemperature? = null
    private var lastLayerListAt = 0L
    private var cachedLayerList = ""
    private var cachedLayerPackage = ""
    private var overlayDragging = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            frameProbe = IFrameProbe.Stub.asInterface(service)
            serviceBound = frameProbe != null
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            frameProbe = null
            serviceBound = false
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        PreferenceStore.load(this)
        createNotificationChannel()
        startAsForeground()
        monitorJob = scope.launch {
            while (isActive) {
                val options = PreferenceStore.options.value
                val snapshot = withContext(Dispatchers.IO) { collectSnapshot(options.refreshMs) }
                MonitorState.publish(snapshot)
                refreshOverlayVisibility()
                delay(options.refreshMs.toLong())
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startAsForeground() {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1, Intent(this, MonitorService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_fpsplus)
            .setContentTitle("FPS+ is monitoring")
            .setContentText("Live device stats and floating HUD are active")
            .setContentIntent(openIntent)
            .addAction(R.drawable.ic_stat_fpsplus, "Stop", stopIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun collectSnapshot(refreshMs: Int): MonitorSnapshot {
        val activityManager = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
        val memory = android.app.ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memory)
        val totalRam = memory.totalMem.coerceAtLeast(0L)
        val usedRam = (totalRam - memory.availMem).coerceIn(0L, totalRam)

        val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val battery = if (level >= 0 && scale > 0) ((level * 100f) / scale).toInt().coerceIn(0, 100) else -1
        val tempRaw = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE
        val batteryTemp = if (tempRaw != Int.MIN_VALUE && tempRaw > 0) tempRaw / 10f else Float.NaN

        val power = getSystemService(POWER_SERVICE) as PowerManager
        val thermal = if (Build.VERSION.SDK_INT >= 29) thermalName(power.currentThermalStatus) else "Not exposed"

        var fps: Int? = null
        var fpsStatus = "Waiting for Shizuku"
        var topPackage = "—"
        var deviceTemp = batteryTemp
        var tempSource = if (batteryTemp.isFinite()) "Battery" else "—"
        val probe = frameProbe
        if (probe == null) {
            attemptShizukuBind()
            fpsStatus = if (isShizukuAuthorized()) "Connecting to Shizuku" else "Shizuku permission required"
        } else {
            try {
                topPackage = probe.topPackage?.takeIf { it.isNotBlank() } ?: "—"
                val now = SystemClock.elapsedRealtime()
                if (lastThermalSampleAt == 0L || now - lastThermalSampleAt >= 5_000L) {
                    cachedChipTemperature = ThermalParser.parse(probe.thermalDump.orEmpty())
                    lastThermalSampleAt = now
                }
                cachedChipTemperature?.let { chipTemperature ->
                    deviceTemp = chipTemperature.celsius
                    tempSource = chipTemperature.source
                }
                if (lastLayerListAt == 0L || topPackage != cachedLayerPackage || now - lastLayerListAt >= 3_000L) {
                    cachedLayerList = probe.layerList.orEmpty()
                    cachedLayerPackage = topPackage
                    lastLayerListAt = now
                }
                val chosenLayer = selectLayer(cachedLayerList, topPackage)
                if (chosenLayer == null) {
                    fpsStatus = "No supported game surface"
                } else {
                    val latency = probe.getLatency(chosenLayer).orEmpty()
                    fps = FpsEstimator.estimate(latency)
                    fpsStatus = if (fps != null) "Live · SurfaceFlinger" else "Waiting for frames"
                }
            } catch (_: SecurityException) {
                fpsStatus = "Shizuku access unavailable"
                frameProbe = null
                serviceBound = false
            } catch (_: Throwable) {
                fpsStatus = "Frame data unavailable"
                frameProbe = null
                serviceBound = false
            }
        }

        return MonitorSnapshot(
            fps = fps,
            fpsStatus = fpsStatus,
            topPackage = topPackage,
            ramUsedBytes = usedRam,
            ramTotalBytes = totalRam,
            batteryPercent = battery,
            batteryTempC = batteryTemp,
            deviceTempC = deviceTemp,
            tempSource = tempSource,
            thermalLabel = thermal,
            updatedAt = SystemClock.elapsedRealtime(),
            isRunning = true
        )
    }

    private fun selectLayer(layerDump: String, packageName: String): String? {
        if (packageName.isBlank() || packageName == "—" || packageName == packageNameOfApp()) return null
        val candidates = layerDump.lineSequence().map { it.trim() }
            .filter { it.isNotBlank() && it.contains(packageName) }
            .toList()
        return candidates.firstOrNull { it.contains("SurfaceView", ignoreCase = true) }
            ?: candidates.firstOrNull { it.contains("BLAST", ignoreCase = true) }
            ?: candidates.firstOrNull()
    }

    private fun packageNameOfApp() = packageName

    private fun thermalName(status: Int): String = when (status) {
        PowerManager.THERMAL_STATUS_NONE -> "Cool"
        PowerManager.THERMAL_STATUS_LIGHT -> "Light"
        PowerManager.THERMAL_STATUS_MODERATE -> "Moderate"
        PowerManager.THERMAL_STATUS_SEVERE -> "Severe"
        PowerManager.THERMAL_STATUS_CRITICAL -> "Critical"
        PowerManager.THERMAL_STATUS_EMERGENCY -> "Emergency"
        PowerManager.THERMAL_STATUS_SHUTDOWN -> "Shutdown risk"
        else -> "Unknown"
    }

    private fun isShizukuAuthorized(): Boolean = try {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) { false }

    private fun attemptShizukuBind() {
        val now = SystemClock.elapsedRealtime()
        if (serviceBound || now - lastBindAttempt < 5_000L || !isShizukuAuthorized()) return
        lastBindAttempt = now
        try {
            val args = Shizuku.UserServiceArgs(ComponentName(this, FrameProbeService::class.java))
                .tag("fpsplus-frame-probe-v1")
                .daemon(false)
            userServiceArgs = args
            Shizuku.bindUserService(args, connection)
            serviceBound = true
        } catch (_: Throwable) {
            serviceBound = false
            frameProbe = null
        }
    }

    private fun refreshOverlayVisibility() {
        val options = PreferenceStore.options.value
        val shouldShow = Settings.canDrawOverlays(this) && (
            options.showFps || options.showRam || options.showBattery || options.showTemp ||
                options.showApp || options.showThermal
            )
        if (shouldShow && overlayView == null) addOverlay()
        if (!shouldShow && overlayView != null) removeOverlay()
        overlayView?.update(MonitorState.snapshot.value, options)
        if (!overlayDragging) {
            overlayParams?.let { params ->
                if (params.x != options.x || params.y != options.y) {
                    params.x = options.x
                    params.y = options.y
                    try { overlayView?.let { windowManager.updateViewLayout(it, params) } } catch (_: Throwable) { }
                }
            }
        }
    }

    private fun addOverlay() {
        if (!Settings.canDrawOverlays(this) || overlayView != null) return
        val options = PreferenceStore.options.value
        val view = HudOverlayView(this).apply {
            update(MonitorState.snapshot.value, options)
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = options.x
            y = options.y
        }

        var downX = 0f
        var downY = 0f
        var originX = params.x
        var originY = params.y
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    overlayDragging = true
                    downX = event.rawX
                    downY = event.rawY
                    originX = params.x
                    originY = params.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = originX + (event.rawX - downX).toInt()
                    params.y = originY + (event.rawY - downY).toInt()
                    try { windowManager.updateViewLayout(view, params) } catch (_: Throwable) { }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    overlayDragging = false
                    PreferenceStore.update(this) { it.copy(x = params.x, y = params.y) }
                    true
                }
                else -> true
            }
        }
        try {
            windowManager.addView(view, params)
            overlayView = view
            overlayParams = params
        } catch (_: Throwable) {
            overlayView = null
            overlayParams = null
        }
    }

    private fun removeOverlay() {
        overlayView?.let { view ->
            try { windowManager.removeView(view) } catch (_: Throwable) { }
        }
        overlayView = null
        overlayParams = null
        overlayDragging = false
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(CHANNEL_ID, "FPS+ monitoring", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Persistent notification while the FPS+ floating monitor is running"
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        monitorJob?.cancel()
        removeOverlay()
        val args = userServiceArgs
        if (args != null && serviceBound) {
            try { Shizuku.unbindUserService(args, connection, true) } catch (_: Throwable) { }
        }
        frameProbe = null
        serviceBound = false
        serviceJob.cancel()
        MonitorState.markStopped()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "fpsplus_monitor"
        const val NOTIFICATION_ID = 417
        private const val ACTION_STOP = "com.novaforge.fpsplus.action.STOP_MONITOR"
    }
}
