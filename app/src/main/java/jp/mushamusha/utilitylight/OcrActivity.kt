package jp.mushamusha.utilitylight

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions

class OcrActivity : CompactActivity() {
    private lateinit var preview: PreviewView
    private lateinit var result: TextView
    private lateinit var readButton: Button
    private var imageCapture: ImageCapture? = null
    private var recognizer: TextRecognizer? = null
    private var reading = false
    private var lastRecognized = ""

    private val ask = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera()
        else Toast.makeText(this, "文字読み取りにはカメラ権限が必要です", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = verticalScreen(0)
        root.addView(titleView("文字読み取り（OCR）").apply { setPadding(dp(12), dp(10), dp(12), dp(4)) })
        preview = PreviewView(this)
        root.addView(preview, LinearLayout.LayoutParams(-1, 0, 1f))
        result = bodyView("読み取りたい文字を中央に映して「文字を読み取る」を押してください。", 14f).apply {
            setPadding(dp(12), dp(6), dp(12), dp(4))
            maxLines = 5
        }
        root.addView(result)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), 0, dp(8), dp(6))
        }
        readButton = actionButton("文字を読み取る").apply { setOnClickListener { readText() } }
        row.addView(readButton, LinearLayout.LayoutParams(0, dp(48), 1f))
        row.addView(actionButton("コピー").apply { setOnClickListener { copyResult() } }, LinearLayout.LayoutParams(0, dp(48), 1f))
        root.addView(row)
        setContentView(root)

        if (!CameraSupport.hasAnyCamera(this)) result.text = "この端末には利用できるカメラがありません"
        else if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera()
        else ask.launch(Manifest.permission.CAMERA)
    }

    private fun startCamera() {
        val capture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()
        imageCapture = capture
        CameraSupport.bind(
            context = this,
            lifecycleOwner = this,
            previewView = preview,
            extraUseCases = listOf(capture),
            onUnavailable = { message ->
                imageCapture = null
                result.text = message
            }
        )
    }

    @OptIn(ExperimentalGetImage::class)
    private fun readText() {
        if (reading) return
        val capture = imageCapture ?: run {
            Toast.makeText(this, "カメラの準備中です", Toast.LENGTH_SHORT).show()
            return
        }
        reading = true
        readButton.isEnabled = false
        result.text = "高解像度で撮影して読み取り中…"
        capture.takePicture(ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val mediaImage = image.image
                if (mediaImage == null) {
                    image.close()
                    finishReading("カメラ画像を取得できませんでした", recognized = false)
                    return
                }
                val input = InputImage.fromMediaImage(mediaImage, image.imageInfo.rotationDegrees)
                val localRecognizer = recognizer ?: TextRecognition
                    .getClient(JapaneseTextRecognizerOptions.Builder().build())
                    .also { recognizer = it }
                localRecognizer.process(input)
                    .addOnSuccessListener { text ->
                        val value = text.text.ifBlank { "文字を認識できませんでした" }
                        finishReading(value, recognized = text.text.isNotBlank())
                    }
                    .addOnFailureListener { finishReading("読み取りに失敗しました", recognized = false) }
                    .addOnCompleteListener { image.close() }
            }

            override fun onError(exception: ImageCaptureException) {
                finishReading("撮影に失敗しました", recognized = false)
            }
        })
    }

    private fun finishReading(message: String, recognized: Boolean) {
        reading = false
        if (isFinishing || isDestroyed) return
        readButton.isEnabled = true
        result.text = message
        lastRecognized = if (recognized) message else ""
    }

    private fun copyResult() {
        if (lastRecognized.isBlank()) {
            Toast.makeText(this, "コピーできる読み取り結果がありません", Toast.LENGTH_SHORT).show()
            return
        }
        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("OCR", lastRecognized))
        Toast.makeText(this, "コピーしました", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        recognizer?.close()
        super.onDestroy()
    }
}
