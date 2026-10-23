package jp.mushamusha.utilitylight

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.appcompat.app.AlertDialog
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class QrScannerActivity : CompactActivity() {
    private lateinit var preview: PreviewView
    private lateinit var result: TextView
    private var executor: ExecutorService? = null
    private var scanner: BarcodeScanner? = null
    private var analyzer: ImageAnalysis? = null
    private var lastValue: String? = null
    private var cameraStarted = false

    private val ask = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera()
        else Toast.makeText(this, "読み取りにはカメラ権限が必要です", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = verticalScreen(0)
        root.addView(titleView("QR・バーコード読み取り").apply { setPadding(dp(12), dp(10), dp(12), dp(4)) })
        preview = PreviewView(this)
        root.addView(preview, LinearLayout.LayoutParams(-1, 0, 1f))
        result = bodyView("コードをカメラに映してください", 16f).apply {
            setPadding(dp(12), dp(6), dp(12), dp(6))
            maxLines = 4
            ellipsize = TextUtils.TruncateAt.END
            setOnClickListener { lastValue?.let(::showActions) }
        }
        root.addView(result)
        root.addView(actionButton("読み取り履歴").apply { setOnClickListener { showHistory() } })
        setContentView(root)

        if (!CameraSupport.hasAnyCamera(this)) {
            result.text = "この端末には利用できるカメラがありません"
        } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            ask.launch(Manifest.permission.CAMERA)
        }
    }

    @OptIn(ExperimentalGetImage::class)
    private fun startCamera() {
        if (cameraStarted) return
        cameraStarted = true
        val localExecutor = Executors.newSingleThreadExecutor().also { executor = it }
        val localScanner = BarcodeScanning.getClient().also { scanner = it }
        val localAnalyzer = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { analyzer = it }

        localAnalyzer.setAnalyzer(localExecutor) { proxy ->
            val media = proxy.image
            if (media == null) {
                proxy.close()
                return@setAnalyzer
            }
            val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
            localScanner.process(image)
                .addOnSuccessListener { codes ->
                    codes.firstOrNull()?.rawValue?.let { value ->
                        runOnUiThread {
                            if (value != lastValue && !isFinishing && !isDestroyed) {
                                lastValue = value
                                saveHistory(value)
                                result.text = "$value\nタップで操作"
                            }
                        }
                    }
                }
                .addOnCompleteListener { proxy.close() }
        }

        CameraSupport.bind(
            context = this,
            lifecycleOwner = this,
            previewView = preview,
            extraUseCases = listOf(localAnalyzer),
            onUnavailable = { message ->
                cameraStarted = false
                localAnalyzer.clearAnalyzer()
                if (analyzer === localAnalyzer) analyzer = null
                if (scanner === localScanner) scanner = null
                if (executor === localExecutor) executor = null
                localScanner.close()
                localExecutor.shutdownNow()
                result.text = message
            }
        )
    }

    private fun showActions(value: String) {
        val items = arrayOf("コピー", "共有", "ブラウザで開く")
        AlertDialog.Builder(this)
            .setTitle(value.take(120))
            .setItems(items) { _, which ->
                when (which) {
                    0 -> {
                        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
                            .setPrimaryClip(ClipData.newPlainText("code", value))
                        Toast.makeText(this, "コピーしました", Toast.LENGTH_SHORT).show()
                    }
                    1 -> safeShare(value)
                    2 -> openBrowser(value)
                }
            }
            .show()
    }

    private fun safeShare(value: String) {
        shareTextSafely(value)
    }

    private fun openBrowser(value: String) {
        val uri = runCatching { Uri.parse(value) }.getOrNull()
        if (uri?.scheme != "http" && uri?.scheme != "https") {
            Toast.makeText(this, "URLではありません", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_VIEW, uri)
        if (intent.resolveActivity(packageManager) == null) {
            Toast.makeText(this, "URLを開けるアプリがありません", Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(intent)
    }

    private fun saveHistory(value: String) {
        val prefs = getSharedPreferences("scan_history", MODE_PRIVATE)
        val old = prefs.getString("items", "").orEmpty()
            .split("\u001e").filter { it.isNotBlank() }.toMutableList()
        old.remove(value)
        old.add(0, value)
        prefs.edit().putString("items", old.take(30).joinToString("\u001e")).apply()
    }

    private fun showHistory() {
        val prefs = getSharedPreferences("scan_history", MODE_PRIVATE)
        val items = prefs.getString("items", "").orEmpty().split("\u001e").filter { it.isNotBlank() }
        if (items.isEmpty()) {
            Toast.makeText(this, "履歴はまだありません", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("読み取り履歴")
            .setItems(items.map { it.take(100) }.toTypedArray()) { _, index ->
                lastValue = items[index]
                result.text = "${items[index]}\nタップで操作"
            }
            .setNeutralButton("履歴を消す") { _, _ -> prefs.edit().remove("items").apply() }
            .show()
    }

    override fun onDestroy() {
        analyzer?.clearAnalyzer()
        scanner?.close()
        executor?.shutdownNow()
        super.onDestroy()
    }
}
