package jp.mushamusha.utilitylight

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.view.Surface
import android.view.animation.Animation
import android.view.animation.RotateAnimation
import android.widget.TextView
import kotlin.math.roundToInt

class CompassActivity : CompactActivity(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private lateinit var value: TextView
    private lateinit var arrow: TextView
    private lateinit var accuracy: TextView
    private var lastAngle = 0f
    private var rotationVector: Sensor? = null
    private var accelerometer: Sensor? = null
    private var magnetometer: Sensor? = null
    private var gravityValues: FloatArray? = null
    private var magneticValues: FloatArray? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = verticalScreen()
        root.addView(titleView("コンパス（磁北）"))
        arrow = bodyView("↑", 108f).apply { gravity = 17 }
        value = bodyView("方位を取得中…", 26f).apply { gravity = 17 }
        accuracy = bodyView("センサー精度: 確認中", 12f).apply { gravity = 17 }
        root.addView(arrow)
        root.addView(value)
        root.addView(accuracy)
        root.addView(bodyView("表示は磁北基準です。磁石・金属・磁気付きケースの近くでは誤差が増えます。精度が悪い時はスマホを8の字にゆっくり動かしてください。", 13f))
        setContentView(root)
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        rotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (rotationVector == null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        }
    }

    override fun onResume() {
        super.onResume()
        val rv = rotationVector
        if (rv != null) {
            sensorManager.registerListener(this, rv, SensorManager.SENSOR_DELAY_UI)
            return
        }
        val accel = accelerometer
        val magnet = magnetometer
        if (accel != null && magnet != null) {
            sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_UI)
            sensorManager.registerListener(this, magnet, SensorManager.SENSOR_DELAY_UI)
        } else {
            value.text = "コンパスに必要なセンサーがありません"
        }
    }

    override fun onPause() {
        sensorManager.unregisterListener(this)
        super.onPause()
    }

    override fun onAccuracyChanged(sensor: Sensor?, sensorAccuracy: Int) {
        if (sensor?.type != Sensor.TYPE_MAGNETIC_FIELD && rotationVector == null) return
        accuracy.text = "センサー精度: " + when (sensorAccuracy) {
            SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> "高"
            SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "中"
            SensorManager.SENSOR_STATUS_ACCURACY_LOW -> "低（校正推奨）"
            SensorManager.SENSOR_STATUS_UNRELIABLE -> "不安定（校正してください）"
            else -> "確認中"
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        val matrix = FloatArray(9)
        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(matrix, event.values)
        } else {
            when (event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> gravityValues = event.values.clone()
                Sensor.TYPE_MAGNETIC_FIELD -> magneticValues = event.values.clone()
            }
            val gravity = gravityValues ?: return
            val magnetic = magneticValues ?: return
            if (!SensorManager.getRotationMatrix(matrix, null, gravity, magnetic)) return
        }

        val adjusted = remapForDisplay(matrix)
        val orientation = FloatArray(3)
        SensorManager.getOrientation(adjusted, orientation)
        var degrees = Math.toDegrees(orientation[0].toDouble()).toFloat()
        if (degrees < 0f) degrees += 360f
        renderHeading(degrees)
    }

    private fun remapForDisplay(matrix: FloatArray): FloatArray {
        val output = FloatArray(9)
        val rotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display?.rotation ?: Surface.ROTATION_0
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.rotation
        }
        val (xAxis, yAxis) = when (rotation) {
            Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
            Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
            Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
            else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
        }
        return if (SensorManager.remapCoordinateSystem(matrix, xAxis, yAxis, output)) output else matrix
    }

    private fun renderHeading(degrees: Float) {
        val direction = when {
            degrees >= 337.5f || degrees < 22.5f -> "北"
            degrees < 67.5f -> "北東"
            degrees < 112.5f -> "東"
            degrees < 157.5f -> "南東"
            degrees < 202.5f -> "南"
            degrees < 247.5f -> "南西"
            degrees < 292.5f -> "西"
            else -> "北西"
        }
        value.text = "${degrees.roundToInt()}°  $direction"

        val target = -degrees
        val delta = ((target - lastAngle + 540f) % 360f) - 180f
        val nextAngle = lastAngle + delta
        val animation = RotateAnimation(
            lastAngle,
            nextAngle,
            Animation.RELATIVE_TO_SELF,
            .5f,
            Animation.RELATIVE_TO_SELF,
            .5f
        ).apply {
            duration = 120
            fillAfter = true
        }
        arrow.startAnimation(animation)
        lastAngle = nextAngle
    }
}
