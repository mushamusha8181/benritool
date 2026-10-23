package jp.mushamusha.utilitylight

import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast

class QrMakerActivity : CompactActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = verticalScreen()
        root.addView(titleView("QRコード作成"))
        val input = textInput("URL・文章・Wi-Fi情報など")
        intent.getStringExtra("content")?.let(input::setText)
        root.addView(input)
        val image = ImageView(this).apply {
            adjustViewBounds = true
            setPadding(dp(6), dp(8), dp(6), dp(8))
        }
        root.addView(image, LinearLayout.LayoutParams(-1, dp(260)))
        root.addView(actionButton("QRコードを作成").apply {
            setOnClickListener {
                val value = input.text.toString().trim()
                if (value.isBlank()) {
                    Toast.makeText(this@QrMakerActivity, "内容を入力してください", Toast.LENGTH_SHORT).show()
                } else {
                    try {
                        image.setImageBitmap(makeQrBitmap(value))
                    } catch (_: Exception) {
                        Toast.makeText(this@QrMakerActivity, "QRコードを作成できませんでした", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
        root.addView(bodyView("QRコードは端末内で生成します。外部へ内容は送信しません。", 13f))
        setContentView(ScrollView(this).apply { addView(root) })

        if (input.text.isNotBlank()) {
            runCatching { makeQrBitmap(input.text.toString()) }
                .onSuccess(image::setImageBitmap)
        }
    }
}
