package jp.mushamusha.utilitylight

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

class LevelActivity : CompactActivity(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private lateinit var text: TextView
    private var fx = 0f
    private var fy = 0f
    private var fz = 9.8f
    private var initialized = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = verticalScreen()
        root.addView(titleView("水平器・傾斜角"))
        text = bodyView("測定中…", 30f).apply { gravity = 17 }
        root.addView(text)
        root.addView(bodyView("スマホを平らに置くと水平器として、面に沿わせると傾斜角の目安として使えます。", 13f))
        setContentView(root)
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
    }

    override fun onResume() {
        super.onResume()
        initialized = false
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (sensor != null) sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        else text.text = "加速度センサーがありません"
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onSensorChanged(event: SensorEvent) {
        val alpha = 0.18f
        if (!initialized) {
            fx = event.values[0]; fy = event.values[1]; fz = event.values[2]; initialized = true
        } else {
            fx += alpha * (event.values[0] - fx)
            fy += alpha * (event.values[1] - fy)
            fz += alpha * (event.values[2] - fz)
        }

        val vertical = abs(fz).coerceAtLeast(0.0001f)
        val leftRight = Math.toDegrees(atan2(fx.toDouble(), vertical.toDouble()))
        val frontBack = Math.toDegrees(atan2(fy.toDouble(), vertical.toDouble()))
        val inclination = Math.toDegrees(atan2(sqrt((fx * fx + fy * fy).toDouble()), abs(fz).toDouble()))
        val level = abs(leftRight) < 1.0 && abs(frontBack) < 1.0

        text.text = buildString {
            append("左右  %.1f°\n".format(leftRight))
            append("前後  %.1f°\n".format(frontBack))
            append("傾斜角  %.1f°\n\n".format(inclination))
            append(if (level) "✓ ほぼ水平" else "角度を確認してください")
        }
    }
}
