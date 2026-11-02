package rocks.pkb.plektra.pinyin

import android.view.KeyEvent

/** The SYM page used while Chinese is active: full-width punctuation on the letter keys. */
object ChineseSymbols {
    val MAPPINGS: Map<Int, String> = mapOf(
        // Top row
        KeyEvent.KEYCODE_Q to "，",
        KeyEvent.KEYCODE_W to "。",
        KeyEvent.KEYCODE_E to "、",
        KeyEvent.KEYCODE_R to "；",
        KeyEvent.KEYCODE_T to "：",
        KeyEvent.KEYCODE_Y to "？",
        KeyEvent.KEYCODE_U to "！",
        KeyEvent.KEYCODE_I to "“",
        KeyEvent.KEYCODE_O to "”",
        KeyEvent.KEYCODE_P to "……",
        // Home row
        KeyEvent.KEYCODE_A to "《",
        KeyEvent.KEYCODE_S to "》",
        KeyEvent.KEYCODE_D to "【",
        KeyEvent.KEYCODE_F to "】",
        KeyEvent.KEYCODE_G to "（",
        KeyEvent.KEYCODE_H to "）",
        KeyEvent.KEYCODE_J to "「",
        KeyEvent.KEYCODE_K to "」",
        KeyEvent.KEYCODE_L to "——",
        // Bottom row
        KeyEvent.KEYCODE_Z to "‘",
        KeyEvent.KEYCODE_X to "’",
        KeyEvent.KEYCODE_C to "·",
        KeyEvent.KEYCODE_V to "～",
        KeyEvent.KEYCODE_B to "￥",
        KeyEvent.KEYCODE_N to "『",
        KeyEvent.KEYCODE_M to "』",
    )
}
