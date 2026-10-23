package jp.mushamusha.utilitylight

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.widget.TextView
import kotlin.math.sqrt

class MagneticMeterActivity : CompactActivity(), SensorEventListener {
    private lateinit var sm: SensorManager
    private lateinit var value: TextView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = verticalScreen()
        root.addView(titleView("磁気メーター"))
        value = bodyView("磁気センサーを確認中…", 32f).apply { gravity = 17 }
        root.addView(value, android.widget.LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(bodyView("磁場の強さをμTで表示します。金属探知は端末や対象物によって反応が大きく異なるため、目安として利用してください。", 13f))
        setContentView(root)
        sm = getSystemService(SENSOR_SERVICE) as SensorManager
    }
    override fun onResume() {
        super.onResume()
        val sensor = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        if (sensor == null) value.text = "この端末には磁気センサーがありません"
        else sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
    }
    override fun onPause() { sm.unregisterListener(this); super.onPause() }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    override fun onSensorChanged(event: SensorEvent) {
        val x = event.values[0]; val y = event.values[1]; val z = event.values[2]
        val uT = sqrt(x*x + y*y + z*z)
        value.text = String.format("%.1f μT", uT)
    }
}
