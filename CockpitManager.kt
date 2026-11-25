package com.example.bmwe60cockpit

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.*
import android.widget.*
import java.io.File
import java.io.FileOutputStream
import kotlin.math.sin
import kotlin.random.Random

class CockpitManager(
    private val ctx: Context,
    private val isScreensaver: Boolean,
    private val onPickPhoto: ((Int) -> Unit)? = null
) {
    val rootLayout = FrameLayout(ctx)
    private val prefs = ctx.getSharedPreferences("e60_cockpit_cfg", Context.MODE_PRIVATE)
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var bgImage: ImageView
    private lateinit var leftPanel: LinearLayout
    private lateinit var rightPanel: LinearLayout
    private lateinit var freeRightLayer: FrameLayout
    private lateinit var logoView: Bmw3dLogoView
    private lateinit var gEct: PerformanceGaugeView
    private lateinit var gEot: PerformanceGaugeView
    private lateinit var gVolt: PerformanceGaugeView
    private lateinit var gNm: PerformanceGaugeView
    private lateinit var gKm: PerformanceGaugeView
    private lateinit var leftResizeHandle: View
    private lateinit var rightResizeHandle: View

    private var editMode = false
    private var lightState = 0
    private var demoMode = false
    private var phase = 0.0

    private val beepRunnable = object : Runnable {
        override fun run() {
            val beepOn = prefs.getBoolean("beep_on", true)
            val includePos = prefs.getBoolean("beep_pos", true)
            val vol = prefs.getInt("beep_vol", 80)
            if (beepOn && (lightState == 0 || (lightState == 1 && includePos)) && vol > 0) {
                try {
                    val tg = ToneGenerator(AudioManager.STREAM_ALARM, vol.coerceIn(1, 100))
                    tg.startTone(ToneGenerator.TONE_PROP_BEEP, 160)
                    handler.postDelayed({ tg.release() }, 250)
                } catch (_: Exception) {}
            }
            handler.postDelayed(this, 10000)
        }
    }

    private val demoRunnable = object : Runnable {
        override fun run() {
            if (!demoMode) return
            phase += 0.07
            val w = (sin(phase) + 1.0).toFloat() / 2f
            val maxNm = prefs.getInt("max_nm", 400).toFloat()
            gEct.setValue(86f + w * 8f); gEot.setValue(90f + w * 14f); gVolt.setValue(13.9f + w * 0.5f)
            val nm = 50f + w * (maxNm - 50f)
            val rpm = 850f + w * 3200f + Random.nextInt(-20, 20)
            gNm.setValue(nm); gKm.setValue((nm * rpm) / 7023f)
            handler.postDelayed(this, 60)
        }
    }

    init {
        buildUI()
        applySettings()
        handler.postDelayed(beepRunnable, 3000)
    }

    fun destroy() {
        demoMode = false
        handler.removeCallbacksAndMessages(null)
    }

    fun savePhoto(slot: Int, uri: Uri) {
        try {
            val file = File(ctx.filesDir, "e60_bg_" + slot + ".jpg")
            ctx.contentResolver.openInputStream(uri)?.use { inp -> FileOutputStream(file).use { out -> inp.copyTo(out) } }
            updateBackgroundPhoto()
        } catch (_: Exception) {}
    }

    private fun updateBackgroundPhoto() {
        val file = File(ctx.filesDir, "e60_bg_" + lightState + ".jpg")
        val fallback = File(ctx.filesDir, "e60_bg_0.jpg")
        val target = if (file.exists()) file else if (fallback.exists()) fallback else null
        if (target != null) { bgImage.setImageBitmap(BitmapFactory.decodeFile(target.absolutePath)); bgImage.visibility = View.VISIBLE }
        else bgImage.visibility = View.GONE
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun buildUI() {
        rootLayout.setBackgroundColor(Color.parseColor("#05070B"))
        bgImage = ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        rootLayout.addView(bgImage, FrameLayout.LayoutParams(-1, -1))
        freeRightLayer = FrameLayout(ctx)
        rootLayout.addView(freeRightLayer, FrameLayout.LayoutParams(-1, -1))

        leftPanel = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(12, 18, 12, 18) }
        logoView = Bmw3dLogoView(ctx); leftPanel.addView(logoView)
        val leftGaugesBox = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; layoutParams = LinearLayout.LayoutParams(-1, 0, 1f) }
        gEct = PerformanceGaugeView(ctx, "ECT", "°C", 40f, 120f, "#00A3E0", 1)
        gEot = PerformanceGaugeView(ctx, "EOT", "°C", 40f, 150f, "#F59E0B", 2)
        gVolt = PerformanceGaugeView(ctx, "VOLT", "V", 10f, 16f, "#10B981", 3)
        leftGaugesBox.addView(gEct); leftGaugesBox.addView(gEot); leftGaugesBox.addView(gVolt)
        leftPanel.addView(leftGaugesBox)
        rootLayout.addView(leftPanel, FrameLayout.LayoutParams(240, -1, Gravity.START))

        leftResizeHandle = createResizeHandle { dx ->
            val w = (leftPanel.layoutParams.width + dx.toInt()).coerceIn(140, 480)
            prefs.edit().putInt("left_w", w).apply(); applySettings()
        }
        rootLayout.addView(leftResizeHandle)

        rightPanel = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(12, 18, 12, 18) }
        rootLayout.addView(rightPanel, FrameLayout.LayoutParams(260, -1, Gravity.END))
        rightResizeHandle = createResizeHandle { dx ->
            val w = (rightPanel.layoutParams.width - dx.toInt()).coerceIn(150, 520)
            prefs.edit().putInt("right_w", w).apply(); applySettings()
        }
        rootLayout.addView(rightResizeHandle)

        gNm = PerformanceGaugeView(ctx, "Nm", "Nm", 0f, 400f, "#E21B22", 4)
        gKm = PerformanceGaugeView(ctx, "KM", "KM", 0f, 200f, "#00A3E0", 5)
        setupFreeDrag(gNm, "nm_x", "nm_y"); setupFreeDrag(gKm, "km_x", "km_y")

        if (!isScreensaver) {
            val btnGear = Button(ctx).apply {
                text = "⚙"; textSize = 20f; setTextColor(Color.WHITE)
                background = GradientDrawable().apply { setColor(Color.parseColor("#990F172A")); setStroke(2, Color.parseColor("#38BDF8")); cornerRadius = 50f }
                setOnClickListener { openSettingsDialog() }
            }
            rootLayout.addView(btnGear, FrameLayout.LayoutParams(96, 96, Gravity.TOP or Gravity.END).apply { topMargin = 16; rightMargin = 18 })
        }
        gEct.setValue(89f); gEot.setValue(94f); gVolt.setValue(14.2f); gNm.setValue(120f); gKm.setValue(65f)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createResizeHandle(onDrag: (Float) -> Unit) = View(ctx).apply {
        setBackgroundColor(Color.parseColor("#AA38BDF8"))
        var lastX = 0f
        setOnTouchListener { _, ev ->
            if (!editMode) return@setOnTouchListener false
            when (ev.action) { MotionEvent.ACTION_DOWN -> lastX = ev.rawX; MotionEvent.ACTION_MOVE -> { onDrag(ev.rawX - lastX); lastX = ev.rawX } }
            true
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupFreeDrag(v: View, keyX: String, keyY: String) {
        var dX = 0f; var dY = 0f
        v.setOnTouchListener { view, ev ->
            if (!editMode || prefs.getBoolean("right_frame_mode", false)) return@setOnTouchListener false
            val minX = (prefs.getInt("left_w", 240) + 12).toFloat()
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> { dX = view.x - ev.rawX; dY = view.y - ev.rawY }
                MotionEvent.ACTION_MOVE -> {
                    view.x = (ev.rawX + dX).coerceIn(minX, (rootLayout.width - view.width).toFloat().coerceAtLeast(minX))
                    view.y = (ev.rawY + dY).coerceIn(0f, (rootLayout.height - view.height).toFloat().coerceAtLeast(0f))
                }
                MotionEvent.ACTION_UP -> prefs.edit().putFloat(keyX, view.x).putFloat(keyY, view.y).apply()
            }
            true
        }
    }

    fun applySettings() {
        updateBackgroundPhoto()
        gNm.setMaxValue(prefs.getInt("max_nm", 400).toFloat())
        gKm.setMaxValue(prefs.getInt("max_km", 200).toFloat())

        val leftW = prefs.getInt("left_w", 240)
        leftPanel.layoutParams = (leftPanel.layoutParams as FrameLayout.LayoutParams).apply { width = leftW }
        leftPanel.background = buildFrameDrawable(prefs.getInt("left_style", 1))

        val lSize = (leftW - 32).coerceIn(90, 230)
        logoView.visibility = if (prefs.getBoolean("show_logo", true)) View.VISIBLE else View.GONE
        logoView.layoutParams = LinearLayout.LayoutParams((lSize * 0.72f).toInt(), (lSize * 0.72f).toInt()).apply { bottomMargin = 10 }
        listOf(gEct to "show_ect", gEot to "show_eot", gVolt to "show_volt").forEach { (g, k) ->
            g.visibility = if (prefs.getBoolean(k, true)) View.VISIBLE else View.GONE
            g.layoutParams = LinearLayout.LayoutParams(lSize, lSize).apply { topMargin = 6; bottomMargin = 6 }
        }

        val rightFrameMode = prefs.getBoolean("right_frame_mode", false)
        val rightW = prefs.getInt("right_w", 260)
        (gNm.parent as? ViewGroup)?.removeView(gNm); (gKm.parent as? ViewGroup)?.removeView(gKm)
        gNm.visibility = if (prefs.getBoolean("show_nm", true)) View.VISIBLE else View.GONE
        gKm.visibility = if (prefs.getBoolean("show_km", true)) View.VISIBLE else View.GONE

        if (rightFrameMode) {
            rightPanel.visibility = View.VISIBLE; freeRightLayer.visibility = View.GONE
            rightPanel.layoutParams = (rightPanel.layoutParams as FrameLayout.LayoutParams).apply { width = rightW }
            rightPanel.background = buildFrameDrawable(prefs.getInt("right_style", 1))
                        val rSize = (rightW - 32).coerceIn(100, 300)
            gNm.layoutParams = LinearLayout.LayoutParams(rSize, rSize).apply { bottomMargin = 14 }
            gKm.layoutParams = LinearLayout.LayoutParams(rSize, rSize).apply { topMargin = 14 }
            rightPanel.addView(gNm); rightPanel.addView(gKm)
        } else {
            rightPanel.visibility = View.GONE; freeRightLayer.visibility = View.VISIBLE
            val freeSize = prefs.getInt("free_size", 230)
            freeRightLayer.addView(gNm, FrameLayout.LayoutParams(freeSize, freeSize))
            freeRightLayer.addView(gKm, FrameLayout.LayoutParams(freeSize, freeSize))
            gNm.x = prefs.getFloat("nm_x", (leftW + 760).toFloat()); gNm.y = prefs.getFloat("nm_y", 90f)
            gKm.x = prefs.getFloat("km_x", (leftW + 760).toFloat()); gKm.y = prefs.getFloat("km_y", 360f)
        }

        leftResizeHandle.visibility = if (editMode) View.VISIBLE else View.GONE
        leftResizeHandle.layoutParams = FrameLayout.LayoutParams(16, -1, Gravity.START).apply { leftMargin = leftW - 8 }
        rightResizeHandle.visibility = if (editMode && rightFrameMode) View.VISIBLE else View.GONE
        rightResizeHandle.layoutParams = FrameLayout.LayoutParams(16, -1, Gravity.END).apply { rightMargin = rightW - 8 }
    }

    private fun buildFrameDrawable(style: Int): GradientDrawable? = when (style) {
        1 -> GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.parseColor("#CC162033"), Color.parseColor("#D9090D16"))).apply { setStroke(3, Color.parseColor("#5538BDF8")); cornerRadius = 24f }
        2 -> GradientDrawable().apply { setColor(Color.parseColor("#B30B0F19")); setStroke(2, Color.parseColor("#475569")); cornerRadius = 16f }
        else -> null
    }

    private fun openSettingsDialog() {
        showCockpitSettingsDialog(
            ctx = ctx, prefs = prefs, isEdit = editMode, isDemo = demoMode,
            onEditToggle = { editMode = it; applySettings() },
            onDemoToggle = { demoMode = it; if (demoMode) handler.post(demoRunnable) },
            onPickPhoto = onPickPhoto,
            onTestLight = { lightState = it; updateBackgroundPhoto() },
            onRefresh = { applySettings() }
        )
    }
}

