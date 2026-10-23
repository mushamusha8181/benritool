package jp.mushamusha.utilitylight

import android.graphics.Color
import android.os.Bundle
import android.widget.CheckBox
import android.widget.ScrollView

class FavoritesActivity : CompactActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = verticalScreen()
        root.addView(titleView("お気に入り編集"))
        root.addView(bodyView("トップ画面の上部に表示したい機能を選んでください。", 13f))
        val prefs = getSharedPreferences("favorites", MODE_PRIVATE)
        val selected = prefs.getStringSet("keys", ToolCatalog.defaultFavorites)?.toMutableSet() ?: ToolCatalog.defaultFavorites.toMutableSet()
        ToolCatalog.tools.forEach { tool ->
            root.addView(CheckBox(this).apply {
                text = tool.label
                textSize = 15f
                setTextColor(Color.WHITE)
                minHeight = dp(48)
                isChecked = tool.key in selected
                setOnCheckedChangeListener { _, checked ->
                    if (checked) selected += tool.key else selected -= tool.key
                    prefs.edit().putStringSet("keys", selected.toSet()).apply()
                }
            })
        }
        setContentView(ScrollView(this).apply { addView(root) })
    }
}
