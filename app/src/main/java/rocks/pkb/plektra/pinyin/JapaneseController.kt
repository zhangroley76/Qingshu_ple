package rocks.pkb.plektra.pinyin

import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import org.json.JSONObject

/**
 * Japanese input: romaji → hiragana composing text, converted to kanji from the SKK dictionary
 * word by word. Keys work as in pinyin: Space takes the highlighted candidate, Alt+W…V picks 1–9,
 * Alt+P turns the page, Enter commits the kana, Backspace removes one kana (or pending letter),
 * Back cancels. Picking a candidate for the first part of the reading keeps the rest composing.
 */
class JapaneseController(
    context: Context,
    private val inputConnection: () -> InputConnection?,
    private val onBarChanged: (PinyinBarState?) -> Unit,
) {
    private val appContext = context.applicationContext

    /** Kana already converted from romaji, and the letters that don't make a kana yet. */
    private var kana = ""
    private var pending = ""

    private data class Candidate(val text: String, val consumes: Int)

    private var candidates: List<Candidate> = emptyList()
    private var highlighted = 0
    private var pageStart = 0
    private var lastEditUptime = 0L

    var active: Boolean = false
        private set

    val isComposing: Boolean get() = kana.isNotEmpty() || pending.isNotEmpty()

    data class Mods(val alt: Boolean, val ctrlOrMeta: Boolean, val altText: String?, val overlayActive: Boolean)

    fun updateActive(on: Boolean) {
        if (!on && active) commitKana()
        active = on
        if (on) JapaneseDictionary.get(appContext) { if (isComposing) refresh() }
    }

    fun onKeyDown(keyCode: Int, event: KeyEvent?, mods: Mods): Boolean {
        if (!active) return false
        if (mods.ctrlOrMeta || mods.overlayActive) {
            if (isComposing) commitHighlighted()
            return false
        }
        val letter = if (keyCode in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z) 'a' + (keyCode - KeyEvent.KEYCODE_A) else null
        if (mods.alt) return handleAlt(keyCode, mods.altText)
        if (letter != null) {
            feed(letter)
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_MINUS && event?.isShiftPressed != true) {
            feed('-')
            return true
        }
        if (!isComposing) {
            val typed = event?.unicodeChar?.takeIf { it != 0 }?.toChar()
            val mark = typed?.let { PUNCTUATION[it] }
            if (mark != null) {
                commit(mark)
                return true
            }
            return false
        }
        when (keyCode) {
            KeyEvent.KEYCODE_SPACE -> select(highlighted)
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> commitKana()
            KeyEvent.KEYCODE_DEL -> backspace()
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> cancel()
            KeyEvent.KEYCODE_DPAD_LEFT -> step(false)
            KeyEvent.KEYCODE_DPAD_RIGHT -> step(true)
            KeyEvent.KEYCODE_DPAD_UP -> page(false)
            KeyEvent.KEYCODE_DPAD_DOWN -> page(true)
            else -> {
                val typed = event?.unicodeChar?.takeIf { it != 0 }?.toChar()
                commitHighlighted()
                val mark = typed?.let { PUNCTUATION[it] }
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
            val mark = c?.let { PUNCTUATION[it] } ?: return false
            commit(mark)
            return true
        }
        when {
            keyCode == KeyEvent.KEYCODE_P -> page(true, wrap = true)
            c != null && c in '1'..'9' -> {
                val index = pageStart + (c - '1')
                if (index < candidates.size) select(index)
            }
            c != null && PUNCTUATION.containsKey(c) -> {
                commitHighlighted()
                commit(PUNCTUATION.getValue(c))
            }
        }
        return true
    }

    // --- Bar (taps, swipes and the ‹ › buttons) ---

    fun onBarTapped(index: Int) {
        if (index in candidates.indices) select(index)
    }

    /** Moves the highlight; returns whether it moved. */
    fun step(forward: Boolean): Boolean {
        if (candidates.isEmpty()) return false
        val next = (highlighted + if (forward) 1 else -1).coerceIn(0, candidates.size - 1)
        if (next == highlighted) return false
        highlighted = next
        pageStart = (highlighted / PAGE_SIZE) * PAGE_SIZE
        publishBar()
        return true
    }

    fun page(forward: Boolean, wrap: Boolean = false) {
        if (candidates.isEmpty()) return
        var start = pageStart + if (forward) PAGE_SIZE else -PAGE_SIZE
        if (start >= candidates.size) start = if (wrap) 0 else pageStart
        if (start < 0) start = 0
        pageStart = start
        highlighted = start
        publishBar()
    }

    // --- Composition ---

    private fun feed(c: Char) {
        val (converted, rest) = RomajiKana.convert(pending + c)
        kana += converted
        pending = rest
        refresh()
    }

    private fun backspace() {
        if (pending.isNotEmpty()) pending = pending.dropLast(1) else kana = kana.dropLast(1)
        refresh()
    }

    private fun cancel() {
        kana = ""
        pending = ""
        showComposing()
        candidates = emptyList()
        publishBar()
    }

    /** The reading looked up: the kana, with a trailing "n" read as ん. */
    private fun reading(): String = kana + if (pending == "n") "ん" else ""

    private fun refresh() {
        showComposing()
        candidates = if (isComposing) buildCandidates() else emptyList()
        highlighted = 0
        pageStart = 0
        publishBar()
    }

    private fun buildCandidates(): List<Candidate> {
        val reading = reading()
        if (reading.isEmpty() || (pending.isNotEmpty() && pending != "n")) {
            // Still spelling a kana ("ky"): show what's there.
            return listOf(Candidate(kana + pending, kana.length)).filter { it.text.isNotEmpty() && kana.isNotEmpty() }
        }
        val out = LinkedHashMap<String, Candidate>()
        fun add(text: String, consumes: Int) {
            if (text.isNotEmpty() && text !in out) out[text] = Candidate(text, consumes)
        }
        learned(reading)?.let { add(it, reading.length) }
        val dictionary = JapaneseDictionary.get(appContext) { if (isComposing) refresh() }
        dictionary?.lookup(reading)?.forEach { add(it, reading.length) }
        // Verbs and adjectives with okurigana: 読む → "よm" + む. Only the longest stem that has
        // words; when the reading is a word by itself, only for common inflected endings, so
        // にほん doesn't also offer 似ほん.
        if (dictionary != null) {
            val hasWord = out.isNotEmpty()
            for (split in reading.length - 1 downTo 1) {
                val okuri = reading.substring(split)
                if (hasWord && okuri !in COMMON_OKURI) continue
                val letter = okuriLetter(okuri[0]) ?: continue
                val stems = dictionary.lookup(reading.substring(0, split) + letter)
                if (stems.isEmpty()) continue
                stems.forEach { add(it + okuri, reading.length) }
                break
            }
        }
        add(reading, reading.length)
        add(RomajiKana.toKatakana(reading), reading.length)
        // No word for the whole reading: offer the longest first part that has one.
        if (dictionary != null && out.size <= 2) {
            for (length in reading.length - 1 downTo 1) {
                val words = dictionary.lookup(reading.substring(0, length))
                if (words.isEmpty()) continue
                words.forEach { add(it, length) }
                break
            }
        }
        return out.values.toList()
    }

    private fun select(index: Int) {
        val candidate = candidates.getOrNull(index) ?: run {
            commitKana()
            return
        }
        val reading = reading()
        if (candidate.consumes >= reading.length) {
            remember(reading, candidate.text)
            kana = ""
            pending = ""
            commitComposition(candidate.text)
        } else {
            commitComposition(candidate.text)
            kana = reading.substring(candidate.consumes)
            pending = ""
            refresh()
            return
        }
        candidates = emptyList()
        publishBar()
    }

    /** Commits the highlighted candidate (used before punctuation and shortcuts). */
    fun commitHighlighted() {
        var guard = 0
        while (isComposing && guard++ < 8) select(highlighted)
    }

    private fun commitKana() {
        if (!isComposing) return
        val text = reading() + if (pending != "n") pending else ""
        kana = ""
        pending = ""
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
        val text = kana + pending
        ic.setComposingText(text, 1)
        if (text.isEmpty()) ic.finishComposingText()
        lastEditUptime = SystemClock.uptimeMillis()
    }

    private fun publishBar() {
        onBarChanged(
            if (candidates.isEmpty()) null
            else PinyinBarState(candidates.map { it.text }, pageStart, highlighted)
        )
    }

    /** Returns true when the update belongs to the composition. */
    fun onSelectionUpdate(newSelStart: Int, newSelEnd: Int, candidatesEnd: Int): Boolean {
        if (!isComposing) return false
        val own = SystemClock.uptimeMillis() - lastEditUptime < OWN_EDIT_WINDOW_MS
        val atEnd = candidatesEnd >= 0 && newSelStart == newSelEnd && newSelEnd == candidatesEnd
        if (own || atEnd) return true
        kana = ""
        pending = ""
        inputConnection()?.finishComposingText()
        candidates = emptyList()
        publishBar()
        return true
    }

    fun onFinishInput() {
        kana = ""
        pending = ""
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
        if (text == reading) return
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
        private const val TAG = "QingshuJapanese"
        private const val OWN_EDIT_WINDOW_MS = 250L
        private const val PREFS = "qingshu_japanese"
        private const val KEY_LEARNED = "learned"
        private const val MAX_LEARNED = 3000

        /** Okurigana endings common enough to offer even when the reading is also a word. */
        private val COMMON_OKURI = setOf(
            "う", "く", "ぐ", "す", "つ", "ぬ", "ぶ", "む", "る", "い", "て", "た", "で", "だ",
            "き", "し", "ち", "に", "み", "り", "ます", "ました", "ない", "なかった", "まして", "って", "った",
            "いて", "いた", "んで", "んだ", "かった", "くて", "ければ", "れる", "られる", "せる", "よう"
        )

        private val PUNCTUATION = mapOf(
            ',' to "、", '.' to "。", '?' to "？", '!' to "！", ':' to "：", ';' to "；",
            '[' to "「", ']' to "」", '(' to "（", ')' to "）", '~' to "〜", '/' to "・"
        )

        /** The romaji letter SKK uses for okurigana starting with [kana] (書く → "かk"). */
        fun okuriLetter(kana: Char): Char? = when (kana) {
            in "あいうえお" -> "aiueo"["あいうえお".indexOf(kana)]
            in "かきくけこ" -> 'k'; in "がぎぐげご" -> 'g'
            in "さしすせそ" -> 's'; in "ざじずぜぞ" -> 'z'
            in "たちつてと" -> 't'; in "だぢづでど" -> 'd'; 'っ' -> 't'
            in "なにぬねの" -> 'n'; 'ん' -> 'n'
            in "はひふへほ" -> 'h'; in "ばびぶべぼ" -> 'b'; in "ぱぴぷぺぽ" -> 'p'
            in "まみむめも" -> 'm'; in "やゆよ" -> 'y'
            in "らりるれろ" -> 'r'; in "わを" -> 'w'
            else -> null
        }
    }
}
