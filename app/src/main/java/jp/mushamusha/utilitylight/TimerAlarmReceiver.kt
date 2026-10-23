package jp.mushamusha.utilitylight

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

class TimerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val token = intent?.getStringExtra(EXTRA_TOKEN)
        if (!AlarmScheduler.finishIfCurrent(context, token)) return
        ensureChannel(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val openIntent = PendingIntent.getActivity(
            context,
            4102,
            Intent(context, TimerActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("タイマー終了")
            .setContentText("設定した時間になりました")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(openIntent)
            .setVibrate(longArrayOf(0, 350, 200, 350))
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // The user may revoke notification access immediately before the alarm fires.
        } catch (_: Exception) {
            // A failed notification must not crash the broadcast receiver.
        }
    }

    companion object {
        const val ACTION_TIMER_FIRED = "jp.mushamusha.utilitylight.TIMER_FINISHED"
        const val EXTRA_TOKEN = "timer_token"
        const val CHANNEL_ID = "timer_alarm"
        private const val NOTIFICATION_ID = 4103

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(CHANNEL_ID, "タイマー", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "タイマー終了を知らせます"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 350, 200, 350)
                val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                setSound(sound, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
            }
            manager.createNotificationChannel(channel)
        }
    }
}
