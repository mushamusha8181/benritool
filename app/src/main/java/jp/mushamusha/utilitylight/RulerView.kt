package jp.mushamusha.utilitylight

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class RulerView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        strokeWidth = context.dp(1).toFloat().coerceAtLeast(1f)
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = context.dp(12).toFloat()
    }

    var calibration: Float = 1f
        set(value) {
            field = value.coerceIn(.5f, 1.5f)
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.WHITE)
        val xdpi = resources.displayMetrics.xdpi.takeIf { it.isFinite() && it > 50f } ?: 160f
        val pxPerMm = xdpi / 25.4f * calibration
        if (!pxPerMm.isFinite() || pxPerMm <= 0f) return
        val maxMm = (width / pxPerMm).toInt().coerceAtLeast(0) + 1
        val labelOffset = context.dp(14).toFloat()
        for (mm in 0..maxMm) {
            val x = mm * pxPerMm
            val heightPx = when {
                mm % 10 == 0 -> height * .28f
                mm % 5 == 0 -> height * .18f
                else -> height * .10f
            }
            canvas.drawLine(x, 0f, x, heightPx, line)
            if (mm % 10 == 0) canvas.drawText("${mm / 10}", x + context.dp(2), heightPx + labelOffset, text)
        }
        canvas.drawText("cm", context.dp(6).toFloat(), height - context.dp(8).toFloat(), text)
    }
}
