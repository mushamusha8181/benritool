package jp.mushamusha.utilitylight

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.security.MessageDigest

class ShortUrlActivity : CompactActivity() {
    private lateinit var input: EditText
    private lateinit var output: TextView
    private lateinit var history: TextView
    private lateinit var shortenButton: Button
    private var latestRequestId = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = verticalScreen()
        root.addView(titleView("短縮URL作成"))
        root.addView(bodyView("入力したURLは、短縮ボタンを押した時だけ is.gd にHTTPS送信します。同じURLは端末内キャッシュを優先します。", 13f))
        input = textInput("https://example.com/...")
        if (intent?.action == Intent.ACTION_SEND) intent.getStringExtra(Intent.EXTRA_TEXT)?.let(input::setText)
        root.addView(input)
        output = bodyView("", 18f)
        root.addView(output)
        shortenButton = actionButton("短縮する").apply { setOnClickListener { confirmAndShorten() } }
        root.addView(shortenButton)

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(actionButton("コピー").apply { setOnClickListener { copyOutput() } }, LinearLayout.LayoutParams(0, dp(48), 1f))
        row.addView(actionButton("共有").apply { setOnClickListener { shareOutput() } }, LinearLayout.LayoutParams(0, dp(48), 1f))
        row.addView(actionButton("QR化").apply {
            setOnClickListener {
                val value = output.text.toString()
                if (value.startsWith("http")) {
                    startActivity(Intent(this@ShortUrlActivity, QrMakerActivity::class.java).putExtra("content", value))
                }
            }
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        root.addView(row)
        root.addView(bodyView("履歴", 17f))
        history = bodyView(loadHistory(), 13f)
        root.addView(history)
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun confirmAndShorten() {
        var value = input.text.toString().trim()
        if (value.isBlank()) {
            Toast.makeText(this, "URLを入力してください", Toast.LENGTH_SHORT).show()
            return
        }
        if (!value.startsWith("http://", ignoreCase = true) && !value.startsWith("https://", ignoreCase = true)) {
            value = "https://$value"
        }
        if (!isValidHttpUrl(value)) {
            Toast.makeText(this, "http / https のURLを確認してください", Toast.LENGTH_SHORT).show()
            return
        }

        val cached = getCached(value)
        if (!cached.isNullOrBlank()) {
            output.text = cached
            Toast.makeText(this, "端末内の作成済みURLを使用しました", Toast.LENGTH_SHORT).show()
            return
        }

        val urlValue = value
        AlertDialog.Builder(this)
            .setTitle("外部サービスへ送信")
            .setMessage("このURLを is.gd に送信して短縮します。\n\n$urlValue\n\n※ 作成した短縮URLは、is.gd側の仕様上あとから変更・削除できません。")
            .setNegativeButton("やめる", null)
            .setPositiveButton("短縮する") { _, _ -> doShorten(urlValue) }
            .show()
    }

    private fun isValidHttpUrl(value: String): Boolean = try {
        val parsed = URL(value)
        (parsed.protocol == "http" || parsed.protocol == "https") && parsed.host.isNotBlank()
    } catch (_: Exception) {
        false
    }

    private fun doShorten(value: String) {
        val requestId = ++latestRequestId
        shortenButton.isEnabled = false
        output.text = "短縮中…"
        Thread {
            var connection: HttpURLConnection? = null
            try {
                val endpoint = "https://is.gd/create.php?format=simple&url=" + URLEncoder.encode(value, "UTF-8")
                connection = URL(endpoint).openConnection() as HttpURLConnection
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                connection.setRequestProperty("User-Agent", "BenriTool/1.5 Android")
                val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.bufferedReader()?.use { it.readText() }?.trim().orEmpty()
                applyRequestResult(requestId) {
                    if (body.startsWith("http")) {
                        output.text = body
                        putCached(value, body)
                        saveHistory(value, body)
                        history.text = loadHistory()
                    } else {
                        output.text = body.ifBlank { "短縮できませんでした" }
                    }
                }
            } catch (_: Exception) {
                applyRequestResult(requestId) { output.text = "通信エラーで短縮できませんでした" }
            } finally {
                connection?.disconnect()
                applyRequestResult(requestId) { shortenButton.isEnabled = true }
            }
        }.start()
    }

    private fun applyRequestResult(requestId: Int, block: () -> Unit) {
        runOnUiThread {
            if (requestId == latestRequestId && !isFinishing && !isDestroyed) block()
        }
    }

    private fun cacheKey(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return "cache_" + digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun getCached(source: String): String? =
        getSharedPreferences("short_url", MODE_PRIVATE).getString(cacheKey(source), null)

    private fun putCached(source: String, short: String) {
        getSharedPreferences("short_url", MODE_PRIVATE).edit().putString(cacheKey(source), short).apply()
    }

    private fun copyOutput() {
        val value = output.text.toString()
        if (!value.startsWith("http")) return
        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("short url", value))
        Toast.makeText(this, "コピーしました", Toast.LENGTH_SHORT).show()
    }

    private fun shareOutput() {
        val value = output.text.toString()
        if (!value.startsWith("http")) return
        shareTextSafely(value)
    }

    private fun saveHistory(source: String, short: String) {
        val prefs = getSharedPreferences("short_url", MODE_PRIVATE)
        val old = prefs.getString("history", "").orEmpty()
        val line = "$short\n↳ $source"
        prefs.edit().putString("history", (line + "\n\n" + old).take(6000)).apply()
    }

    private fun loadHistory(): String =
        getSharedPreferences("short_url", MODE_PRIVATE).getString("history", "まだ履歴はありません").orEmpty()

    override fun onDestroy() {
        latestRequestId++
        super.onDestroy()
    }
}
