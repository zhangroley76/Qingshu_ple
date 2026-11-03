package rocks.pkb.plektra.pinyin

import android.view.KeyEvent

/**
 * Makes a keyboard's Fn key act as left Ctrl, so Nav Mode (Fn + E/S/D/F arrows) works with it.
 *
 * The Titan 2 Elite's Fn key sends the vendor key code 403 (scan code 251) and adds no meta
 * state to other keys, so the held state is tracked here and Ctrl is added to every key pressed
 * while Fn is down. KEYCODE_FUNCTION / META_FUNCTION_ON from other keyboards is handled the same way.
 */
object FnAsCtrl {
    /** Vendor key code of the Titan 2 Elite Fn key. */
    const val TITAN_FN_KEYCODE = 403

    private const val CTRL_BITS = KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON

    /** Fn is physically down (between its key-down and key-up). */
    @Volatile
    var held: Boolean = false
        private set

    fun isFnKey(keyCode: Int): Boolean =
        keyCode == TITAN_FN_KEYCODE || keyCode == KeyEvent.KEYCODE_FUNCTION

    /** Forget a held Fn whose key-up may have gone to another window. */
    fun reset() {
        held = false
    }

    /** The remapped key code and event, or null when the event has nothing to do with Fn. */
    fun map(keyCode: Int, event: KeyEvent?): Pair<Int, KeyEvent?>? {
        val fnKey = isFnKey(keyCode)
        if (fnKey && event != null) {
            held = event.action == KeyEvent.ACTION_DOWN
        }
        val fnMeta = event != null && event.metaState and KeyEvent.META_FUNCTION_ON != 0
        if (!fnKey && !fnMeta && !held) return null
        val code = if (fnKey) KeyEvent.KEYCODE_CTRL_LEFT else keyCode
        val original = event ?: return code to null
        val ctrlActive = held || fnMeta || (fnKey && original.action == KeyEvent.ACTION_DOWN)
        val baseMeta = original.metaState and KeyEvent.META_FUNCTION_ON.inv()
        val meta = if (ctrlActive) baseMeta or CTRL_BITS else baseMeta
        return code to KeyEvent(
            original.downTime, original.eventTime, original.action, code, original.repeatCount,
            meta, original.deviceId, original.scanCode, original.flags, original.source
        )
    }

    /** Keys worth logging in every mode: modifiers, Fn, unknown keys and Ctrl/Fn/Meta chords. */
    fun isDiagnosticKey(keyCode: Int, event: KeyEvent?): Boolean {
        if (KeyEvent.isModifierKey(keyCode) || isFnKey(keyCode) ||
            keyCode == KeyEvent.KEYCODE_UNKNOWN || keyCode == KeyEvent.KEYCODE_SYM ||
            keyCode > KeyEvent.getMaxKeyCode()
        ) return true
        val meta = event?.metaState ?: 0
        return meta and (KeyEvent.META_CTRL_ON or KeyEvent.META_FUNCTION_ON or KeyEvent.META_META_ON) != 0
    }
}
