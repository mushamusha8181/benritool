package jp.mushamusha.utilitylight

import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Toast

class WifiQrActivity : CompactActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = verticalScreen()
        root.addView(titleView("Wi-Fi用QRコード"))
        val ssid = textInput("Wi-Fi名（SSID）").apply { maxLines = 1 }
        val password = textInput("パスワード").apply {
            maxLines = 1
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val security = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@WifiQrActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("WPA / WPA2", "WEP", "パスワードなし")
            )
        }
        security.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                password.isEnabled = position != 2
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        val hidden = CheckBox(this).apply {
            text = "非公開SSID（ステルス）"
            setTextColor(android.graphics.Color.WHITE)
            minHeight = dp(48)
        }
        val image = ImageView(this).apply { adjustViewBounds = true }
        root.addView(ssid)
        root.addView(password)
        root.addView(security)
        root.addView(hidden)
        root.addView(image, LinearLayout.LayoutParams(-1, dp(260)))
        root.addView(actionButton("Wi-Fi QRを作成").apply {
            setOnClickListener {
                val name = ssid.text.toString()
                if (name.isBlank()) {
                    Toast.makeText(this@WifiQrActivity, "SSIDを入力してください", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val passwordValue = password.text.toString()
                if (security.selectedItemPosition != 2 && passwordValue.isBlank()) {
                    Toast.makeText(this@WifiQrActivity, "パスワードを入力してください", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val type = when (security.selectedItemPosition) {
                    1 -> "WEP"
                    2 -> "nopass"
                    else -> "WPA"
                }
                val passwordPart = if (type == "nopass") "" else "P:${esc(passwordValue)};"
                val payload = "WIFI:T:$type;S:${esc(name)};$passwordPart" + "H:${hidden.isChecked};;"
                runCatching { makeQrBitmap(payload) }
                    .onSuccess(image::setImageBitmap)
                    .onFailure { Toast.makeText(this@WifiQrActivity, "QRコードを作成できませんでした", Toast.LENGTH_SHORT).show() }
            }
        })
        root.addView(bodyView("SSIDとパスワードは端末内でQR化します。一般的なWPA/WPA2・WEP・暗号化なし向けです。WPA3専用やEnterprise Wi-Fiは、読み取り側端末によって接続できない場合があります。", 12f))
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun esc(value: String): String = value
        .replace("\\", "\\\\")
        .replace(";", "\\;")
        .replace(",", "\\,")
        .replace(":", "\\:")
        .replace("\"", "\\\"")
}
