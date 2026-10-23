package jp.mushamusha.utilitylight

import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar

class ScreenLightActivity : CompactActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setBrightness(1f)
        val root = verticalScreen()
        root.setBackgroundColor(Color.WHITE)
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10),dp(10),dp(10),dp(10))
            setBackgroundColor(0xAA000000.toInt())
        }
        controls.addView(bodyView("画面ライト　色と明るさ", 18f))
        val seek = SeekBar(this).apply { max = 100; progress = 100 }
        seek.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) = setBrightness((p.coerceAtLeast(5))/100f)
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        controls.addView(seek)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun addColor(name:String, color:Int){ row.addView(Button(this).apply { text=name; minHeight=dp(48); textSize=14f; setOnClickListener { root.setBackgroundColor(color) } }, LinearLayout.LayoutParams(0, -2, 1f)) }
        addColor("白", Color.WHITE); addColor("暖色", 0xFFFFE0B2.toInt()); addColor("赤", 0xFFFF5555.toInt())
        controls.addView(row)
        root.addView(controls)
        setContentView(root)
    }
    private fun setBrightness(v: Float) {
        window.attributes = window.attributes.apply { screenBrightness = v }
    }
}
