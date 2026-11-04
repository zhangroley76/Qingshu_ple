package rocks.pkb.plektra.pinyin

import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import org.json.JSONObject

/**
 * Cantonese input: toneless Jyutping letters ("neihou", "mgoi") converted to Cantonese words from
 * the rime-cantonese lexicon, word by word. Keys work as in pinyin: Space takes the highlighted
 * candidate, Alt+W…V picks 1–9, Alt+P turns the page, Enter commits the letters, Backspace removes
 * one, Back cancels. When the letters are longer than one word ("ngodeisikfaan"), the longest
 * first word is offered and the rest keeps composing after it is picked. Initials alone work for
 * 2–4 syllable words ("nh" → 你好). Output is traditional characters, as written in Hong Kong.
 */
class CantoneseController(
    context: Context,
    private val inputConnection: () -> InputConnection?,
    private val onBarChanged: (PinyinBarState?) -> Unit,
) : WordInputController {
    private val appContext = context.applicationContext

    /** The Jyutping letters typed so far. */
    private var letters = ""

    private data class Candidate(val text: String, val consumes: Int)

    private var candidates: List<Candidate> = emptyList()
    private var highlighted = 0
    private var pageStart = 0
    private var lastEditUptime = 0L

    override var active: Boolean = false
        private set

    override val isComposing: Boolean get() = letters.isNotEmpty()

    /** A URL field (browser address bar): punctuation stays half-width. */
    private var halfWidthPunctuation = false

    override fun updateActive(on: Boolean, urlField: Boolean) {
        if (!on && active) commitLetters()
        active = on
        halfWidthPunctuation = urlField
        if (on) CantoneseDictionary.get(appContext) { if (isComposing) refresh() }
    }

    private fun mark(c: Char): String? = if (halfWidthPunctuation) null else PUNCTUATION_MAP[c]

    override fun onKeyDown(keyCode: Int, event: KeyEvent?, mods: WordInputController.Mods): Boolean {
        if (!active) return false
        if (mods.ctrlOrMeta || mods.overlayActive) {
            if (isComposing) commitHighlighted()
            return false
        }
        val letter = if (keyCode in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z) 'a' + (keyCode - KeyEvent.KEYCODE_A) else null
        if (mods.alt) return handleAlt(keyCode, mods.altText)
        if (letter != null) {
            // Shift+letter types an English capital, as in pinyin.
            if (!isComposing && event?.isShiftPressed == true) return false
            letters += letter
            refresh()
            return true
        }
        if (!isComposing) {
            val typed = event?.unicodeChar?.takeIf { it != 0 }?.toChar()
            val mark = typed?.let { mark(it) }
            if (mark != null) {
                commit(mark)
                return true
            }
            return false
        }
        when (keyCode) {
            KeyEvent.KEYCODE_SPACE -> select(highlighted)
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> commitLetters()
            KeyEvent.KEYCODE_DEL -> {
                letters = letters.dropLast(1)
                refresh()
            }
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> cancel()
            KeyEvent.KEYCODE_DPAD_LEFT -> step(false)
            KeyEvent.KEYCODE_DPAD_RIGHT -> step(true)
            KeyEvent.KEYCODE_DPAD_UP -> page(false, wrap = false)
            KeyEvent.KEYCODE_DPAD_DOWN -> page(true, wrap = false)
            else -> {
                val typed = event?.unicodeChar?.takeIf { it != 0 }?.toChar()
                commitHighlighted()
                val mark = typed?.let { mark(it) }
                if (mark != null) {
                    commit(mark)
                    return true
                }
                return false
            }
        }
        return true
    }

    private fun handleAlt(keyCode: Int, altText: String?): Boolean {
        val c = altText?.singleOrNull()
        if (!isComposing) {
            val mark = c?.let { mark(it) } ?: return false
            commit(mark)
            return true
        }
        when {
            keyCode == KeyEvent.KEYCODE_P -> page(true, wrap = true)
            c != null && c in '1'..'9' -> {
                val index = pageStart + (c - '1')
                if (index < candidates.size) select(index)
            }
            c != null && PUNCTUATION_MAP.containsKey(c) -> {
                commitHighlighted()
                commit(mark(c) ?: c.toString())
            }
        }
        return true
    }

    // --- Bar (taps, swipes and the ‹ › buttons) ---

    override fun onBarTapped(index: Int) {
        if (index in candidates.indices) select(index)
    }

    override fun step(forward: Boolean): Boolean {
        if (candidates.isEmpty()) return false
        val next = (highlighted + if (forward) 1 else -1).coerceIn(0, candidates.size - 1)
        if (next == highlighted) return false
        highlighted = next
        pageStart = (highlighted / PAGE_SIZE) * PAGE_SIZE
        publishBar()
        return true
    }

    override fun page(forward: Boolean, wrap: Boolean) {
        if (candidates.isEmpty()) return
        var start = pageStart + if (forward) PAGE_SIZE else -PAGE_SIZE
        if (start >= candidates.size) start = if (wrap) 0 else pageStart
        if (start < 0) start = 0
        pageStart = start
        highlighted = start
        publishBar()
    }

    // --- Composition ---

    private fun cancel() {
        letters = ""
        showComposing()
        candidates = emptyList()
        publishBar()
    }

    private fun refresh() {
        showComposing()
        candidates = if (isComposing) buildCandidates() else emptyList()
        highlighted = 0
        pageStart = 0
        publishBar()
    }

    private fun buildCandidates(): List<Candidate> {
        val reading = letters
        val dictionary = CantoneseDictionary.get(appContext) { if (isComposing) refresh() }
            ?: return listOf(Candidate(reading, reading.length))
        val out = LinkedHashMap<String, Candidate>()
        fun add(text: String, consumes: Int) {
            if (text.isNotEmpty() && text !in out) out[text] = Candidate(text, consumes)
        }
        learned(reading)?.let { add(it, reading.length) }
        val exact = dictionary.lookup(reading)
        // The longest first part that is a word ("ngodeisikfaan" → 我哋): pick it and the rest keeps
        // composing.
        var partialLength = 0
        var partial: List<String> = emptyList()
        for (length in reading.length - 1 downTo 1) {
            val words = dictionary.lookup(reading.substring(0, length))
            if (words.isEmpty()) continue
            partialLength = length
            partial = words
            break
        }
        fun addPartial() {
            if (partialLength == 0) return
            learned(reading.substring(0, partialLength))?.let { add(it, partialLength) }
            partial.take(PARTIAL).forEach { add(it, partialLength) }
        }
        // Whole syllables typed ("sikfaanmei" = sik faan mei) with no word for all of them: word by
        // word beats guessing a longer word (食飯未吖). An unfinished last syllable ("neih") is
        // better served by the longer words it can become (你好).
        val wordByWord = exact.isEmpty() && partialLength > 0 &&
            dictionary.lookup(reading.substring(partialLength)).isNotEmpty()
        if (wordByWord) addPartial()
        // The common words first, then longer words this could become, then the rest of the exact
        // readings (rare characters), then initials-only abbreviations.
        exact.take(EXACT_FIRST).forEach { add(it, reading.length) }
        if (reading.length >= 2) dictionary.completions(reading, COMPLETIONS).forEach { add(it, reading.length) }
        if (reading.length in 2..4) dictionary.lookup("#$reading").take(ABBREVIATIONS).forEach { add(it, reading.length) }
        exact.drop(EXACT_FIRST).forEach { add(it, reading.length) }
        addPartial()
        if (out.isEmpty()) add(reading, reading.length)
        return out.values.toList()
    }

    private fun select(index: Int) {
        val candidate = candidates.getOrNull(index) ?: run {
            commitLetters()
            return
        }
        val reading = letters
        remember(reading.substring(0, candidate.consumes.coerceAtMost(reading.length)), candidate.text)
        commitComposition(candidate.text)
        letters = reading.substring(candidate.consumes.coerceAtMost(reading.length))
        if (letters.isNotEmpty()) {
            refresh()
            return
        }
        candidates = emptyList()
        publishBar()
    }

    override fun commitHighlighted() {
        var guard = 0
        while (isComposing && guard++ < 16) select(highlighted)
    }

    private fun commitLetters() {
        if (!isComposing) return
        val text = letters
        letters = ""
        commitComposition(text)
        candidates = emptyList()
        publishBar()
    }

    private fun commitComposition(text: String) {
        val ic = inputConnection() ?: return
        ic.beginBatchEdit()
        ic.setComposingText(text, 1)
        ic.finishComposingText()
        ic.endBatchEdit()
        lastEditUptime = SystemClock.uptimeMillis()
    }

    private fun commit(text: String) {
        inputConnection()?.commitText(text, 1)
        lastEditUptime = SystemClock.uptimeMillis()
    }

    private fun showComposing() {
        val ic = inputConnection() ?: return
        ic.setComposingText(letters, 1)
        if (letters.isEmpty()) ic.finishComposingText()
        lastEditUptime = SystemClock.uptimeMillis()
    }

    private fun publishBar() {
        onBarChanged(
            if (candidates.isEmpty()) null
            else PinyinBarState(candidates.map { it.text }, pageStart, highlighted)
        )
    }

    override fun onSelectionUpdate(newSelStart: Int, newSelEnd: Int, candidatesEnd: Int): Boolean {
        if (!isComposing) return false
        val own = SystemClock.uptimeMillis() - lastEditUptime < OWN_EDIT_WINDOW_MS
        val atEnd = candidatesEnd >= 0 && newSelStart == newSelEnd && newSelEnd == candidatesEnd
        if (own || atEnd) return true
        letters = ""
        inputConnection()?.finishComposingText()
        candidates = emptyList()
        publishBar()
        return true
    }

    override fun onFinishInput() {
        letters = ""
        candidates = emptyList()
        publishBar()
        saveLearned()
    }

    // --- Learning: the last pick for a reading comes first next time ---

    private val learnedMap: LinkedHashMap<String, String> by lazy {
        val map = object : LinkedHashMap<String, String>(256, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > MAX_LEARNED
        }
        try {
            val json = JSONObject(prefs().getString(KEY_LEARNED, "{}") ?: "{}")
            json.keys().forEach { map[it] = json.getString(it) }
        } catch (error: Exception) {
            Log.w(TAG, "learned words unreadable", error)
        }
        map
    }
    private var learnedDirty = false

    private fun learned(reading: String): String? = learnedMap[reading]

    private fun remember(reading: String, text: String) {
        if (reading.isEmpty() || text == reading) return
        learnedMap[reading] = text
        learnedDirty = true
    }

    private fun saveLearned() {
        if (!learnedDirty) return
        val json = JSONObject()
        learnedMap.forEach { (k, v) -> json.put(k, v) }
        prefs().edit().putString(KEY_LEARNED, json.toString()).apply()
        learnedDirty = false
    }

    private fun prefs() = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    companion object {
        const val PAGE_SIZE = 9
        private const val TAG = "QingshuCantonese"
        private const val OWN_EDIT_WINDOW_MS = 250L
        private const val PREFS = "qingshu_cantonese"
        private const val KEY_LEARNED = "learned"
        private const val MAX_LEARNED = 3000
        private const val EXACT_FIRST = 6
        private const val COMPLETIONS = 6
        private const val ABBREVIATIONS = 12
        private const val PARTIAL = 12

        /** Hong Kong style full-width punctuation. */
        private val PUNCTUATION_MAP = mapOf(
            ',' to "，", '.' to "。", '?' to "？", '!' to "！", ':' to "：", ';' to "；",
            '[' to "「", ']' to "」", '(' to "（", ')' to "）", '~' to "～"
        )
    }
}
