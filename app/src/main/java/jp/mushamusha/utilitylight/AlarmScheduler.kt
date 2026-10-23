package jp.mushamusha.utilitylight

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import java.util.UUID

object AlarmScheduler {
    enum class Mode { EXACT, INEXACT }
    data class ScheduleResult(val success: Boolean, val mode: Mode? = null)

    private const val EXACT_REQUEST = 4101
    private const val FALLBACK_REQUEST = 4104
    private const val PREFS = "timer_state"
    private const val KEY_WALL_END = "timer_wall_end_ms"
    private const val KEY_ELAPSED_END = "timer_elapsed_end_ms"
    private const val KEY_BOOT_COUNT = "timer_boot_count"
    private const val KEY_RUNNING = "timer_running"
    private const val KEY_TOKEN = "timer_token"
    private const val KEY_EXACT = "timer_exact"

    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
    }

    fun schedule(context: Context, durationMillis: Long, preferExact: Boolean): ScheduleResult {
        if (durationMillis <= 0L) return ScheduleResult(false)
        cancelAlarmsOnly(context)
        clearState(context)
        val token = UUID.randomUUID().toString()
        val elapsedEnd = SystemClock.elapsedRealtime() + durationMillis
        val wallEnd = System.currentTimeMillis() + durationMillis
        val exactRequested = preferExact && canScheduleExact(context)
        val mode = scheduleAlarms(context, elapsedEnd, token, exactRequested) ?: return ScheduleResult(false)

        saveState(context, wallEnd, elapsedEnd, token, mode == Mode.EXACT)
        return ScheduleResult(true, mode)
    }

    fun cancel(context: Context) {
        cancelAlarmsOnly(context)
        clearState(context)
    }

    fun clearState(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    fun finishIfCurrent(context: Context, token: String?): Boolean {
        if (token.isNullOrBlank()) return false
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_RUNNING, false) || prefs.getString(KEY_TOKEN, null) != token) return false
        cancelAlarmsOnly(context)
        clearState(context)
        return true
    }

    fun restoreIfNeeded(context: Context) {
        if (!isRunning(context)) return
        val token = token(context) ?: run {
            clearState(context)
            return
        }
        val remaining = remainingMillis(context)
        if (remaining <= 0L) {
            context.sendBroadcast(
                Intent(context, TimerAlarmReceiver::class.java)
                    .setAction(TimerAlarmReceiver.ACTION_TIMER_FIRED)
                    .putExtra(TimerAlarmReceiver.EXTRA_TOKEN, token)
            )
            return
        }

        cancelAlarmsOnly(context)
        val elapsedEnd = SystemClock.elapsedRealtime() + remaining
        val wallEnd = System.currentTimeMillis() + remaining
        val exactRequested = canScheduleExact(context)
        val mode = scheduleAlarms(context, elapsedEnd, token, exactRequested)
        if (mode != null) {
            saveState(context, wallEnd, elapsedEnd, token, mode == Mode.EXACT)
        } else {
            clearState(context)
        }
    }

    fun remainingMillis(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_RUNNING, false)) return 0L
        val savedBoot = prefs.getInt(KEY_BOOT_COUNT, -1)
        val currentBoot = bootCount(context)
        val elapsedEnd = prefs.getLong(KEY_ELAPSED_END, 0L)
        return if (savedBoot >= 0 && savedBoot == currentBoot && elapsedEnd > 0L) {
            (elapsedEnd - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        } else {
            (prefs.getLong(KEY_WALL_END, 0L) - System.currentTimeMillis()).coerceAtLeast(0L)
        }
    }

    fun isRunning(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_RUNNING, false)

    fun isHighPrecision(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_EXACT, false) && canScheduleExact(context)
    }

    private fun token(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TOKEN, null)

    private fun scheduleAlarms(context: Context, elapsedEnd: Long, token: String, exactRequested: Boolean): Mode? {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        var exactScheduled = false
        if (exactRequested) {
            exactScheduled = try {
                val exactPending = pendingIntent(context, EXACT_REQUEST, ACTION_EXACT, token, create = true)!!
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, elapsedEnd, exactPending)
                true
            } catch (_: Exception) {
                false
            }
        }

        val fallbackScheduled = try {
            val fallback = pendingIntent(context, FALLBACK_REQUEST, ACTION_FALLBACK, token, create = true)!!
            alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, elapsedEnd, fallback)
            true
        } catch (_: Exception) {
            false
        }

        return when {
            exactScheduled -> Mode.EXACT
            fallbackScheduled -> Mode.INEXACT
            else -> null
        }
    }

    private fun saveState(context: Context, wallEnd: Long, elapsedEnd: Long, token: String, exact: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_WALL_END, wallEnd)
            .putLong(KEY_ELAPSED_END, elapsedEnd)
            .putInt(KEY_BOOT_COUNT, bootCount(context))
            .putBoolean(KEY_RUNNING, true)
            .putString(KEY_TOKEN, token)
            .putBoolean(KEY_EXACT, exact)
            .apply()
    }

    private fun cancelAlarmsOnly(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        pendingIntent(context, EXACT_REQUEST, ACTION_EXACT, null, create = false)?.let(alarmManager::cancel)
        pendingIntent(context, FALLBACK_REQUEST, ACTION_FALLBACK, null, create = false)?.let(alarmManager::cancel)
    }

    private fun pendingIntent(
        context: Context,
        requestCode: Int,
        action: String,
        token: String?,
        create: Boolean
    ): PendingIntent? {
        val intent = Intent(context, TimerAlarmReceiver::class.java)
            .setAction(action)
            .apply { if (token != null) putExtra(TimerAlarmReceiver.EXTRA_TOKEN, token) }
        val flags = PendingIntent.FLAG_IMMUTABLE or if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE
        return PendingIntent.getBroadcast(context, requestCode, intent, flags)
    }

    private fun bootCount(context: Context): Int = try {
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
    } catch (_: Exception) {
        -1
    }

    private const val ACTION_EXACT = "jp.mushamusha.utilitylight.TIMER_EXACT"
    private const val ACTION_FALLBACK = "jp.mushamusha.utilitylight.TIMER_FALLBACK"
}
