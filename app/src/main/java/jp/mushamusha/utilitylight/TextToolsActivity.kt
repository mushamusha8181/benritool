package jp.mushamusha.utilitylight

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.*
import java.net.URLDecoder
import java.net.URLEncoder

class TextToolsActivity : CompactActivity() {
    private lateinit var input: EditText
    private lateinit var count: TextView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = verticalScreen()
        root.addView(titleView("文字ツール"))
        input = textInput("ここに文章を入力").apply { minLines = 5; maxLines = 10 }
        count = bodyView("0文字 / 0行", 13f)
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = updateCount()
            override fun afterTextChanged(s: Editable?) {}
        })
        root.addView(input); root.addView(count)
        addPair(root, "大文字", "小文字") { which -> input.setText(if (which == 0) input.text.toString().uppercase() else input.text.toString().lowercase()) }
        addPair(root, "全角ASCII", "半角ASCII") { which -> input.setText(if (which == 0) toFullWidth(input.text.toString()) else toHalfWidth(input.text.toString())) }
        addPair(root, "改行を削除", "空白を整理") { which -> input.setText(if (which == 0) input.text.toString().replace(Regex("[\\r\\n]+"), " ") else input.text.toString().replace(Regex("[ \\t]+"), " ").trim()) }
        addPair(root, "URLエンコード", "URLデコード") { which ->
            runCatching {
                input.setText(if (which == 0) URLEncoder.encode(input.text.toString(), "UTF-8") else URLDecoder.decode(input.text.toString(), "UTF-8"))
            }.onFailure { Toast.makeText(this, "変換できませんでした", Toast.LENGTH_SHORT).show() }
        }
        root.addView(actionButton("全文をコピー").apply { setOnClickListener {
            (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("text", input.text.toString()))
            Toast.makeText(this@TextToolsActivity, "コピーしました", Toast.LENGTH_SHORT).show()
        }})
        setContentView(ScrollView(this).apply { addView(root) })
        updateCount()
    }

    private fun addPair(root: LinearLayout, left: String, right: String, action: (Int) -> Unit) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(actionButton(left).apply { setOnClickListener { action(0) } }, LinearLayout.LayoutParams(0, dp(48), 1f))
        row.addView(actionButton(right).apply { setOnClickListener { action(1) } }, LinearLayout.LayoutParams(0, dp(48), 1f))
        root.addView(row)
    }

    private fun updateCount() {
        val s = input.text?.toString().orEmpty()
        val lines = if (s.isEmpty()) 0 else s.count { it == '\n' } + 1
        count.text = "${s.length}文字 / ${lines}行"
    }

    private fun toFullWidth(s: String): String = buildString {
        s.forEach { c -> append(when (c.code) { 0x20 -> '\u3000'; in 0x21..0x7E -> (c.code + 0xFEE0).toChar(); else -> c }) }
    }
    private fun toHalfWidth(s: String): String = buildString {
        s.forEach { c -> append(when (c.code) { 0x3000 -> ' '; in 0xFF01..0xFF5E -> (c.code - 0xFEE0).toChar(); else -> c }) }
    }
}
