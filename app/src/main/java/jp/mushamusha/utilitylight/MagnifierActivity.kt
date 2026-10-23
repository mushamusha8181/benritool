package jp.mushamusha.utilitylight

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.TorchState
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat

class MagnifierActivity : CompactActivity() {
    private lateinit var preview: PreviewView
    private lateinit var zoom: SeekBar
    private lateinit var torchButton: Button
    private var camera: Camera? = null

    private val ask = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera()
        else Toast.makeText(this, "拡大鏡にはカメラ権限が必要です", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = verticalScreen(0)
        root.addView(titleView("拡大鏡").apply { setPadding(dp(12), dp(10), dp(12), dp(4)) })
        preview = PreviewView(this)
        root.addView(preview, LinearLayout.LayoutParams(-1, 0, 1f))
        zoom = SeekBar(this).apply {
            max = 100
            progress = 0
            isEnabled = false
            setPadding(dp(12), dp(6), dp(12), dp(6))
        }
        zoom.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) camera?.cameraControl?.setLinearZoom(progress / 100f)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
        root.addView(zoom)
        torchButton = actionButton("ライト ON/OFF").apply {
            isEnabled = false
            setOnClickListener {
                val current = camera ?: return@setOnClickListener
                if (!current.cameraInfo.hasFlashUnit()) return@setOnClickListener
                val turnOn = current.cameraInfo.torchState.value != TorchState.ON
                current.cameraControl.enableTorch(turnOn)
            }
        }
        root.addView(torchButton)
        setContentView(root)

        if (!CameraSupport.hasAnyCamera(this)) {
            torchButton.text = "カメラなし"
        } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera()
        else ask.launch(Manifest.permission.CAMERA)
    }

    private fun startCamera() {
        CameraSupport.bind(
            context = this,
            lifecycleOwner = this,
            previewView = preview,
            onBound = { bound ->
                camera = bound
                zoom.isEnabled = true
                torchButton.isEnabled = bound.cameraInfo.hasFlashUnit()
                torchButton.text = if (torchButton.isEnabled) "ライト ON/OFF" else "ライトなし"
                if (bound.cameraInfo.hasFlashUnit()) {
                    bound.cameraInfo.torchState.observe(this) { state ->
                        torchButton.text = if (state == TorchState.ON) "ライト OFF" else "ライト ON"
                    }
                }
            },
            onUnavailable = { message ->
                zoom.isEnabled = false
                torchButton.isEnabled = false
                torchButton.text = message
            }
        )
    }
}
