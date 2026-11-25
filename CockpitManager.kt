package com.example.bmwe60cockpit

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
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
            val shouldBeep = beepOn && (lightState == 0 || (lightState == 1 && includePos))
            if (shouldBeep && vol > 0) {
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
            gEct.setValue(86f + w * 8f)
            gEot.setValue(90f + w * 14f)
            gVolt.setValue(13.9f + w * 0.5f)
            val nm = 50f + w * (maxNm - 50f)
            val rpm = 850f + w * 3200f + Random.nextInt(-20, 20)
            gNm.setValue(nm)
            gKm.setValue((nm * rpm) / 7023f)
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
            val file = File(ctx.filesDir, "e60_bg_$slot.jpg")
            ctx.contentResolver.openInputStream(uri)?.use { inp ->
                FileOutputStream(file).use { out -> inp.copyTo(out) }
            }
            updateBackgroundPhoto()
        } catch (_: Exception) {}
    }

    private fun updateBackgroundPhoto() {
        val file = File(ctx.filesDir, "e60_bg_$lightState.jpg")
        val fallback = File(ctx.filesDir, "e60_bg_0.jpg")
        val target = if (file.exists()) file else if (fallback.exists()) fallback else null
        if (target != null) {
            bgImage.setImageBitmap(BitmapFactory.decodeFile(target.absolutePath))
            bgImage.visibility = View.VISIBLE
        } else {
            bgImage.visibility = View.GONE
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun buildUI() {
        rootLayout.setBackgroundColor(Color.parseColor("#05070B"))
        bgImage = ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        rootLayout.addView(bgImage, FrameLayout.LayoutParams(-1, -1))

        freeRightLayer = FrameLayout(ctx)
        rootLayout.addView(freeRightLayer, FrameLayout.LayoutParams(-1, -1))

        leftPanel = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(12, 18, 12, 18)
        }
        logoView = Bmw3dLogoView(ctx)
        leftPanel.addView(logoView)

        val leftGaugesBox = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
        }
        gEct = PerformanceGaugeView(ctx, "ECT", "°C", 40f, 120f, "#00A3E0", 1)
        gEot = PerformanceGaugeView(ctx, "EOT", "°C", 40f, 150f, "#F59E0B", 2)
        gVolt = PerformanceGaugeView(ctx, "VOLT", "V", 10f, 16f, "#10B981", 3)
        leftGaugesBox.addView(gEct); leftGaugesBox.addView(gEot); leftGaugesBox.addView(gVolt)
        leftPanel.addView(leftGaugesBox)
        rootLayout.addView(leftPanel, FrameLayout.LayoutParams(240, -1, Gravity.START))

        leftResizeHandle = createResizeHandle { dx ->
            val w = (leftPanel.layoutParams.width + dx.toInt()).coerceIn(140, 480)
            prefs.edit().putInt("left_w", w).apply()
            applySettings()
        }
        rootLayout.addView(leftResizeHandle)

        rightPanel = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(12, 18, 12, 18)
        }
        rootLayout.addView(rightPanel, FrameLayout.LayoutParams(260, -1, Gravity.END))

        rightResizeHandle = createResizeHandle { dx ->
            val w = (rightPanel.layoutParams.width - dx.toInt()).coerceIn(150, 520)
            prefs.edit().putInt("right_w", w).apply()
            applySettings()
        }
        rootLayout.addView(rightResizeHandle)

        gNm = PerformanceGaugeView(ctx, "Nm", "Nm", 0f, 400f, "#E21B22", 4)
        gKm = PerformanceGaugeView(ctx, "KM", "KM", 0f, 200f, "#00A3E0", 5)
        setupFreeDrag(gNm, "nm_x", "nm_y")
        setupFreeDrag(gKm, "km_x", "km_y")

        if (!isScreensaver) {
            val btnGear = Button(ctx).apply {
                text = "⚙"
                textSize = 20f
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#990F172A"))
                    setStroke(2, Color.parseColor("#38BDF8"))
                    cornerRadius = 50f
                }
                setOnClickListener { openSettingsDialog() }
            }
            val gp = FrameLayout.LayoutParams(96, 96, Gravity.TOP or Gravity.END).apply {
                topMargin = 16; rightMargin = 18
            }
            rootLayout.addView(btnGear, gp)
        }

        gEct.setValue(89f); gEot.setValue(94f); gVolt.setValue(14.2f); gNm.setValue(120f); gKm.setValue(65f)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createResizeHandle(onDrag: (Float) -> Unit): View {
        return View(ctx).apply {
            setBackgroundColor(Color.parseColor("#AA38BDF8"))
            var lastX = 0f
            setOnTouchListener { _, ev ->
                if (!editMode) return@setOnTouchListener false
                when (ev.action) {
                    MotionEvent.ACTION_DOWN -> lastX = ev.rawX
                    MotionEvent.ACTION_MOVE -> { onDrag(ev.rawX - lastX); lastX = ev.rawX }
                }
                true
            }
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
                    val nx = (ev.rawX + dX).coerceIn(minX, (rootLayout.width - view.width).toFloat().coerceAtLeast(minX))
                    val ny = (ev.rawY + dY).coerceIn(0f, (rootLayout.height - view.height).toFloat().coerceAtLeast(0f))
                    view.x = nx; view.y = ny
                }
                MotionEvent.ACTION_UP -> prefs.edit().putFloat(keyX, view.x).putFloat(keyY, view.y).apply()
            }
            true
        }
    }

    fun applySettings() {
        updateBackgroundPhoto()

        val maxNm = prefs.getInt("max_nm", 400).toFloat()
        val maxKm = prefs.getInt("max_km", 200).toFloat()
        gNm.setMaxValue(maxNm)
        gKm.setMaxValue(maxKm)

        val leftW = prefs.getInt("left_w", 240)
        val leftStyle = prefs.getInt("left_style", 1)
        leftPanel.layoutParams = (leftPanel.layoutParams as FrameLayout.LayoutParams).apply { width = leftW }
        leftPanel.background = buildFrameDrawable(leftStyle)

        val lGaugeSize = (leftW - 32).coerceIn(90, 230)
        logoView.visibility = if (prefs.getBoolean("show_logo", true)) View.VISIBLE else View.GONE
        logoView.layoutParams = LinearLayout.LayoutParams((lGaugeSize * 0.72f).toInt(), (lGaugeSize * 0.72f).toInt()).apply { bottomMargin = 10 }

        listOf(gEct to "show_ect", gEot to "show_eot", gVolt to "show_volt").forEach { (g, k) ->
            g.visibility = if (prefs.getBoolean(k, true)) View.VISIBLE else View.GONE
        val lStyleRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("Ramka 3D", "Zwykła", "Brak ramki").forEachIndexed { idx, txt ->
            lStyleRow.addView(Button(ctx).apply { text = txt; textSize = 11f; setOnClickListener { prefs.edit().putInt("left_style", idx + 1).apply(); applySettings() } })
        }
        box.addView(lStyleRow)

        box.addView(TextView(ctx).apply { text = "\nPRAWE ZEGARY (Nm / KM) I SKALA (12:00 = POŁOWA)"; typeface = Typeface.DEFAULT_BOLD })
        addSwitch("Zegar: Niutonometry (Nm)", "show_nm", true)
        addSwitch("Zegar: Konie mechaniczne (KM)", "show_km", true)

        val scaleRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        val etMaxNm = EditText(ctx).apply { hint = "Max Nm"; inputType = InputType.TYPE_CLASS_NUMBER; setText(prefs.getInt("max_nm", 400).toString()); layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        val etMaxKm = EditText(ctx).apply { hint = "Max KM"; inputType = InputType.TYPE_CLASS_NUMBER; setText(prefs.getInt("max_km", 200).toString()); layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        val btnSaveScale = Button(ctx).apply {
            text = "USTAW"
            setOnClickListener {
                val n = etMaxNm.text.toString().toIntOrNull()?.coerceIn(100, 1500) ?: 400
                val k = etMaxKm.text.toString().toIntOrNull()?.coerceIn(50, 1000) ?: 200
                prefs.edit().putInt("max_nm", n).putInt("max_km", k).apply()
                applySettings()
                Toast.makeText(ctx, "Max: " + n + " Nm / " + k + " KM", Toast.LENGTH_SHORT).show()
            }
        }
        scaleRow.addView(etMaxNm); scaleRow.addView(etMaxKm); scaleRow.addView(btnSaveScale)
        box.addView(scaleRow)

        addSwitch("Umieść Nm i KM w prawej ramce (zamiast swobodnie)", "right_frame_mode", false)

        val rStyleRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("Prawa: 3D", "Prawa: Zwykła", "Prawa: Brak").forEachIndexed { idx, txt ->
            rStyleRow.addView(Button(ctx).apply { text = txt; textSize = 11f; setOnClickListener { prefs.edit().putInt("right_style", idx + 1).apply(); applySettings() } })
        }
        box.addView(rStyleRow)

        box.addView(TextView(ctx).apply { text = "Wielkość zegarów Nm/KM (tryb swobodny):" })
        box.addView(SeekBar(ctx).apply {
            max = 350; progress = prefs.getInt("free_size", 230)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) { prefs.edit().putInt("free_size", p.coerceAtLeast(110)).apply(); applySettings() }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        })

        scroll.addView(box)
        android.app.AlertDialog.Builder(ctx).setTitle("Ustawienia Kokpitu E60").setView(scroll).setPositiveButton("GOTOWE", null).show()
    }
}
