package jp.mushamusha.utilitylight

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat

class MainActivity : CompactActivity() {
    companion object {
        const val ACTION_TORCH_ON = "jp.mushamusha.utilitylight.action.TORCH_ON"
        const val ACTION_SCAN_QR = "jp.mushamusha.utilitylight.action.SCAN_QR"
        private const val SETUP_VERSION = 3
    }

    private lateinit var torch: TorchController
    private lateinit var torchButton: Button
    private var blinkRunning = false
    private var blinkOn = false
    private val handler = Handler(Looper.getMainLooper())
    private var pendingShortcutAction: String? = null
    private var pendingSharedText: String? = null
    private var pendingCameraAction: (() -> Unit)? = null
    private var waitingExactSetupReturn = false
    private var setupCompletion: (() -> Unit)? = null

    private val initialPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        getSharedPreferences("app_setup", MODE_PRIVATE)
            .edit().putInt("permission_setup_version", SETUP_VERSION).apply()
        val denied = results.filterValues { !it }.keys
        if (denied.isNotEmpty()) {
            Toast.makeText(this, "未許可の機能は、使用時または「権限確認」から設定できます", Toast.LENGTH_LONG).show()
        }
        finishInitialSetup()
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val action = pendingCameraAction
        pendingCameraAction = null
        if (granted) action?.invoke()
        else Toast.makeText(this, "ライトやカメラ機能にはカメラ権限が必要です", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        torch = TorchController(this).also { controller ->
            controller.onStateChanged = { updateTorchText() }
        }
        torchButton = findViewById(R.id.btnTorch)
        bindButtons()
        captureShortcut(intent)
        requestInitialPermissionsIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        if (::torch.isInitialized) {
            renderFavorites()
            updateTorchText()
        }
        if (waitingExactSetupReturn) {
            waitingExactSetupReturn = false
            val completion = setupCompletion
            setupCompletion = null
            completion?.invoke()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        captureShortcut(intent)
        val setupDone = getSharedPreferences("app_setup", MODE_PRIVATE)
            .getInt("permission_setup_version", 0) >= SETUP_VERSION
        if (setupDone) processPendingShortcut()
    }

    private fun bindButtons() {
        torchButton.setOnClickListener { withCameraPermission { toggleTorchInternal() } }
        findViewById<Button>(R.id.btnWeak).setOnClickListener { withCameraPermission { setTorchStrength(25, "弱") } }
        findViewById<Button>(R.id.btnMedium).setOnClickListener { withCameraPermission { setTorchStrength(55, "中") } }
        findViewById<Button>(R.id.btnStrong).setOnClickListener {
            withCameraPermission {
                AlertDialog.Builder(this)
                    .setTitle("強ライト")
                    .setMessage("強い光はバッテリー消費と発熱が増えることがあります。続けますか？")
                    .setNegativeButton("やめる", null)
                    .setPositiveButton("点灯") { _, _ -> setTorchStrength(100, "強") }
                    .show()
            }
        }

        findViewById<Button>(R.id.btnScreenLight).open(ScreenLightActivity::class.java)
        findViewById<Button>(R.id.btnCompass).open(CompassActivity::class.java)
        findViewById<Button>(R.id.btnRuler).open(RulerActivity::class.java)
        findViewById<Button>(R.id.btnLevel).open(LevelActivity::class.java)
        findViewById<Button>(R.id.btnMagnetic).open(MagneticMeterActivity::class.java)
        findViewById<Button>(R.id.btnMagnifier).open(MagnifierActivity::class.java, usesCamera = true)
        findViewById<Button>(R.id.btnQr).open(QrScannerActivity::class.java, usesCamera = true)
        findViewById<Button>(R.id.btnOcr).open(OcrActivity::class.java, usesCamera = true)
        findViewById<Button>(R.id.btnColorPicker).open(ColorPickerActivity::class.java, usesCamera = true)
        findViewById<Button>(R.id.btnQrMaker).open(QrMakerActivity::class.java)
        findViewById<Button>(R.id.btnWifiQr).open(WifiQrActivity::class.java)
        findViewById<Button>(R.id.btnShortUrl).open(ShortUrlActivity::class.java)
        findViewById<Button>(R.id.btnUnit).open(UnitConverterActivity::class.java)
        findViewById<Button>(R.id.btnTextTools).open(TextToolsActivity::class.java)
        findViewById<Button>(R.id.btnRandom).open(RandomToolActivity::class.java)
        findViewById<Button>(R.id.btnTimer).open(TimerActivity::class.java)
        findViewById<Button>(R.id.btnCounter).open(CounterActivity::class.java)
        findViewById<Button>(R.id.btnDeviceInfo).open(DeviceInfoActivity::class.java)
        findViewById<Button>(R.id.btnFavorites).open(FavoritesActivity::class.java)
        findViewById<Button>(R.id.btnSystemCheck).open(SystemCheckActivity::class.java)
        findViewById<Button>(R.id.btnBlink).setOnClickListener { button ->
            withCameraPermission { toggleBlink(button as Button) }
        }
    }

    private fun requestInitialPermissionsIfNeeded() {
        val prefs = getSharedPreferences("app_setup", MODE_PRIVATE)
        if (prefs.getInt("permission_setup_version", 0) >= SETUP_VERSION) {
            finishInitialSetup()
            return
        }

        val permissions = mutableListOf<String>()
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
        ) permissions += Manifest.permission.CAMERA
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) permissions += Manifest.permission.POST_NOTIFICATIONS

        if (permissions.isEmpty()) {
            prefs.edit().putInt("permission_setup_version", SETUP_VERSION).apply()
            finishInitialSetup()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("初回セットアップ")
            .setMessage(
                "QR・バーコード、拡大鏡、文字読み取り、カラーピッカー、ライトにはカメラを使用します。" +
                    "タイマー終了を知らせるため通知を使用します。必要な権限を続けて確認します。"
            )
            .setCancelable(false)
            .setPositiveButton("権限を設定") { _, _ -> initialPermissionLauncher.launch(permissions.toTypedArray()) }
            .show()
    }

    private fun finishInitialSetup() {
        requestExactAlarmAccessIfNeeded { processPendingShortcut() }
    }

    private fun requestExactAlarmAccessIfNeeded(onFinished: () -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || AlarmScheduler.canScheduleExact(this)) {
            onFinished()
            return
        }
        val prefs = getSharedPreferences("app_setup", MODE_PRIVATE)
        if (prefs.getBoolean("exact_alarm_intro_v2", false)) {
            onFinished()
            return
        }
        prefs.edit().putBoolean("exact_alarm_intro_v2", true).apply()

        AlertDialog.Builder(this)
            .setTitle("タイマーの高精度通知")
            .setMessage("タイマーは通常精度でも使えます。省電力中でもできるだけ時間どおり鳴らしたい場合は、Androidの「アラームとリマインダー」を許可してください。")
            .setNegativeButton("あとで") { _, _ -> onFinished() }
            .setPositiveButton("設定を開く") { _, _ ->
                setupCompletion = onFinished
                waitingExactSetupReturn = AlarmAccess.openExactAlarmSettings(this)
                if (!waitingExactSetupReturn) {
                    setupCompletion = null
                    onFinished()
                }
            }
            .setOnCancelListener { onFinished() }
            .show()
    }

    private fun renderFavorites() {
        val title = findViewById<TextView>(R.id.favoriteTitle)
        val container = findViewById<LinearLayout>(R.id.favoritesContainer)
        container.removeAllViews()
        val keys = getSharedPreferences("favorites", MODE_PRIVATE)
            .getStringSet("keys", ToolCatalog.defaultFavorites) ?: ToolCatalog.defaultFavorites
        val tools = ToolCatalog.tools.filter { it.key in keys }
        val visible = tools.isNotEmpty()
        title.visibility = if (visible) View.VISIBLE else View.GONE
        container.visibility = if (visible) View.VISIBLE else View.GONE
        tools.chunked(2).forEach { pair ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            pair.forEach { tool ->
                val button = actionButton(tool.label).apply {
                    textSize = 13f
                    setOnClickListener { launchTool(tool) }
                }
                row.addView(button, LinearLayout.LayoutParams(0, dp(48), 1f))
            }
            if (pair.size == 1) row.addView(View(this), LinearLayout.LayoutParams(0, dp(48), 1f))
            container.addView(row)
        }
    }

    private fun launchTool(tool: ToolEntry) {
        if (tool.key == "torch") withCameraPermission { toggleTorchInternal() }
        else tool.activity?.let { activity ->
            val usesCamera = activity == MagnifierActivity::class.java ||
                activity == QrScannerActivity::class.java ||
                activity == OcrActivity::class.java ||
                activity == ColorPickerActivity::class.java
            if (usesCamera && torch.enabled) torch.off()
            startActivity(Intent(this, activity))
        }
    }

    private fun captureShortcut(source: Intent?) {
        val action = source?.action
        when {
            action == ACTION_TORCH_ON || action == ACTION_SCAN_QR -> {
                pendingShortcutAction = action
                source.action = Intent.ACTION_MAIN
            }
            action == Intent.ACTION_SEND && source.type == "text/plain" -> {
                pendingSharedText = source.getStringExtra(Intent.EXTRA_TEXT)
                source.action = Intent.ACTION_MAIN
            }
        }
    }

    private fun processPendingShortcut() {
        val shared = pendingSharedText
        if (!shared.isNullOrBlank()) {
            pendingSharedText = null
            startActivity(
                Intent(this, ShortUrlActivity::class.java)
                    .setAction(Intent.ACTION_SEND)
                    .putExtra(Intent.EXTRA_TEXT, shared)
            )
            return
        }
        val action = pendingShortcutAction ?: return
        pendingShortcutAction = null
        when (action) {
            ACTION_TORCH_ON -> withCameraPermission {
                if (!torch.hasFlash()) {
                    Toast.makeText(this, "使用できるフラッシュがありません", Toast.LENGTH_SHORT).show()
                } else if (torch.setEnabled(true)) {
                    updateTorchText()
                    Toast.makeText(this, "ライトを点灯しました", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "ライトを点灯できませんでした", Toast.LENGTH_SHORT).show()
                }
            }
            ACTION_SCAN_QR -> {
                if (torch.enabled) torch.off()
                startActivity(Intent(this, QrScannerActivity::class.java))
            }
        }
    }

    private fun Button.open(cls: Class<*>, usesCamera: Boolean = false) {
        setOnClickListener {
            if (usesCamera && torch.enabled) torch.off()
            startActivity(Intent(this@MainActivity, cls))
        }
    }

    private fun withCameraPermission(action: () -> Unit) {
        if (!CameraSupport.hasAnyCamera(this)) {
            action()
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            action()
        } else {
            pendingCameraAction = action
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun toggleTorchInternal() {
        if (!torch.hasFlash()) {
            Toast.makeText(this, "この端末には使用できるフラッシュがありません", Toast.LENGTH_SHORT).show()
            return
        }
        if (!torch.setEnabled(!torch.enabled)) {
            Toast.makeText(this, "ライトを制御できませんでした", Toast.LENGTH_SHORT).show()
        }
        updateTorchText()
    }

    private fun setTorchStrength(percent: Int, label: String) {
        if (!torch.hasFlash()) {
            Toast.makeText(this, "この端末には使用できるフラッシュがありません", Toast.LENGTH_SHORT).show()
            return
        }
        when (torch.setStrength(percent)) {
            TorchController.StrengthResult.VARIABLE -> torchButton.text = "🔦  ライト  $label"
            TorchController.StrengthResult.FIXED -> {
                torchButton.text = "🔦  ライト ON（固定）"
                Toast.makeText(this, "この端末は光量変更に非対応のため通常点灯しました", Toast.LENGTH_LONG).show()
            }
            TorchController.StrengthResult.FAILED -> {
                updateTorchText()
                Toast.makeText(this, "ライトを制御できませんでした", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun updateTorchText() {
        if (::torchButton.isInitialized) {
            torchButton.text = if (torch.enabled) "🔦  ライト  ON" else "🔦  ライト  OFF"
        }
    }

    private fun toggleBlink(button: Button) {
        if (!torch.hasFlash()) {
            Toast.makeText(this, "フラッシュがありません", Toast.LENGTH_SHORT).show()
            return
        }
        if (blinkRunning) {
            stopBlink(button)
            return
        }
        blinkRunning = true
        blinkOn = false
        button.text = "✨ 点滅停止"
        runBlinkStep(button)
    }

    private fun runBlinkStep(button: Button) {
        if (!blinkRunning) return
        blinkOn = !blinkOn
        if (!torch.setEnabled(blinkOn)) {
            stopBlink(button)
            Toast.makeText(this, "点滅ライトを続けられませんでした", Toast.LENGTH_SHORT).show()
            return
        }
        handler.postDelayed({ runBlinkStep(button) }, 400L)
    }

    private fun stopBlink(button: Button? = findViewById(R.id.btnBlink)) {
        if (!blinkRunning) return
        blinkRunning = false
        handler.removeCallbacksAndMessages(null)
        torch.off()
        button?.text = "✨ 点滅ライト"
        updateTorchText()
    }

    override fun onPause() {
        if (blinkRunning) stopBlink()
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (isFinishing) torch.off()
        torch.close()
        super.onDestroy()
    }
}
