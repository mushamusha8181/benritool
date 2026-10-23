package jp.mushamusha.utilitylight

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

class CoinCalibrationView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = context.dp(2).toFloat()
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.LTGRAY
        textSize = context.dp(13).toFloat()
        textAlign = Paint.Align.CENTER
    }
    var calibration: Float = 1f
        set(value) { field = value; invalidate() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val xdpi = resources.displayMetrics.xdpi.takeIf { it > 50f } ?: 160f
        val diameterPx = (xdpi / 25.4f) * 23.5f * calibration
        val maxDiameter = (width.coerceAtMost(height) - context.dp(8)).coerceAtLeast(context.dp(20)).toFloat()
        val d = diameterPx.coerceAtMost(maxDiameter)
        val radius = d / 2f
        val cx = width / 2f
        val cy = height / 2f - context.dp(8)
        canvas.drawCircle(cx, cy, radius, paint)
        canvas.drawText("10円硬貨 直径23.5mm", cx, height - context.dp(6).toFloat(), label)
    }
}
