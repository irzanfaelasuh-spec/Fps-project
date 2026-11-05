package com.novaforge.fpsplus.monitor

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Snapshot of values that Android exposes to an ordinary app or to the Shizuku shell service. */
data class MonitorSnapshot(
    val fps: Int? = null,
    val fpsStatus: String = "Waiting for a supported surface",
    val topPackage: String = "—",
    val ramUsedBytes: Long = 0L,
    val ramTotalBytes: Long = 0L,
    val batteryPercent: Int = -1,
    val batteryTempC: Float = Float.NaN,
    val deviceTempC: Float = Float.NaN,
    val tempSource: String = "—",
    val thermalLabel: String = "—",
    val updatedAt: Long = 0L,
    val isRunning: Boolean = false
)

data class HudOptions(
    val showFps: Boolean = true,
    val showRam: Boolean = false,
    val showBattery: Boolean = false,
    val showTemp: Boolean = false,
    val showApp: Boolean = false,
    val showThermal: Boolean = false,
    val compact: Boolean = false,
    val showBorder: Boolean = true,
    val scale: Float = 1.0f,
    val opacity: Float = 0.94f,
    val refreshMs: Int = 1000,
    val x: Int = 24,
    val y: Int = 120
)

object MonitorState {
    private val _snapshot = MutableStateFlow(MonitorSnapshot())
    private val _history = MutableStateFlow<List<Int>>(emptyList())
    val snapshot = _snapshot.asStateFlow()
    val history = _history.asStateFlow()

    fun publish(value: MonitorSnapshot) {
        _snapshot.value = value
        value.fps?.let { fps ->
            _history.value = (_history.value + fps).takeLast(36)
        }
    }

    fun markStopped() {
        _snapshot.value = _snapshot.value.copy(isRunning = false, fps = null, fpsStatus = "Monitor stopped")
    }
}

object PreferenceStore {
    private const val FILE = "fpsplus_preferences"
    private val _options = MutableStateFlow(HudOptions())
    val options = _options.asStateFlow()

    fun load(context: Context): HudOptions {
        val p = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        return HudOptions(
            showFps = p.getBoolean("show_fps", true),
            showRam = p.getBoolean("show_ram", false),
            showBattery = p.getBoolean("show_battery", false),
            showTemp = p.getBoolean("show_temp", false),
            showApp = p.getBoolean("show_app", false),
            showThermal = p.getBoolean("show_thermal", false),
            compact = p.getBoolean("compact", false),
            showBorder = p.getBoolean("show_border", true),
            scale = p.getFloat("scale", 1.0f).coerceIn(0.75f, 1.45f),
            opacity = p.getFloat("opacity", 0.94f).coerceIn(0.45f, 1.0f),
            refreshMs = p.getInt("refresh_ms", 1000).coerceIn(500, 3000),
            x = p.getInt("overlay_x", 24),
            y = p.getInt("overlay_y", 120)
        ).also { _options.value = it }
    }

    fun update(context: Context, transform: (HudOptions) -> HudOptions) {
        val next = transform(_options.value)
        _options.value = next
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putBoolean("show_fps", next.showFps)
            .putBoolean("show_ram", next.showRam)
            .putBoolean("show_battery", next.showBattery)
            .putBoolean("show_temp", next.showTemp)
            .putBoolean("show_app", next.showApp)
            .putBoolean("show_thermal", next.showThermal)
            .putBoolean("compact", next.compact)
            .putBoolean("show_border", next.showBorder)
            .putFloat("scale", next.scale)
            .putFloat("opacity", next.opacity)
            .putInt("refresh_ms", next.refreshMs)
            .putInt("overlay_x", next.x)
            .putInt("overlay_y", next.y)
            .apply()
    }
}
