package jp.mushamusha.utilitylight

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

class TimerActivity : CompactActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private var swStart = 0L
    private var swElapsed = 0L
    private var swRunning = false
    private lateinit var swText: TextView
    private lateinit var timerText: TextView
    private lateinit var timerStatus: TextView
    private var pendingSeconds = 0L
    private var waitingExactAccess = false
    private var waitingNotificationSettings = false

    private val notificationAsk = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) continueStartTimer()
        else {
            Toast.makeText(this, "タイマー終了を知らせるには通知の許可が必要です", Toast.LENGTH_LONG).show()
            pendingSeconds = 0L
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            if (swRunning) {
                swElapsed = SystemClock.elapsedRealtime() - swStart
                swText.text = formatStopwatch(swElapsed)
            }
            refreshTimerDisplay()
            handler.postDelayed(this, 200L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TimerAlarmReceiver.ensureChannel(this)
        val root = verticalScreen()
        root.addView(titleView("ストップウォッチ・タイマー"))

        root.addView(bodyView("ストップウォッチ", 17f))
        swText = bodyView("00:00.0", 38f).apply { gravity = 17 }
        root.addView(swText)
        val swRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        swRow.addView(actionButton("開始 / 一時停止").apply {
            setOnClickListener {
                if (swRunning) {
                    swElapsed = SystemClock.elapsedRealtime() - swStart
                    swRunning = false
                } else {
                    swStart = SystemClock.elapsedRealtime() - swElapsed
                    swRunning = true
                }
            }
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        swRow.addView(actionButton("リセット").apply {
            setOnClickListener {
                swRunning = false
                swElapsed = 0L
                swText.text = "00:00.0"
            }
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        root.addView(swRow)

        root.addView(bodyView("タイマー（秒）", 17f))
        val seconds = textInput("例: 90").apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText("60")
        }
        root.addView(seconds)
        timerText = bodyView("01:00", 38f).apply { gravity = 17 }
        root.addView(timerText)
        timerStatus = bodyView("画面を消したりアプリを閉じても、OSのアラームで終了を通知します。", 12f)
        root.addView(timerStatus)

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(actionButton("開始").apply {
            setOnClickListener {
                val s = seconds.text.toString().toLongOrNull()?.coerceIn(1L, 86400L) ?: 60L
                requestStartTimer(s)
            }
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        row.addView(actionButton("停止").apply {
            setOnClickListener {
                pendingSeconds = 0L
                waitingExactAccess = false
                AlarmScheduler.cancel(this@TimerActivity)
                refreshTimerDisplay()
                Toast.makeText(this@TimerActivity, "タイマーを停止しました", Toast.LENGTH_SHORT).show()
            }
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        root.addView(row)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            root.addView(actionButton("高精度アラーム設定").apply {
                setOnClickListener {
                    if (!AlarmAccess.openExactAlarmSettings(this@TimerActivity)) {
                        Toast.makeText(this@TimerActivity, "設定画面を開けませんでした", Toast.LENGTH_SHORT).show()
                    }
                }
            })
        }

        root.addView(bodyView("高精度権限がない場合も通常精度で開始できますが、省電力中は通知が遅れる場合があります。", 11f))
        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onResume() {
        super.onResume()
        if (waitingNotificationSettings && pendingSeconds > 0L) {
            waitingNotificationSettings = false
            if (notificationsUsable()) continueStartTimer()
        }
        if (waitingExactAccess && pendingSeconds > 0L) {
            waitingExactAccess = false
            if (AlarmScheduler.canScheduleExact(this)) schedulePendingTimer(preferExact = true)
            else continueStartTimer()
        }
        refreshTimerDisplay()
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    override fun onPause() {
        handler.removeCallbacks(tick)
        super.onPause()
    }

    private fun requestStartTimer(seconds: Long) {
        pendingSeconds = seconds
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationAsk.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        if (!notificationsUsable()) {
            showNotificationSettingsDialog()
            return
        }
        continueStartTimer()
    }

    private fun continueStartTimer() {
        if (pendingSeconds <= 0L) return
        if (AlarmScheduler.canScheduleExact(this)) {
            schedulePendingTimer(preferExact = true)
            return
        }

        AlertDialog.Builder(this)
            .setTitle("タイマー精度を選択")
            .setMessage("高精度アラームが未許可です。通常精度でも開始できますが、端末の省電力中は遅れる場合があります。")
            .setNegativeButton("キャンセル") { _, _ -> pendingSeconds = 0L }
            .setNeutralButton("高精度設定") { _, _ ->
                waitingExactAccess = true
                if (!AlarmAccess.openExactAlarmSettings(this)) {
                    waitingExactAccess = false
                    Toast.makeText(this, "設定画面を開けませんでした", Toast.LENGTH_SHORT).show()
                }
            }
            .setPositiveButton("通常精度で開始") { _, _ -> schedulePendingTimer(preferExact = false) }
            .show()
    }

    private fun schedulePendingTimer(preferExact: Boolean) {
        val duration = pendingSeconds * 1000L
        val result = AlarmScheduler.schedule(this, duration, preferExact)
        if (result.success) {
            val message = if (result.mode == AlarmScheduler.Mode.EXACT) {
                "高精度タイマーを開始しました"
            } else {
                "通常精度タイマーを開始しました"
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "タイマーを設定できませんでした", Toast.LENGTH_LONG).show()
        }
        pendingSeconds = 0L
        refreshTimerDisplay()
    }

    private fun notificationsUsable(): Boolean {
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            val channel = manager.getNotificationChannel(TimerAlarmReceiver.CHANNEL_ID)
            if (channel != null && channel.importance == NotificationManager.IMPORTANCE_NONE) return false
        }
        return true
    }

    private fun showNotificationSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle("タイマー通知がOFFです")
            .setMessage("終了を知らせられないため、通知をONにしてからタイマーを開始してください。")
            .setNegativeButton("キャンセル") { _, _ -> pendingSeconds = 0L }
            .setPositiveButton("通知設定を開く") { _, _ ->
                waitingNotificationSettings = true
                openNotificationSettings()
            }
            .show()
    }

    private fun openNotificationSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                putExtra(Settings.EXTRA_CHANNEL_ID, TimerAlarmReceiver.CHANNEL_ID)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
    }

    private fun refreshTimerDisplay() {
        if (!AlarmScheduler.isRunning(this)) {
            timerText.text = "00:00"
            timerStatus.text = if (AlarmScheduler.canScheduleExact(this)) {
                "待機中。高精度アラームを利用できます。"
            } else {
                "待機中。通常精度で利用可能（省電力中は遅れる場合あり）。"
            }
            return
        }
        val left = AlarmScheduler.remainingMillis(this)
        if (left == 0L) {
            timerText.text = "00:00"
            timerStatus.text = "終了処理を待っています"
        } else {
            timerText.text = formatCountdown(left)
            timerStatus.text = if (AlarmScheduler.isHighPrecision(this)) {
                "実行中・高精度"
            } else {
                "実行中・通常精度（省電力中は遅れる場合あり）"
            }
        }
    }

    private fun formatStopwatch(ms: Long): String {
        val total = ms / 100L
        val seconds = total / 10L
        return "%02d:%02d.%d".format(seconds / 60L, seconds % 60L, total % 10L)
    }

    private fun formatCountdown(ms: Long): String {
        val total = (ms + 999L) / 1000L
        val hours = total / 3600L
        val minutes = (total % 3600L) / 60L
        val seconds = total % 60L
        return if (hours > 0) "%02d:%02d:%02d".format(hours, minutes, seconds)
        else "%02d:%02d".format(minutes, seconds)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
