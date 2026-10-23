package jp.mushamusha.utilitylight

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

class SystemCheckActivity : CompactActivity() {
    private lateinit var status: TextView
    private val cameraAsk = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        refresh()
        if (!granted) Toast.makeText(this, "未許可の場合はAndroidのアプリ設定から変更できます", Toast.LENGTH_LONG).show()
    }
    private val notificationAsk = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        refresh()
        if (!granted) Toast.makeText(this, "未許可の場合はAndroidの通知設定から変更できます", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TimerAlarmReceiver.ensureChannel(this)
        val root = verticalScreen()
        root.addView(titleView("権限・端末確認"))
        status = bodyView("", 14f)
        root.addView(status)
        root.addView(actionButton("カメラ権限を確認 / 許可").apply {
            setOnClickListener {
                if (CameraSupport.hasAnyCamera(this@SystemCheckActivity)) cameraAsk.launch(Manifest.permission.CAMERA)
                else Toast.makeText(this@SystemCheckActivity, "この端末にはカメラがありません", Toast.LENGTH_SHORT).show()
            }
        })
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            root.addView(actionButton("通知権限を確認 / 許可").apply {
                setOnClickListener { notificationAsk.launch(Manifest.permission.POST_NOTIFICATIONS) }
            })
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            root.addView(actionButton("高精度アラーム設定").apply {
                setOnClickListener {
                    if (!AlarmAccess.openExactAlarmSettings(this@SystemCheckActivity)) {
                        Toast.makeText(this@SystemCheckActivity, "設定画面を開けませんでした", Toast.LENGTH_SHORT).show()
                    }
                }
            })
        }
        root.addView(actionButton("Androidのアプリ設定を開く").apply {
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            }
        })
        root.addView(bodyView("タイマーは高精度アラームが未許可でも通常精度で利用できます。ただし省電力中は遅れる場合があります。点滅ライトはアプリ表示中だけ動作します。", 12f))
        setContentView(ScrollView(this).apply { addView(root) })
        refresh()
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) refresh()
    }

    private fun refresh() {
        val pm = packageManager
        val sm = getSystemService(SENSOR_SERVICE) as SensorManager
        fun granted(permission: String) = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        fun yes(value: Boolean) = if (value) "利用可" else "なし / 未対応"

        val exact = AlarmScheduler.canScheduleExact(this)
        val notificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (granted(Manifest.permission.POST_NOTIFICATIONS)) "許可済み" else "未許可"
        } else "追加許可不要"
        val appNotifications = NotificationManagerCompat.from(this).areNotificationsEnabled()
        val channelStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            val channel = manager.getNotificationChannel(TimerAlarmReceiver.CHANNEL_ID)
            if (channel == null) "未作成" else if (channel.importance == NotificationManager.IMPORTANCE_NONE) "OFF" else "ON"
        } else "対象外"

        status.text = buildString {
            appendLine("【実行時・特別アクセス】")
            appendLine("カメラ: ${if (!pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) "端末カメラなし" else if (granted(Manifest.permission.CAMERA)) "許可済み" else "未許可"}")
            appendLine("通知権限: $notificationPermission")
            appendLine("アプリ通知: ${if (appNotifications) "ON" else "OFF"}")
            appendLine("タイマー通知チャンネル: $channelStatus")
            appendLine("高精度アラーム: ${if (exact) "利用可" else "未許可（通常精度は利用可）"}")
            appendLine()
            appendLine("【端末機能】")
            appendLine("カメラ: ${yes(pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY))}")
            appendLine("フラッシュ: ${yes(pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH))}")
            appendLine("加速度センサー: ${yes(sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null)}")
            appendLine("回転ベクトル: ${yes(sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null)}")
            appendLine("磁気センサー: ${yes(sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null)}")
            appendLine()
            appendLine("INTERNET / VIBRATE / RECEIVE_BOOT_COMPLETED は通常権限のため、Androidの許可ダイアログは表示されません。")
        }
    }
}
