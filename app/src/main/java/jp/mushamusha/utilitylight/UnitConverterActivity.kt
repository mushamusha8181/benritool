package jp.mushamusha.utilitylight

import android.os.Bundle
import android.text.InputType
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.ScrollView
import android.widget.Toast

class UnitConverterActivity : CompactActivity() {
    private data class Conversion(val convert: (Double) -> Double, val suffix: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = verticalScreen()
        root.addView(titleView("単位変換"))
        val spinner = Spinner(this)
        val items = listOf("cm → inch", "inch → cm", "km → mile", "mile → km", "kg → lb", "lb → kg", "℃ → ℉", "℉ → ℃")
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, items)
        root.addView(spinner)
        val input = textInput("数値を入力").apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
        }
        root.addView(input)
        val output = bodyView("結果", 32f).apply { gravity = 17 }
        root.addView(output, LinearLayout.LayoutParams(-1, dp(96)))
        root.addView(actionButton("変換する").apply {
            setOnClickListener {
                val value = input.text.toString().toDoubleOrNull()
                if (value == null || !value.isFinite()) {
                    Toast.makeText(this@UnitConverterActivity, "有限の数値を入力してください", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val conversion = when (spinner.selectedItemPosition) {
                    0 -> Conversion({ it / 2.54 }, "inch")
                    1 -> Conversion({ it * 2.54 }, "cm")
                    2 -> Conversion({ it * 0.621371 }, "mile")
                    3 -> Conversion({ it / 0.621371 }, "km")
                    4 -> Conversion({ it * 2.2046226218 }, "lb")
                    5 -> Conversion({ it / 2.2046226218 }, "kg")
                    6 -> Conversion({ it * 9 / 5 + 32 }, "℉")
                    else -> Conversion({ (it - 32) * 5 / 9 }, "℃")
                }
                val converted = conversion.convert(value)
                if (!converted.isFinite()) {
                    output.text = "範囲外"
                } else {
                    val formatted = String.format("%.4f", converted).trimEnd('0').trimEnd('.')
                    output.text = "$formatted ${conversion.suffix}"
                }
            }
        })
        setContentView(ScrollView(this).apply { addView(root) })
    }
}
