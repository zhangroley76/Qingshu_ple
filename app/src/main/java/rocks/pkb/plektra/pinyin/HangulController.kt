package rocks.pkb.plektra.pinyin

import android.os.SystemClock
import android.view.KeyEvent
import android.view.inputmethod.InputConnection

/**
 * Korean input on the physical keyboard (2-set layout): letters compose Hangul syllables shown as
 * composing text; Backspace removes one stroke; any other key finishes the syllable and then
 * does its usual job (space, punctuation, digits, shortcuts).
 */
class HangulController(private val inputConnection: () -> InputConnection?) {
    private val composer = HangulComposer()
    private var lastEditUptime = 0L

    var active: Boolean = false
        private set

    val isComposing: Boolean get() = !composer.isEmpty

    fun updateActive(on: Boolean) {
        if (!on && active) finish()
        active = on
    }

    /** Returns true when the key was consumed. */
    fun onKeyDown(keyCode: Int, shift: Boolean, alt: Boolean, ctrlOrMeta: Boolean, overlayActive: Boolean): Boolean {
        if (!active) return false
        if (ctrlOrMeta || alt || overlayActive) {
            finish()
            return false
        }
        if (keyCode in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z) {
            val key = 'a' + (keyCode - KeyEvent.KEYCODE_A)
            val committed = composer.feed(key, shift)
            show(committed)
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_DEL && composer.backspace()) {
            show("")
            return true
        }
        finish()
        return false
    }

    /** Commits the syllable being typed. */
    fun finish() {
        if (composer.isEmpty) return
        val text = composer.finish()
        inputConnection()?.let { ic ->
            ic.beginBatchEdit()
            ic.setComposingText(text, 1)
            ic.finishComposingText()
            ic.endBatchEdit()
        }
        lastEditUptime = SystemClock.uptimeMillis()
    }

    /** The cursor moved by itself (tap elsewhere): keep the text, stop composing. */
    fun onSelectionUpdate(candidatesEnd: Int, newSelStart: Int, newSelEnd: Int): Boolean {
        if (composer.isEmpty) return false
        val own = SystemClock.uptimeMillis() - lastEditUptime < OWN_EDIT_WINDOW_MS
        val atEnd = candidatesEnd >= 0 && newSelStart == newSelEnd && newSelEnd == candidatesEnd
        if (own || atEnd) return true
        composer.clear()
        inputConnection()?.finishComposingText()
        return true
    }

    fun onFinishInput() {
        composer.clear()
    }

    private fun show(committed: String) {
        val ic = inputConnection() ?: return
        ic.beginBatchEdit()
        if (committed.isNotEmpty()) {
            ic.setComposingText(committed, 1)
            ic.finishComposingText()
        }
        val composing = composer.composing
        ic.setComposingText(composing, 1)
        if (composing.isEmpty()) ic.finishComposingText()
        ic.endBatchEdit()
        lastEditUptime = SystemClock.uptimeMillis()
    }

    private companion object {
        const val OWN_EDIT_WINDOW_MS = 250L
    }
}
