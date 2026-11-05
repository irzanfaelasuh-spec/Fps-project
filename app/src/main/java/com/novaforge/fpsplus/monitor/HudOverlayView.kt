package com.novaforge.fpsplus.monitor

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

/** Native Android view for the system overlay window. It does not depend on an Activity lifecycle. */
class HudOverlayView(context: Context) : LinearLayout(context) {
    private var renderedOptions: HudOptions? = null
    private val valueViews = LinkedHashMap<String, TextView>()
    private var fpsView: TextView? = null

    init {
        orientation = VERTICAL
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
        elevation = dp(12f).toFloat()
        clipToPadding = false
        clipChildren = false
    }

    fun update(snapshot: MonitorSnapshot, options: HudOptions) {
        if (renderedOptions != options.copy(x = renderedOptions?.x ?: options.x, y = renderedOptions?.y ?: options.y)) {
            rebuild(options)
        }
        alpha = options.opacity
        updateValues(snapshot, options)
    }

    private fun rebuild(options: HudOptions) {
        renderedOptions = options
        valueViews.clear()
        fpsView = null
        removeAllViews()

        val scale = options.scale.coerceIn(0.75f, 1.45f)
        val padH = (if (options.compact) 10f else 13f) * scale
        val padV = (if (options.compact) 7f else 10f) * scale
        val panel = GradientDrawable().apply {
            setColor(Color.argb(235, 11, 20, 32))
            cornerRadius = dp((if (options.compact) 11f else 15f) * scale).toFloat()
            if (options.showBorder) setStroke(dp(1f), Color.argb(145, 97, 230, 242))
        }
        background = panel
        setPadding(dp(padH), dp(padV), dp(padH), dp(padV))

        if (options.showFps) {
            val fpsRow = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            fpsRow.addView(label("FPS:", 10f * scale), LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
            val value = TextView(context).apply {
                text = "0"
                setTextColor(Color.WHITE)
                textSize = (if (options.compact) 17f else 20f) * scale
                typeface = Typeface.create("sans-serif", Typeface.BOLD)
                includeFontPadding = false
                setPadding(dp(5f * scale), 0, 0, 0)
            }
            fpsView = value
            fpsRow.addView(value, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
            val spacer = View(context)
            fpsRow.addView(spacer, LayoutParams(0, 1, 1f))
            val dot = View(context).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.rgb(245, 198, 106))
                }
            }
            fpsRow.addView(dot, LayoutParams(dp(5f * scale), dp(5f * scale)))
            addView(fpsRow, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        }

        val hasDetails = options.showRam || options.showBattery || options.showTemp || options.showThermal || options.showApp
        if (options.showFps && hasDetails) {
            val divider = View(context).apply { setBackgroundColor(Color.argb(34, 255, 255, 255)) }
            val dividerParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(1f))
            dividerParams.topMargin = dp(6f * scale)
            dividerParams.bottomMargin = dp(5f * scale)
            addView(divider, dividerParams)
        }

        if (options.showRam) addDetail("RAM", "ram", scale)
        if (options.showBattery) addDetail("BAT", "battery", scale)
        if (options.showTemp) addDetail("TEMP", "temp", scale)
        if (options.showThermal) addDetail("THERMAL", "thermal", scale)
        if (options.showApp) addDetail("APP", "app", scale)
        updateValues(MonitorSnapshot(), options)
    }

    private fun addDetail(label: String, key: String, scale: Float) {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2f * scale), 0, dp(2f * scale))
        }
        row.addView(label(label, 8f * scale), LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        val value = TextView(context).apply {
            text = "—"
            setTextColor(Color.rgb(234, 245, 250))
            textSize = 9f * scale
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            includeFontPadding = false
            maxLines = 1
            setPadding(dp(8f * scale), 0, 0, 0)
        }
        valueViews[key] = value
        row.addView(value, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        addView(row, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
    }

    private fun label(text: String, sizeSp: Float) = TextView(context).apply {
        this.text = text
        setTextColor(Color.rgb(139, 155, 174))
        textSize = sizeSp
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        includeFontPadding = false
        letterSpacing = 0.04f
    }

    private fun updateValues(snapshot: MonitorSnapshot, options: HudOptions) {
        fpsView?.let { view ->
            view.text = snapshot.fps?.toString() ?: "0"
            view.setTextColor(fpsColor(snapshot.fps))
        }
        valueViews["ram"]?.text = if (snapshot.ramTotalBytes > 0) "${gb(snapshot.ramUsedBytes)} / ${gb(snapshot.ramTotalBytes)} GB" else "—"
        valueViews["battery"]?.text = if (snapshot.batteryPercent >= 0) "${snapshot.batteryPercent}%" else "—"
        valueViews["temp"]?.text = if (snapshot.deviceTempC.isFinite()) "${snapshot.deviceTempC}°C ${snapshot.tempSource}" else "—"
        valueViews["thermal"]?.text = snapshot.thermalLabel
        valueViews["app"]?.text = snapshot.topPackage.substringAfterLast('.').take(18)
    }

    private fun fpsColor(fps: Int?): Int = when {
        fps == null -> Color.rgb(139, 155, 174)
        fps in 5..10 -> Color.rgb(255, 79, 103)
        fps in 11..20 -> Color.rgb(255, 155, 69)
        fps in 21..40 -> Color.rgb(255, 212, 94)
        fps in 41..50 -> Color.rgb(85, 217, 154)
        fps >= 51 -> Color.rgb(7, 155, 104)
        else -> Color.rgb(139, 155, 174)
    }

    private fun gb(value: Long) = String.format(java.util.Locale.US, "%.1f", value / 1_000_000_000.0)
    private fun dp(value: Float) = (value * resources.displayMetrics.density).roundToInt()
}
