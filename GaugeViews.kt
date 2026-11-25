package com.example.bmwe60cockpit

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

class Bmw3dLogoView(c: Context) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(cv: Canvas) {
        val cx = width / 2f; val cy = height / 2f; val r = minOf(cx, cy) - 6f
        if (r <= 10f) return
        p.style = Paint.Style.FILL
        p.setShadowLayer(12f, 0f, 6f, Color.BLACK)
        p.color = Color.parseColor("#1E293B")
        cv.drawCircle(cx, cy, r, p)
        p.clearShadowLayer()

        p.shader = LinearGradient(cx - r, cy - r, cx + r, cy + r,
            intArrayOf(Color.WHITE, Color.parseColor("#64748B"), Color.parseColor("#E2E8F0")), null, Shader.TileMode.CLAMP)
        cv.drawCircle(cx, cy, r, p)
        p.shader = null

        p.color = Color.parseColor("#090D16")
        cv.drawCircle(cx, cy, r * 0.90f, p)

        val ir = r * 0.62f
        val rc = RectF(cx - ir, cy - ir, cx + ir, cy + ir)
        p.color = Color.parseColor("#008AC9")
        cv.drawArc(rc, 180f, 90f, true, p); cv.drawArc(rc, 0f, 90f, true, p)
        p.color = Color.WHITE
        cv.drawArc(rc, 270f, 90f, true, p); cv.drawArc(rc, 90f, 90f, true, p)

        p.shader = LinearGradient(cx, cy - r, cx, cy + r * 0.2f,
            Color.parseColor("#55FFFFFF"), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        cv.drawCircle(cx, cy, r * 0.88f, p)
        p.shader = null
    }
}

class PerformanceGaugeView(
    c: Context,
    private val title: String,
    private val unit: String,
    private var minVal: Float,
    private var maxVal: Float,
    private val accentHex: String,
    private val iconType: Int // 1=ECT, 2=EOT, 3=VOLT, 4=NM, 5=KM
) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var currentVal = minVal

    fun setMaxValue(newMax: Float) {
        if (newMax > minVal) {
            maxVal = newMax
            currentVal = currentVal.coerceIn(minVal, maxVal)
            invalidate()
        }
    }

    fun setValue(v: Float) {
        currentVal = v.coerceIn(minVal, maxVal)
        invalidate()
    }

    override fun onDraw(cv: Canvas) {
        val cx = width / 2f; val cy = height / 2f
        val r = minOf(cx, cy) - 8f
        if (r <= 16f) return

        // Zewnętrzny pierścień 3D
        p.style = Paint.Style.FILL
        p.setShadowLayer(14f, 0f, 8f, Color.parseColor("#CC000000"))
        p.color = Color.parseColor("#0B0F19")
        cv.drawCircle(cx, cy, r, p)
        p.clearShadowLayer()

        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.08f
        p.shader = LinearGradient(cx - r, cy - r, cx + r, cy + r,
            intArrayOf(Color.parseColor("#94A3B8"), Color.parseColor("#1E293B"), Color.parseColor("#64748B")), null, Shader.TileMode.CLAMP)
        cv.drawCircle(cx, cy, r * 0.94f, p)
        p.shader = null

        // Tarcza z głębią 3D
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(cx, cy, r * 0.88f,
            Color.parseColor("#172033"), Color.parseColor("#070A10"), Shader.TileMode.CLAMP)
        cv.drawCircle(cx, cy, r * 0.88f, p)
        p.shader = null

        // Łuk skali (135° -> +270°, godzina 12:00 to dokładnie połowa skali = 270°)
        val arcR = r * 0.75f
        val arcRect = RectF(cx - arcR, cy - arcR, cx + arcR, cy + arcR)
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = r * 0.11f
        p.color = Color.parseColor("#1E293B")
        cv.drawArc(arcRect, 135f, 270f, false, p)

        val ratio = ((currentVal - minVal) / (maxVal - minVal)).coerceIn(0f, 1f)
        p.color = Color.parseColor(accentHex)
        cv.drawArc(arcRect, 135f, 270f * ratio, false, p)

        // Kreski podziałki (indeks 5 to dokładnie godzina 12:00)
        for (i in 0..10) {
            val ang = Math.toRadians((135.0 + i * 27.0))
            val innerFactor = if (i == 5) 0.54f else 0.58f
            p.strokeWidth = if (i == 5) 3.5f else 2f
            val x1 = cx + cos(ang).toFloat() * (r * innerFactor)
            val y1 = cy + sin(ang).toFloat() * (r * innerFactor)
            val x2 = cx + cos(ang).toFloat() * (r * 0.66f)
            val y2 = cy + sin(ang).toFloat() * (r * 0.66f)
            p.color = if (i / 10f <= ratio) Color.WHITE else Color.parseColor("#64748B")
            cv.drawLine(x1, y1, x2, y2, p)
        }

        // Wartość na godzinie 12:00 dla zegarów Nm i KM (połowa skali)
        if (iconType == 4 || iconType == 5) {
            val midVal = ((minVal + maxVal) / 2f).toInt()
            p.style = Paint.Style.FILL
            p.textAlign = Paint.Align.CENTER
            p.color = Color.parseColor("#E2E8F0")
            p.textSize = r * 0.17f
            p.typeface = Typeface.DEFAULT_BOLD
            cv.drawText(midVal.toString(), cx, cy - r * 0.36f, p)
        }

        // Symbol / nazwa wewnątrz na środku
        drawCenterSymbol(cv, cx, cy - r * 0.08f, r * 0.21f)

        // Aktualna wartość cyfrowa na dole tarczy
        p.style = Paint.Style.FILL
        p.textAlign = Paint.Align.CENTER
        p.color = Color.WHITE
        p.textSize = r * 0.29f
        p.typeface = Typeface.DEFAULT_BOLD
        val valStr = if (iconType == 3) String.format("%.1f", currentVal) else currentVal.toInt().toString()
        cv.drawText(valStr, cx, cy + r * 0.34f, p)

        p.color = Color.parseColor("#94A3B8")
        p.textSize = r * 0.15f
        cv.drawText(unit, cx, cy + r * 0.53f, p)
    }

    private fun drawCenterSymbol(cv: Canvas, cx: Float, cy: Float, s: Float) {
        p.style = Paint.Style.STROKE
        p.strokeWidth = maxOf(2.5f, s * 0.14f)
        p.color = Color.parseColor(accentHex)
        when (iconType) {
            1 -> { // Termometr (płyn chłodzący)
                cv.drawLine(cx, cy - s * 0.6f, cx, cy + s * 0.3f, p)
                p.style = Paint.Style.FILL
                cv.drawCircle(cx, cy + s * 0.4f, s * 0.25f, p)
                p.style = Paint.Style.STROKE
                cv.drawLine(cx - s * 0.5f, cy + s * 0.65f, cx + s * 0.5f, cy + s * 0.65f, p)
            }
            2 -> { // Oliwiarka (olej)
                cv.drawRect(cx - s * 0.45f, cy - s * 0.1f, cx + s * 0.25f, cy + s * 0.35f, p)
                cv.drawLine(cx + s * 0.25f, cy + s * 0.05f, cx + s * 0.65f, cy - s * 0.25f, p)
                cv.drawLine(cx - s * 0.1f, cy - s * 0.1f, cx - s * 0.1f, cy - s * 0.35f, p)
            }
            3 -> { // Akumulator (ładowanie)
                cv.drawRect(cx - s * 0.55f, cy - s * 0.25f, cx + s * 0.55f, cy + s * 0.35f, p)
                cv.drawLine(cx - s * 0.35f, cy + s * 0.05f, cx - s * 0.15f, cy + s * 0.05f, p)
                cv.drawLine(cx + s * 0.15f, cy + s * 0.05f, cx + s * 0.35f, cy + s * 0.05f, p)
                cv.drawLine(cx + s * 0.25f, cy - s * 0.05f, cx + s * 0.25f, cy + s * 0.15f, p)
            }
            else -> { // Etykieta Nm / KM na środku
                p.style = Paint.Style.FILL
                p.textSize = s * 0.85f
                p.textAlign = Paint.Align.CENTER
                cv.drawText(title, cx, cy + s * 0.25f, p)
            }
        }
    }
}
