package rocks.pkb.plektra.pinyin

import android.content.Context
import android.os.SystemClock
import android.text.InputType
import android.util.Log
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.Toast

/**
 * Pinyin input for the physical keyboard.
 *
 * Key handling while the Chinese language is active:
 * - letters go into the composition (shown inline in the editor and as candidates in the bar);
 * - Space selects the highlighted candidate (the first one unless moved);
 * - Alt + the keys that carry 1–9 (W E R / S D F / X C V on the Titan 2) select that candidate on the page;
 * - Enter commits the typed letters, Backspace deletes one letter, Back cancels;
 * - D-pad left/right (or a horizontal swipe on the bar or the keyboard) move the highlight,
 *   up/down turn the page;
 * - Alt punctuation commits the highlighted candidate and then the full-width mark.
 *
 * Everything runs on the main thread. The service decides when the controller is [active].
 */
class PinyinController(
    context: Context,
    private val inputConnection: () -> InputConnection?,
    /** Called whenever the candidate bar must change; null hides it. */
    private val onBarChanged: (PinyinBarState?) -> Unit,
) {
    private val appContext = context.applicationContext
    val engine = PinyinEngine(appContext)

    private var frame: PinyinFrame = PinyinFrame.EMPTY
    private var highlighted = 0
    private var lastEditUptime = 0L
    private var failureToastShown = false

    /** Next-word suggestions shown after a commit, while not composing (offline association). */
    private var associations: List<String> = emptyList()
    private var associationHighlight = 0

    /** Lets the user switch association off (Qingshu setting); on by default. */
    var associationEnabled: Boolean = true

    /** Chinese is the current language and the field accepts free text. */
    var active: Boolean = false
        private set

    val isComposing: Boolean get() = active && frame.isComposing

    /** Modifier state the service resolved for the key being handled. */
    data class Modifiers(
        val alt: Boolean,
        val shift: Boolean,
        val ctrlOrMeta: Boolean,
        /** What the key types on the Alt layer (e.g. "1" for W on the Titan 2), if anything. */
        val altText: String?,
        /** A SYM page or similar overlay owns the keyboard. */
        val overlayActive: Boolean,
        /** Alt is locked (double-tapped number lock): the user is typing digits. */
        val altLocked: Boolean = false,
    )

    /** Updates [active] for the current language and field; returns the new value. */
    fun updateActive(chineseSubtype: Boolean, editorInfo: EditorInfo?): Boolean {
        val wanted = chineseSubtype && acceptsPinyin(editorInfo)
        Log.d(TAG, "updateActive chinese=$chineseSubtype inputType=0x${Integer.toHexString(editorInfo?.inputType ?: 0)} -> $wanted")
        if (!wanted && active) abandon(commitLetters = true)
        active = wanted
        if (chineseSubtype) engine.ensureLoaded()
        return active
    }

    /**
     * Handles a key press. Returns true when the key was consumed; false lets the normal keyboard
     * handle it (after the composition was committed if the key would otherwise disturb it).
     */
    fun onKeyDown(keyCode: Int, event: KeyEvent?, mods: Modifiers): Boolean {
        val consumed = handleKey(keyCode, event, mods)
        Log.d(
            TAG,
            "key=${KeyEvent.keyCodeToString(keyCode)} mods=$mods active=$active engine=${engine.state} " +
                "consumed=$consumed preedit='${frame.preedit}' candidates=${frame.candidates.take(5)}"
        )
        return consumed
    }

    private fun handleKey(keyCode: Int, event: KeyEvent?, mods: Modifiers): Boolean {
        if (!active) return false
        // Volume, media, call and similar keys never touch the composition.
        if (event?.isSystem == true) return false
        if (!engine.isReady) {
            if (engine.state == PinyinEngine.State.FAILED) {
                // Don't retry on every key; say why once.
                if (!failureToastShown) {
                    failureToastShown = true
                    Toast.makeText(appContext, "拼音引擎无法载入：${engine.failureReason}", Toast.LENGTH_LONG).show()
                }
            } else {
                engine.ensureLoaded()
            }
            return false
        }
        val composing = frame.isComposing

        if (mods.ctrlOrMeta || mods.overlayActive) {
            if (composing) commitHighlighted()
            return false
        }

        val letter = letterFor(keyCode)
        if (composing) {
            when {
                mods.alt -> return handleAltWhileComposing(mods.altText)
                letter != null -> return apply(engine.push(letter))
                keyCode == KeyEvent.KEYCODE_SPACE -> return selectAbsolute(highlighted)
                keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER ->
                    return apply(engine.commitRaw())
                keyCode == KeyEvent.KEYCODE_DEL -> return apply(engine.backspace())
                keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE -> {
                    cancel()
                    return true
                }
                keyCode == KeyEvent.KEYCODE_DPAD_LEFT -> { step(false); return true }
                keyCode == KeyEvent.KEYCODE_DPAD_RIGHT -> { step(true); return true }
                keyCode == KeyEvent.KEYCODE_DPAD_UP -> { page(false); return true }
                keyCode == KeyEvent.KEYCODE_DPAD_DOWN -> { page(true); return true }
                keyCode == KeyEvent.KEYCODE_APOSTROPHE -> return apply(engine.push('\''))
            }
            val typed = event?.unicodeChar?.takeIf { it != 0 }?.toChar()
            commitHighlighted()
            if (typed != null && isAsciiPunctuation(typed)) {
                commitText(engine.punctuate(typed))
                return true
            }
            return false
        }

        // Not composing. Space or Alt+1–9 picks a shown association; Back and other keys
        // dismiss them (Back then types nothing, so the next Space is a real space).
        if (associations.isNotEmpty()) {
            // With Alt locked for numbers, digits stay digits.
            val digit = if (mods.alt && !mods.altLocked) mods.altText?.singleOrNull() else null
            when {
                digit != null && digit in '1'..'9' -> {
                    associations.getOrNull(digit - '1')?.let { pickAssociation(it) }
                    return true
                }
                keyCode == KeyEvent.KEYCODE_SPACE && !mods.alt && !mods.shift -> {
                    associations.getOrNull(associationHighlight)?.let { pickAssociation(it) }
                    return true
                }
                keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    step(keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)
                    return true
                }
            }
            clearAssociations()
        }
        if (mods.alt) {
            val text = mods.altText
            if (text != null && text.length == 1 && isAsciiPunctuation(text[0])) {
                // Keep "3.14", "1,000" and "10:30" half-width after a digit.
                if (text[0] in ".,:" && previousCharIsDigit()) return false
                commitText(engine.punctuate(text[0]))
                return true
            }
            return false
        }
        if (letter != null) {
            // Shift+letter types an English capital, as on most Chinese keyboards.
            if (mods.shift) return false
            return apply(engine.push(letter))
        }
        val typed = event?.unicodeChar?.takeIf { it != 0 }?.toChar()
        if (typed != null && keyCode != KeyEvent.KEYCODE_SPACE && isAsciiPunctuation(typed)) {
            commitText(engine.punctuate(typed))
            return true
        }
        return false
    }

    private fun handleAltWhileComposing(altText: String?): Boolean {
        val c = altText?.singleOrNull()
        when {
            c == null -> return true // Unmapped Alt chord: ignore rather than break the composition.
            c in '1'..'9' -> {
                val index = pageStart() + (c - '1')
                if (index < frame.candidates.size) selectAbsolute(index)
                return true
            }
            // The 0 key (Alt+Q on the Titan 2) turns to the next page, back to the first after the last.
            c == '0' -> {
                pageWrapping()
                return true
            }
            c == '\'' -> return apply(engine.push('\''))
            isAsciiPunctuation(c) -> {
                commitHighlighted()
                commitText(engine.punctuate(c))
                return true
            }
            else -> {
                commitHighlighted()
                commitText(c.toString())
                return true
            }
        }
    }

    // --- Candidate selection --------------------------------------------------------------------

    fun selectAbsolute(index: Int): Boolean {
        if (!frame.isComposing) return false
        if (index !in frame.candidates.indices) return apply(engine.space())
        return apply(engine.select(index))
    }

    fun selectHighlighted(): Boolean = selectAbsolute(highlighted)

    /** Bar tap: a candidate while composing, an association otherwise. */
    fun onBarTapped(index: Int) {
        if (frame.isComposing) {
            selectAbsolute(index)
        } else {
            associations.getOrNull(index)?.let { pickAssociation(it) }
        }
    }

    private fun pickAssociation(word: String) {
        commitText(word)
        showAssociations()
    }

    val hasAssociations: Boolean get() = active && !frame.isComposing && associations.isNotEmpty()

    /** Hides the associations (Back), so the next Space types a space. */
    fun dismissAssociations() = clearAssociations()

    private fun clearAssociations() {
        if (associations.isEmpty()) return
        associations = emptyList()
        associationHighlight = 0
        publishBar()
    }

    /** Looks up next words for the text now before the cursor and shows them. */
    private fun showAssociations() {
        associations = if (associationEnabled && active && engine.hasAssociation) {
            val before = try {
                inputConnection()?.getTextBeforeCursor(ASSOCIATION_CONTEXT, 0)?.toString().orEmpty()
            } catch (_: Exception) {
                ""
            }
            engine.associate(before, PAGE_SIZE)
        } else {
            emptyList()
        }
        associationHighlight = 0
        publishBar()
    }

    /** Commits the highlighted candidate (or the letters when there is none). */
    fun commitHighlighted() {
        if (!frame.isComposing) return
        selectAbsolute(highlighted)
        // A partial pick leaves the rest composing; finish it the same way.
        var guard = 0
        while (frame.isComposing && guard++ < 16) selectAbsolute(highlighted)
        if (frame.isComposing) apply(engine.commitRaw())
    }

    /** Moves the highlight one candidate left or right; returns whether it moved. */
    fun step(right: Boolean): Boolean {
        if (!frame.isComposing && associations.isNotEmpty()) {
            val target = (associationHighlight + if (right) 1 else -1).coerceIn(0, associations.lastIndex)
            if (target == associationHighlight) return false
            associationHighlight = target
            publishBar()
            return true
        }
        if (!frame.isComposing || frame.candidates.isEmpty()) return false
        val target = (highlighted + if (right) 1 else -1).coerceIn(0, frame.candidates.lastIndex)
        if (target == highlighted) return false
        highlighted = target
        publishBar()
        return true
    }

    fun page(forward: Boolean) {
        if (!frame.isComposing || frame.candidates.isEmpty()) return
        val target = pageStart() + if (forward) PAGE_SIZE else -PAGE_SIZE
        if (target < 0 || target > frame.candidates.lastIndex) return
        highlighted = target
        publishBar()
    }

    private fun pageStart(): Int = (highlighted / PAGE_SIZE) * PAGE_SIZE

    /** Next page, or the first one after the last. */
    private fun pageWrapping() {
        if (!frame.isComposing || frame.candidates.isEmpty()) return
        val next = pageStart() + PAGE_SIZE
        highlighted = if (next > frame.candidates.lastIndex) 0 else next
        publishBar()
    }

    // --- Interruptions --------------------------------------------------------------------------

    /**
     * Ends the composition without the user choosing a candidate (field closed, cursor moved away,
     * language switched). With [commitLetters] the typed letters are left in the editor.
     */
    fun abandon(commitLetters: Boolean) {
        if (!frame.isComposing) {
            frame = PinyinFrame.EMPTY
            publishBar()
            return
        }
        if (commitLetters && inputConnection() != null) {
            apply(engine.commitRaw())
        } else {
            engine.clear()
            frame = PinyinFrame.EMPTY
            highlighted = 0
            publishBar()
        }
    }

    /** Cancels the composition and removes it from the editor. */
    fun cancel() {
        engine.clear()
        frame = PinyinFrame.EMPTY
        highlighted = 0
        inputConnection()?.let { ic ->
            ic.beginBatchEdit()
            ic.setComposingText("", 1)
            ic.finishComposingText()
            ic.endBatchEdit()
        }
        lastEditUptime = SystemClock.uptimeMillis()
        publishBar()
    }

    /**
     * Called from onUpdateSelection. Returns true when the update belongs to the composition (the
     * caller should skip its own selection handling).
     */
    fun onSelectionUpdate(newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int): Boolean {
        val recentOwnEdit = SystemClock.uptimeMillis() - lastEditUptime < OWN_EDIT_WINDOW_MS
        if (!frame.isComposing) {
            if (associations.isNotEmpty() && !recentOwnEdit) clearAssociations()
            return false
        }
        val cursorAtComposingEnd = candidatesEnd >= 0 && newSelStart == newSelEnd && newSelEnd == candidatesEnd
        if (cursorAtComposingEnd || recentOwnEdit) return true
        // The user tapped elsewhere or the app dropped the composing region.
        Log.d(TAG, "Composition interrupted (sel=$newSelStart..$newSelEnd composing=$candidatesStart..$candidatesEnd)")
        if (candidatesEnd >= 0) {
            abandon(commitLetters = true)
        } else {
            engine.clear()
            frame = PinyinFrame.EMPTY
            highlighted = 0
            publishBar()
        }
        return true
    }

    fun onFinishInput() {
        associations = emptyList()
        if (frame.isComposing) {
            engine.clear()
            frame = PinyinFrame.EMPTY
            highlighted = 0
        }
        publishBar()
    }

    fun destroy() {
        engine.destroy()
    }

    // --- Applying frames ------------------------------------------------------------------------

    private fun apply(next: PinyinFrame?): Boolean {
        next ?: return false
        val ic = inputConnection()
        if (ic != null) {
            ic.beginBatchEdit()
            if (next.replace > 0 && next.commit.isNotEmpty()) ic.deleteSurroundingText(next.replace, 0)
            if (next.commit.isNotEmpty()) ic.commitText(next.commit, 1)
            if (next.preedit.isNotEmpty()) {
                ic.setComposingText(next.preedit, 1)
            } else if (frame.isComposing && next.commit.isEmpty()) {
                ic.setComposingText("", 1)
                ic.finishComposingText()
            }
            ic.endBatchEdit()
            lastEditUptime = SystemClock.uptimeMillis()
        }
        val committedAndDone = next.commit.isNotEmpty() && !next.isComposing
        frame = next
        highlighted = 0
        associations = emptyList()
        if (committedAndDone) showAssociations() else publishBar()
        return true
    }

    private fun commitText(text: String) {
        if (text.isEmpty()) return
        inputConnection()?.commitText(text, 1)
        lastEditUptime = SystemClock.uptimeMillis()
    }

    private fun previousCharIsDigit(): Boolean =
        try {
            inputConnection()?.getTextBeforeCursor(1, 0)?.lastOrNull()?.isDigit() == true
        } catch (_: Exception) {
            false
        }

    private fun publishBar() {
        onBarChanged(
            when {
                active && frame.isComposing && frame.candidates.isNotEmpty() ->
                    PinyinBarState(frame.candidates, pageStart(), highlighted)
                // Associations: Space picks the highlighted one (Back dismisses them first).
                active && !frame.isComposing && associations.isNotEmpty() ->
                    PinyinBarState(associations, 0, associationHighlight)
                else -> null
            }
        )
    }

    companion object {
        private const val TAG = "QingshuPinyin"

        /** Candidates per page: one for each of the digit keys 1–9. */
        const val PAGE_SIZE = 9

        /** Selection updates this soon after our own edit are its echo, not a cursor move. */
        private const val OWN_EDIT_WINDOW_MS = 250L

        /** Characters before the cursor used to find the previous word for association. */
        private const val ASSOCIATION_CONTEXT = 16

        fun isChineseLocale(locale: String?): Boolean =
            locale != null && locale.lowercase().let { it == "zh" || it.startsWith("zh_") || it.startsWith("zh-") }

        private fun letterFor(keyCode: Int): Char? =
            if (keyCode in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z) 'a' + (keyCode - KeyEvent.KEYCODE_A) else null

        private fun isAsciiPunctuation(c: Char): Boolean =
            c.code in 0x21..0x7E && !c.isLetterOrDigit()

        /** Passwords, URLs, e-mail addresses and non-text fields bypass the engine, as in Qingjian. */
        fun acceptsPinyin(info: EditorInfo?): Boolean {
            val type = info?.inputType ?: return false
            if (type and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT) return false
            return when (type and InputType.TYPE_MASK_VARIATION) {
                InputType.TYPE_TEXT_VARIATION_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_URI,
                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS -> false
                else -> true
            }
        }
    }
}
