package jp.mushamusha.utilitylight

import android.app.Activity

data class ToolEntry(val key: String, val label: String, val activity: Class<out Activity>?)

object ToolCatalog {
    val tools = listOf(
        ToolEntry("torch", "🔦 ライト", null),
        ToolEntry("screen_light", "☀️ 画面ライト", ScreenLightActivity::class.java),
        ToolEntry("compass", "🧭 コンパス", CompassActivity::class.java),
        ToolEntry("ruler", "📏 定規", RulerActivity::class.java),
        ToolEntry("level", "⚖️ 水平器", LevelActivity::class.java),
        ToolEntry("magnetic", "🧲 磁気メーター", MagneticMeterActivity::class.java),
        ToolEntry("magnifier", "🔎 拡大鏡", MagnifierActivity::class.java),
        ToolEntry("qr", "▣ QR・バーコード", QrScannerActivity::class.java),
        ToolEntry("ocr", "🔤 文字読み取り", OcrActivity::class.java),
        ToolEntry("color", "🎨 色を調べる", ColorPickerActivity::class.java),
        ToolEntry("qr_make", "⊞ QR作成", QrMakerActivity::class.java),
        ToolEntry("wifi_qr", "📶 Wi-Fi QR", WifiQrActivity::class.java),
        ToolEntry("short_url", "🔗 短縮URL", ShortUrlActivity::class.java),
        ToolEntry("unit", "↔️ 単位変換", UnitConverterActivity::class.java),
        ToolEntry("text", "✍️ 文字ツール", TextToolsActivity::class.java),
        ToolEntry("random", "🎲 乱数・くじ", RandomToolActivity::class.java),
        ToolEntry("timer", "⏱️ タイマー", TimerActivity::class.java),
        ToolEntry("counter", "🔢 カウンター", CounterActivity::class.java),
        ToolEntry("device", "📱 端末情報", DeviceInfoActivity::class.java)
    )
    val defaultFavorites = setOf("torch", "qr", "magnifier", "timer")
}
