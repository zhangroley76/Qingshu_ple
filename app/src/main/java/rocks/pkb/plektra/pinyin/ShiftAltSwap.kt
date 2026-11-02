package rocks.pkb.plektra.pinyin

import android.view.KeyEvent

/**
 * Swaps the Shift and Alt keys: their key codes, and the Shift/Alt bits in the meta state of
 * every event, so "Shift held + letter" arrives as "Alt held + letter" and vice versa.
 */
object ShiftAltSwap {
    private const val SHIFT_BITS =
        KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON or KeyEvent.META_SHIFT_RIGHT_ON
    private const val ALT_BITS =
        KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON or KeyEvent.META_ALT_RIGHT_ON

    fun swapKeyCode(keyCode: Int): Int = when (keyCode) {
        KeyEvent.KEYCODE_SHIFT_LEFT -> KeyEvent.KEYCODE_ALT_LEFT
        KeyEvent.KEYCODE_SHIFT_RIGHT -> KeyEvent.KEYCODE_ALT_RIGHT
        KeyEvent.KEYCODE_ALT_LEFT -> KeyEvent.KEYCODE_SHIFT_LEFT
        KeyEvent.KEYCODE_ALT_RIGHT -> KeyEvent.KEYCODE_SHIFT_RIGHT
        else -> keyCode
    }

    fun swapMetaState(meta: Int): Int {
        var result = meta and (SHIFT_BITS or ALT_BITS).inv()
        if (meta and KeyEvent.META_SHIFT_ON != 0) result = result or KeyEvent.META_ALT_ON
        if (meta and KeyEvent.META_SHIFT_LEFT_ON != 0) result = result or KeyEvent.META_ALT_LEFT_ON
        if (meta and KeyEvent.META_SHIFT_RIGHT_ON != 0) result = result or KeyEvent.META_ALT_RIGHT_ON
        if (meta and KeyEvent.META_ALT_ON != 0) result = result or KeyEvent.META_SHIFT_ON
        if (meta and KeyEvent.META_ALT_LEFT_ON != 0) result = result or KeyEvent.META_SHIFT_LEFT_ON
        if (meta and KeyEvent.META_ALT_RIGHT_ON != 0) result = result or KeyEvent.META_SHIFT_RIGHT_ON
        return result
    }

    fun swap(keyCode: Int, event: KeyEvent?): Pair<Int, KeyEvent?> {
        val code = swapKeyCode(keyCode)
        val original = event ?: return code to null
        val meta = swapMetaState(original.metaState)
        if (code == original.keyCode && meta == original.metaState) return code to original
        val swapped = KeyEvent(
            original.downTime,
            original.eventTime,
            original.action,
            code,
            original.repeatCount,
            meta,
            original.deviceId,
            original.scanCode,
            original.flags,
            original.source
        )
        return code to swapped
    }
}
