package jp.mushamusha.utilitylight

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Handler
import android.os.Looper

class TorchController(context: Context) : AutoCloseable {
    enum class StrengthResult { VARIABLE, FIXED, FAILED }

    private val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var cachedCameraId: String? = null

    var enabled: Boolean = false
        private set

    var onStateChanged: ((Boolean) -> Unit)? = null

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, torchEnabled: Boolean) {
            if (cameraId == resolveCameraId()) {
                enabled = torchEnabled
                onStateChanged?.invoke(torchEnabled)
            }
        }

        override fun onTorchModeUnavailable(cameraId: String) {
            if (cameraId == cachedCameraId && enabled) {
                enabled = false
                onStateChanged?.invoke(false)
            }
        }
    }

    init {
        runCatching { manager.registerTorchCallback(torchCallback, mainHandler) }
    }

    fun hasFlash(): Boolean = resolveCameraId() != null

    fun setEnabled(on: Boolean): Boolean {
        val id = resolveCameraId() ?: return false
        return try {
            manager.setTorchMode(id, on)
            enabled = on
            true
        } catch (_: Exception) {
            cachedCameraId = null
            enabled = false
            onStateChanged?.invoke(false)
            false
        }
    }

    fun setStrength(percent: Int): StrengthResult {
        val id = resolveCameraId() ?: return StrengthResult.FAILED
        if (Build.VERSION.SDK_INT < 33) {
            return if (setEnabled(true)) StrengthResult.FIXED else StrengthResult.FAILED
        }
        return try {
            val characteristics = manager.getCameraCharacteristics(id)
            val max = characteristics.get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) ?: 1
            if (max <= 1) {
                if (setEnabled(true)) StrengthResult.FIXED else StrengthResult.FAILED
            } else {
                val level = when {
                    percent <= 33 -> 1
                    percent <= 66 -> (max + 1) / 2
                    else -> max
                }.coerceIn(1, max)
                manager.turnOnTorchWithStrengthLevel(id, level)
                enabled = true
                StrengthResult.VARIABLE
            }
        } catch (_: Exception) {
            cachedCameraId = null
            if (setEnabled(true)) StrengthResult.FIXED else StrengthResult.FAILED
        }
    }

    fun off(): Boolean = setEnabled(false)

    private fun resolveCameraId(): String? {
        cachedCameraId?.let { return it }
        cachedCameraId = try {
            manager.cameraIdList.firstOrNull { id ->
                manager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (_: Exception) {
            null
        }
        return cachedCameraId
    }

    override fun close() {
        runCatching { manager.unregisterTorchCallback(torchCallback) }
        onStateChanged = null
    }
}
