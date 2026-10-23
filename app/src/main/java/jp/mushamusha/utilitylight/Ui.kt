package jp.mushamusha.utilitylight

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.ViewGroup
import android.widget.*

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

fun Context.verticalScreen(padding: Int = 12): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    val p = dp(padding)
    setPadding(p, p, p, p)
    setBackgroundColor(Color.rgb(16,17,20))
}

fun Context.titleView(text: String): TextView = TextView(this).apply {
    this.text = text
    textSize = 22f
    setTextColor(Color.WHITE)
    setPadding(0, dp(1), 0, dp(8))
}

fun Context.bodyView(text: String = "", size: Float = 16f): TextView = TextView(this).apply {
    this.text = text
    textSize = size
    setTextColor(Color.WHITE)
    setPadding(0, dp(4), 0, dp(4))
}

fun Context.actionButton(text: String): Button = Button(this).apply {
    this.text = text
    isAllCaps = false
    textSize = 14f
    minHeight = dp(48)
    minimumHeight = dp(48)
    setPadding(dp(8), dp(1), dp(8), dp(1))
    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply {
        topMargin = dp(2)
        bottomMargin = dp(2)
    }
}

fun Context.textInput(hint: String): EditText = EditText(this).apply {
    this.hint = hint
    textSize = 15f
    minHeight = dp(48)
    setTextColor(Color.WHITE)
    setHintTextColor(Color.LTGRAY)
    setSingleLine(false)
    minLines = 1
    maxLines = 5
    setPadding(dp(8), dp(4), dp(8), dp(4))
}

fun Activity.shareTextSafely(value: String, chooserTitle: String = "共有") {
    try {
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, value)
        }, chooserTitle))
    } catch (_: Exception) {
        Toast.makeText(this, "共有できるアプリがありません", Toast.LENGTH_SHORT).show()
    }
}
