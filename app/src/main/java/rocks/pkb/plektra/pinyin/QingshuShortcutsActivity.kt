package rocks.pkb.plektra.pinyin

import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import rocks.pkb.plektra.SettingsManager
import rocks.pkb.plektra.data.mappings.KeyMappingLoader

/**
 * Shortcut help, opened by typing "/?" in any text field (no Fn chord, so apps keep theirs): Qingshu's Chinese keys and the current
 * Fn (Nav Mode) mappings, read from the user's own mapping so customisations show up. Any key or
 * a tap closes it.
 */
class QingshuShortcutsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val fg = if (dark) 0xFFECECEC.toInt() else 0xFF1C1C1C.toInt()
        val dim = if (dark) 0xFFA8A8A8.toInt() else 0xFF5F5F5F.toInt()
        val bg = if (dark) 0xFF262626.toInt() else 0xFFFDFDFD.toInt()

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(14))
        }
        fun heading(text: String) = column.addView(TextView(this).apply {
            this.text = text
            setTextColor(fg)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(10), 0, dp(4))
        })
        fun row(keys: String, action: String) = column.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(2), 0, dp(2))
            addView(TextView(context).apply {
                text = keys
                setTextColor(fg)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                typeface = Typeface.MONOSPACE
                layoutParams = LinearLayout.LayoutParams(dp(132), ViewGroup.LayoutParams.WRAP_CONTENT)
            })
            addView(TextView(context).apply {
                text = action
                setTextColor(fg)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
        })

        val swapped = SettingsManager.getSwapShiftAlt(this)
        val alt = if (swapped) "Shift键" else "Alt"
        heading("中文")
        row("Shift + 空格", "切换语言：EN / 中 / 粵 / 한 / あ（粵、한、あ 要在设置里开启）")
        row("空格", "上屏高亮候选 / 选联想")
        row("$alt + W E R", "选第 1 2 3 个候选")
        row("$alt + S D F", "选第 4 5 6 个")
        row("$alt + X C V", "选第 7 8 9 个")
        row("$alt + P", "下一页候选")
        row("回车", "上屏打的字母")
        row("Back", "取消组字 / 关闭联想")
        row("$alt + 标点", "全角中文标点")
        row("SYM", "符号页（第一页中文标点）")
        row("有联想时 $alt + 数字键", "选联想；没有对应的联想就直接打数字")
        row("有联想时要打数字", "先按 Back，或用 $alt + 0，或连按两下 $alt 锁定数字")
        if (swapped) {
            column.addView(TextView(this).apply {
                text = "已开启 Shift/Alt 对调：上面的「Shift键」是键盘上原本的 Alt 键。"
                setTextColor(dim)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            })
        }

        heading("英文")
        row("纠正后马上按退格", "还原成原来打的字（同一个字还原两次会记住）")
        row("自动大写后按退格", "删掉后再打就是小写")
        row("打字母前按一下 Shift", "取消这次自动大写")

        val holdNav = SettingsManager.getNavModeCtrlHoldEnabled(this)
        heading(if (holdNav) "按住 Fn" else "点一下 Fn（或连点两下锁定）")
        val mappings = KeyMappingLoader.loadCtrlKeyMappings(assets, this)
        val shown = mappings.entries
            .mapNotNull { (keyCode, mapping) -> describe(mapping)?.let { keyLabel(keyCode) to it } }
            .sortedBy { (key, _) -> KEY_ORDER.indexOf(key).let { if (it < 0) 99 else it } }
        for ((key, action) in shown) row("Fn + $key", action)
        row("Fn + Shift + 方向", "一边移动一边选取")
        row("打 /?", "在任何输入框叫出这个说明")
        if (!holdNav) {
            column.addView(TextView(this).apply {
                text = "想按住 Fn 直接用：设置 → Nav Mode → Ctrl-hold navigation。"
                setTextColor(dim)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            })
        }
        column.addView(TextView(this).apply {
            text = "按任意键或点一下关闭"
            setTextColor(dim)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, dp(12), 0, 0)
        })

        val scroll = ScrollView(this).apply {
            addView(column)
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(bg)
            }
            setOnClickListener { finish() }
        }
        column.setOnClickListener { finish() }
        setContentView(scroll)
        window.setLayout(
            (resources.displayMetrics.widthPixels * 0.94).toInt(),
            (resources.displayMetrics.heightPixels * 0.85).toInt()
        )
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        finish()
        return true
    }

    private fun describe(mapping: KeyMappingLoader.CtrlMapping): String? = when (mapping.type) {
        "keycode" -> when (mapping.value) {
            "DPAD_UP" -> "上"
            "DPAD_DOWN" -> "下"
            "DPAD_LEFT" -> "左"
            "DPAD_RIGHT" -> "右"
            "DPAD_CENTER" -> "确认"
            "PAGE_UP" -> "上一页"
            "PAGE_DOWN" -> "下一页"
            "MOVE_HOME" -> "行首"
            "MOVE_END" -> "行尾"
            "TAB" -> "Tab"
            "ESCAPE" -> "Esc"
            "FORWARD_DEL" -> "向后删除"
            else -> mapping.value.lowercase()
        }
        "action" -> when (mapping.value) {
            "expand_selection_left" -> "往左多选一个字"
            "expand_selection_right" -> "往右多选一个字"
            "expand_selection_word_left" -> "往左多选一个词"
            "expand_selection_word_right" -> "往右多选一个词"
            "move_word_left" -> "光标往左跳一个词（加 Shift 选取）"
            "move_word_right" -> "光标往右跳一个词（加 Shift 选取）"
            "select_all" -> "全选"
            "copy" -> "复制"
            "cut" -> "剪下"
            "paste" -> "贴上"
            "undo" -> "撤销"
            "page_start" -> "到开头"
            "page_end" -> "到结尾"
            "toggle_minimal_ui" -> "精简界面开关"
            "media_play_pause" -> "播放 / 暂停"
            "media_next" -> "下一首"
            "media_previous" -> "上一首"
            else -> mapping.value
        }
        "command" -> "指令：${mapping.value.substringAfterLast('.')}"
        "native_ctrl" -> "交给 App 的 Ctrl 快捷键"
        else -> null
    }

    private fun keyLabel(keyCode: Int): String =
        KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_")

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

    private companion object {
        val KEY_ORDER = "E S D F W R U I N M A Z X C V Q T O Y H J K L P B G".split(' ')
    }
}
