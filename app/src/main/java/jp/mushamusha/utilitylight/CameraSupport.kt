package jp.mushamusha.utilitylight

import android.content.Context
import android.content.pm.PackageManager
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner

object CameraSupport {
    fun hasAnyCamera(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)

    fun bind(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        extraUseCases: List<UseCase> = emptyList(),
        onBound: (Camera) -> Unit = {},
        onUnavailable: (String) -> Unit
    ) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val provider = try {
                future.get()
            } catch (_: Exception) {
                onUnavailable("カメラを初期化できませんでした")
                return@addListener
            }

            val selector = chooseSelector(provider)
            if (selector == null) {
                onUnavailable("この端末には利用できるカメラがありません")
                return@addListener
            }

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            try {
                provider.unbindAll()
                val all = ArrayList<UseCase>(extraUseCases.size + 1).apply {
                    add(preview)
                    addAll(extraUseCases)
                }
                val camera = provider.bindToLifecycle(lifecycleOwner, selector, *all.toTypedArray())
                onBound(camera)
            } catch (_: Exception) {
                onUnavailable("カメラを開始できませんでした")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun chooseSelector(provider: ProcessCameraProvider): CameraSelector? {
        val candidates = listOf(CameraSelector.DEFAULT_BACK_CAMERA, CameraSelector.DEFAULT_FRONT_CAMERA)
        return candidates.firstOrNull { selector ->
            try {
                provider.hasCamera(selector)
            } catch (_: Exception) {
                false
            }
        }
    }
}
