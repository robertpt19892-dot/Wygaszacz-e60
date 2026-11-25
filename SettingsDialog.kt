package com.example.bmwe60cockpit

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.provider.Settings
import android.text.InputType
import android.widget.*

fun showCockpitSettingsDialog(
    ctx: Context,
    prefs: SharedPreferences,
    isEdit: Boolean,
    isDemo: Boolean,
    onEditToggle: (Boolean) -> Unit,
    onDemoToggle: (Boolean) -> Unit,
    onPickPhoto: ((Int) -> Unit)?,
    onTestLight: (Int) -> Unit,
    onRefresh: () -> Unit
) {
    val scroll = ScrollView(ctx)
    val box = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(36, 28, 36, 28) }

    box.addView(Button(ctx).apply {
        text = "🖥 AKTYWUJ / USTAW WYGASZACZ EKRANU"
        setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
        setBackgroundColor(Color.parseColor("#0284C7")); setPadding(20, 22, 20, 22)
        setOnClickListener {
            try { ctx.startActivity(Intent(Settings.ACTION_DREAM_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            catch (_: Exception) { ctx.startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    })

    fun addSwitch(label: String, key: String, def: Boolean) {
        box.addView(Switch(ctx).apply {
            text = label; isChecked = prefs.getBoolean(key, def); textSize = 15f; setPadding(0, 12, 0, 12)
            setOnCheckedChangeListener { _, chk -> prefs.edit().putBoolean(key, chk).apply(); onRefresh() }
        })
    }

    box.addView(Switch(ctx).apply {
        text = "TRYB EDYCJI UKŁADU (Rozciąganie ramek / Przesuwanie)"
        isChecked = isEdit; setTextColor(Color.parseColor("#0284C7"))
        setOnCheckedChangeListener { _, chk -> onEditToggle(chk) }
    })

    box.addView(Switch(ctx).apply {
        text = "Symulacja jazdy (Test zegarów 3D)"
        isChecked = isDemo
        setOnCheckedChangeListener { _, chk -> onDemoToggle(chk) }
    })

    box.addView(TextView(ctx).apply { text = "\nZDJĘCIA TŁA (3 STANY ŚWIATEŁ CAN)"; typeface = Typeface.DEFAULT_BOLD })
    val photoRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
    listOf("1: WYŁ", "2: POSTOJOWE", "3: MIJANIA").forEachIndexed { idx, txt ->
        photoRow.addView(Button(ctx).apply { text = txt; textSize = 11f; setOnClickListener { onPickPhoto?.invoke(idx) } })
    }
    box.addView(photoRow)

    val testRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
    listOf("Test: WYŁ", "Test: Postojowe", "Test: Mijania").forEachIndexed { idx, txt ->
        testRow.addView(Button(ctx).apply { text = txt; textSize = 11f; setOnClickListener { onTestLight(idx) } })
    }
    box.addView(testRow)

    box.addView(TextView(ctx).apply { text = "\nSYGNAŁ ŚWIATEŁ (PIKANIE CO 10s)"; typeface = Typeface.DEFAULT_BOLD })
    addSwitch("Aktywuj cykliczny sygnał (co 10s)", "beep_on", true)
    addSwitch("Pikaj również na samych postojowych", "beep_pos", true)
    box.addView(TextView(ctx).apply { text = "Głośność pikania:" })
    box.addView(SeekBar(ctx).apply {
        max = 100; progress = prefs.getInt("beep_vol", 80)
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) { prefs.edit().putInt("beep_vol", p).apply() }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
    })

    box.addView(TextView(ctx).apply { text = "\nLEWY PANEL I ZEGARY"; typeface = Typeface.DEFAULT_BOLD })
    addSwitch("Znaczek BMW 3D na górze", "show_logo", true)
    addSwitch("Zegar: Temperatura płynu (ECT)", "show_ect", true)
    addSwitch("Zegar: Temperatura oleju (EOT)", "show_eot", true)
    addSwitch("Zegar: Napięcie ładowania (V)", "show_volt", true)

    val lStyleRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
    listOf("Ramka 3D", "Zwykła", "Brak ramki").forEachIndexed { idx, txt ->
        lStyleRow.addView(Button(ctx).apply { text = txt; textSize = 11f; setOnClickListener { prefs.edit().putInt("left_style", idx + 1).apply(); onRefresh() } })
    }
    box.addView(lStyleRow)

    box.addView(TextView(ctx).apply { text = "\nPRAWE ZEGARY (Nm / KM) I SKALA (12:00 = POŁOWA)"; typeface = Typeface.DEFAULT_BOLD })
    addSwitch("Zegar: Niutonometry (Nm)", "show_nm", true)
    addSwitch("Zegar: Konie mechaniczne (KM)", "show_km", true)

    val scaleRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
    val etMaxNm = EditText(ctx).apply { hint = "Max Nm"; inputType = InputType.TYPE_CLASS_NUMBER; setText(prefs.getInt("max_nm", 400).toString()); layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
    val etMaxKm = EditText(ctx).apply { hint = "Max KM"; inputType = InputType.TYPE_CLASS_NUMBER; setText(prefs.getInt("max_km", 200).toString()); layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
    scaleRow.addView(etMaxNm); scaleRow.addView(etMaxKm)
    scaleRow.addView(Button(ctx).apply {
        text = "USTAW"
        setOnClickListener {
            val n = etMaxNm.text.toString().toIntOrNull()?.coerceIn(100, 1500) ?: 400
            val k = etMaxKm.text.toString().toIntOrNull()?.coerceIn(50, 1000) ?: 200
            prefs.edit().putInt("max_nm", n).putInt("max_km", k).apply(); onRefresh()
            Toast.makeText(ctx, "Max: " + n + " Nm / " + k + " KM", Toast.LENGTH_SHORT).show()
        }
    })
    box.addView(scaleRow)

    addSwitch("Umieść Nm i KM w prawej ramce (zamiast swobodnie)", "right_frame_mode", false)
    val rStyleRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
    listOf("Prawa: 3D", "Prawa: Zwykła", "Prawa: Brak").forEachIndexed { idx, txt ->
        rStyleRow.addView(Button(ctx).apply { text = txt; textSize = 11f; setOnClickListener { prefs.edit().putInt("right_style", idx + 1).apply(); onRefresh() } })
    }
    box.addView(rStyleRow)

    box.addView(TextView(ctx).apply { text = "Wielkość zegarów Nm/KM (tryb swobodny):" })
    box.addView(SeekBar(ctx).apply {
        max = 350; progress = prefs.getInt("free_size", 230)
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) { prefs.edit().putInt("free_size", p.coerceAtLeast(110)).apply(); onRefresh() }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
    })

    scroll.addView(box)
    AlertDialog.Builder(ctx).setTitle("Ustawienia Kokpitu E60").setView(scroll).setPositiveButton("GOTOWE", null).show()
}
