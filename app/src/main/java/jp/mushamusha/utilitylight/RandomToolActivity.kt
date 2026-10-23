package jp.mushamusha.utilitylight

import android.os.Bundle
import android.text.InputType
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import kotlin.random.Random

class RandomToolActivity : CompactActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = verticalScreen()
        root.addView(titleView("乱数・サイコロ・くじ引き"))
        val result = bodyView("—", 36f).apply { gravity = 17 }
        root.addView(result, LinearLayout.LayoutParams(-1, dp(72)))

        val rangeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val min = textInput("最小").apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
            setText("1")
        }
        val max = textInput("最大").apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
            setText("100")
        }
        rangeRow.addView(min, LinearLayout.LayoutParams(0, dp(48), 1f))
        rangeRow.addView(max, LinearLayout.LayoutParams(0, dp(48), 1f))
        root.addView(rangeRow)
        root.addView(actionButton("範囲から乱数").apply {
            setOnClickListener {
                val a = min.text.toString().toIntOrNull()
                val b = max.text.toString().toIntOrNull()
                if (a == null || b == null || a > b) {
                    Toast.makeText(this@RandomToolActivity, "範囲を確認してください", Toast.LENGTH_SHORT).show()
                } else {
                    val span = b.toLong() - a.toLong() + 1L
                    result.text = (a.toLong() + Random.nextLong(span)).toString()
                }
            }
        })

        val quick = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        quick.addView(actionButton("🎲 サイコロ").apply { setOnClickListener { result.text = Random.nextInt(1, 7).toString() } }, LinearLayout.LayoutParams(0, dp(48), 1f))
        quick.addView(actionButton("YES / NO").apply { setOnClickListener { result.text = if (Random.nextBoolean()) "YES" else "NO" } }, LinearLayout.LayoutParams(0, dp(48), 1f))
        root.addView(quick)

        val choices = textInput("候補を1行ずつ入力\n例：ラーメン\nカレー\n寿司").apply { minLines = 4 }
        root.addView(choices)
        root.addView(actionButton("候補から1つ選ぶ").apply {
            setOnClickListener {
                val list = choices.text.toString().lines().map(String::trim).filter(String::isNotBlank)
                result.text = if (list.isEmpty()) "候補なし" else list.random()
            }
        })
        setContentView(ScrollView(this).apply { addView(root) })
    }
}
