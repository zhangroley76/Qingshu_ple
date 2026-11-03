package rocks.pkb.plektra.pinyin

import android.view.KeyEvent

/** Maps a keyboard's Fn key (KEYCODE_FUNCTION / META_FUNCTION_ON) to left Ctrl. */
object FnAsCtrl {
    private const val CTRL_BITS = KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON

    /** The remapped key code and event, or null when the event has nothing to do with Fn. */
    fun map(keyCode: Int, event: KeyEvent?): Pair<Int, KeyEvent?>? {
        val isFnKey = keyCode == KeyEvent.KEYCODE_FUNCTION
        val fnHeld = event != null && event.metaState and KeyEvent.META_FUNCTION_ON != 0
        if (!isFnKey && !fnHeld) return null
        val code = if (isFnKey) KeyEvent.KEYCODE_CTRL_LEFT else keyCode
        val original = event ?: return code to null
        val meta = if (fnHeld || (isFnKey && original.action == KeyEvent.ACTION_DOWN)) {
            (original.metaState and KeyEvent.META_FUNCTION_ON.inv()) or CTRL_BITS
        } else {
            original.metaState and KeyEvent.META_FUNCTION_ON.inv()
        }
        return code to KeyEvent(
            original.downTime, original.eventTime, original.action, code, original.repeatCount,
            meta, original.deviceId, original.scanCode, original.flags, original.source
        )
    }

    /** Keys worth logging in every mode: modifiers, unknown keys and Ctrl/Fn/Meta chords. */
    fun isDiagnosticKey(keyCode: Int, event: KeyEvent?): Boolean {
        if (KeyEvent.isModifierKey(keyCode) || keyCode == KeyEvent.KEYCODE_FUNCTION ||
            keyCode == KeyEvent.KEYCODE_UNKNOWN || keyCode == KeyEvent.KEYCODE_SYM
        ) return true
        val meta = event?.metaState ?: 0
        return meta and (KeyEvent.META_CTRL_ON or KeyEvent.META_FUNCTION_ON or KeyEvent.META_META_ON) != 0
    }
}
