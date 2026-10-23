package jp.mushamusha.utilitylight

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.Toast

class CounterActivity : CompactActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("counter", MODE_PRIVATE)
        var count = prefs.getLong("n", 0L)
        val root = verticalScreen()
        root.addView(titleView("カウンター"))
        val value = bodyView(count.toString(), 72f).apply { gravity = 17 }
        root.addView(value, LinearLayout.LayoutParams(-1, 0, 1f))

        fun update(newValue: Long) {
            count = newValue
            value.text = count.toString()
            prefs.edit().putLong("n", count).apply()
        }

        root.addView(actionButton("＋ 1").apply {
            setOnClickListener {
                if (count == Long.MAX_VALUE) Toast.makeText(this@CounterActivity, "これ以上増やせません", Toast.LENGTH_SHORT).show()
                else update(count + 1L)
            }
        })
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(actionButton("− 1").apply {
            setOnClickListener {
                if (count == Long.MIN_VALUE) Toast.makeText(this@CounterActivity, "これ以上減らせません", Toast.LENGTH_SHORT).show()
                else update(count - 1L)
            }
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        row.addView(actionButton("リセット").apply { setOnClickListener { update(0L) } }, LinearLayout.LayoutParams(0, dp(48), 1f))
        root.addView(row)
        setContentView(root)
    }
}
