package jp.mushamusha.utilitylight

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.StatFs
import android.widget.ScrollView

class DeviceInfoActivity : CompactActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = verticalScreen()
        root.addView(titleView("端末・バッテリー情報"))
        root.addView(bodyView(buildInfo(), 14f))
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun buildInfo(): String {
        val battery = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val percent = if (level >= 0) level * 100 / scale.coerceAtLeast(1) else -1
        val tempRaw = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val temp = tempRaw / 10f
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mem = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val stat = StatFs(filesDir.absolutePath)
        fun gb(bytes: Long) = String.format("%.1f GB", bytes / 1024.0 / 1024.0 / 1024.0)
        val dm = resources.displayMetrics

        return buildString {
            appendLine("端末: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("画面: ${dm.widthPixels} × ${dm.heightPixels}px / ${String.format("%.1f", dm.density)} density")
            appendLine()
            appendLine("バッテリー: ${if (percent >= 0) "$percent%" else "取得不可"}")
            appendLine("充電中: ${if (status == null || status == -1) "取得不可" else if (charging) "はい" else "いいえ"}")
            appendLine("バッテリー温度: ${if (tempRaw > 0) String.format("%.1f℃", temp) else "取得不可"}")
            appendLine()
            appendLine("メモリ合計: ${gb(mem.totalMem)}")
            appendLine("メモリ空き: ${gb(mem.availMem)}")
            appendLine("内部ストレージ合計: ${gb(stat.totalBytes)}")
            appendLine("内部ストレージ空き: ${gb(stat.availableBytes)}")
        }
    }
}
