package jp.mushamusha.utilitylight

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.SeekBar

class RulerActivity : CompactActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("ruler", MODE_PRIVATE)
        val root = verticalScreen(10)
        root.addView(titleView("定規・校正"))
        root.addView(bodyView("端末のDPI情報には誤差があるため、最初に実物で校正すると精度が上がります。", 13f))

        val calibration = prefs.getFloat("cal", 1f).coerceIn(.5f, 1.5f)
        val coin = CoinCalibrationView(this).apply { this.calibration = calibration }
        root.addView(coin, LinearLayout.LayoutParams(-1, dp(170)))
        root.addView(bodyView("10円硬貨を画面に重ね、白い円と同じ大きさになるようスライダーを調整してください。", 12f))

        val ruler = RulerView(this).apply { this.calibration = calibration }
        root.addView(ruler, LinearLayout.LayoutParams(-1, 0, 1f))
        val label = bodyView("校正 ${String.format("%.1f", calibration * 100)}%", 15f)
        root.addView(label)

        val seek = SeekBar(this).apply {
            max = 1000
            progress = ((calibration - .5f) * 1000f).toInt().coerceIn(0, 1000)
        }
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = .5f + progress / 1000f
                ruler.calibration = value
                coin.calibration = value
                label.text = "校正 ${String.format("%.1f", value * 100)}%"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                prefs.edit().putFloat("cal", ruler.calibration).apply()
            }
        })
        root.addView(seek)
        root.addView(actionButton("100%に戻す").apply {
            setOnClickListener {
                ruler.calibration = 1f
                coin.calibration = 1f
                seek.progress = 500
                prefs.edit().putFloat("cal", 1f).apply()
            }
        })
        setContentView(root)
    }
}
