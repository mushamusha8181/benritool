package jp.mushamusha.utilitylight

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat

class ColorPickerActivity : CompactActivity() {
    private lateinit var preview: PreviewView
    private lateinit var swatch: TextView
    private var lastHex = ""

    private val ask = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera()
        else Toast.makeText(this, "色の取得にはカメラ権限が必要です", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = verticalScreen(0)
        root.addView(titleView("カラーピッカー").apply { setPadding(dp(12), dp(10), dp(12), dp(4)) })
        preview = PreviewView(this)
        root.addView(preview, LinearLayout.LayoutParams(-1, 0, 1f))
        swatch = bodyView("中央の色を取得します", 16f).apply {
            gravity = 17
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        root.addView(swatch)
        root.addView(bodyView("※ カメラの露出・ホワイトバランスの影響を受けるため、表示色は参考値です。", 11f).apply {
            setPadding(dp(12), 0, dp(12), dp(4))
        })
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), 0, dp(8), dp(6))
        }
        row.addView(actionButton("中央の色を取得").apply { setOnClickListener { pickColor() } }, LinearLayout.LayoutParams(0, dp(48), 1f))
        row.addView(actionButton("コードをコピー").apply { setOnClickListener { copyColor() } }, LinearLayout.LayoutParams(0, dp(48), 1f))
        root.addView(row)
        setContentView(root)

        if (!CameraSupport.hasAnyCamera(this)) swatch.text = "この端末には利用できるカメラがありません"
        else if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera()
        else ask.launch(Manifest.permission.CAMERA)
    }

    private fun startCamera() {
        CameraSupport.bind(
            context = this,
            lifecycleOwner = this,
            previewView = preview,
            onUnavailable = { message -> swatch.text = message }
        )
    }

    private fun pickColor() {
        val bitmap = preview.bitmap ?: run {
            Toast.makeText(this, "カメラ画像を取得できませんでした", Toast.LENGTH_SHORT).show()
            return
        }
        if (bitmap.width <= 0 || bitmap.height <= 0) return
        val pixel = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)
        lastHex = String.format("#%02X%02X%02X", r, g, b)
        swatch.text = "$lastHex   RGB($r, $g, $b)"
        swatch.setBackgroundColor(Color.rgb(r, g, b))
        val luminance = 0.299 * r + 0.587 * g + 0.114 * b
        swatch.setTextColor(if (luminance > 160) Color.BLACK else Color.WHITE)
    }

    private fun copyColor() {
        if (lastHex.isBlank()) {
            Toast.makeText(this, "先に色を取得してください", Toast.LENGTH_SHORT).show()
            return
        }
        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("color", lastHex))
        Toast.makeText(this, "コピーしました", Toast.LENGTH_SHORT).show()
    }
}
