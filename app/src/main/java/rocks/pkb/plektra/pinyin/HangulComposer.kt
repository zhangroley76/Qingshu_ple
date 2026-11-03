package rocks.pkb.plektra.pinyin

/**
 * Korean 2-set (두벌식) composition: QWERTY letters → jamo → syllables.
 *
 * Holds the syllable being typed as the keystrokes that make it (initial consonant, one or two
 * vowel strokes, up to two final strokes), so Backspace removes one stroke at a time. Feeding a
 * stroke returns the text that became final; [composing] is what to show as composing text.
 */
class HangulComposer {
    private var initial: Char? = null
    private var vowel = ""
    private var finals = ""

    val isEmpty: Boolean get() = initial == null && vowel.isEmpty() && finals.isEmpty()

    /** The syllable (or lone jamo) being typed. */
    val composing: String get() = render(initial, vowel, finals)

    fun clear() {
        initial = null
        vowel = ""
        finals = ""
    }

    /** Ends the syllable: returns it and starts over. */
    fun finish(): String = composing.also { clear() }

    /** Adds the jamo typed with [key] (a–z, [shift] for the doubled consonants). Returns committed text. */
    fun feed(key: Char, shift: Boolean): String {
        val jamo = jamoFor(key, shift) ?: return ""
        return if (jamo in VOWELS) feedVowel(jamo) else feedConsonant(jamo)
    }

    /** Removes the last stroke; false when there was nothing to remove. */
    fun backspace(): Boolean {
        when {
            finals.isNotEmpty() -> finals = finals.dropLast(1)
            vowel.isNotEmpty() -> vowel = vowel.dropLast(1)
            initial != null -> initial = null
            else -> return false
        }
        return true
    }

    private fun feedConsonant(c: Char): String {
        when {
            isEmpty -> {
                initial = c
                return ""
            }
            vowel.isEmpty() || initial == null -> return restartWith(initial = c)
            finals.isEmpty() && c in JONG -> {
                finals = c.toString()
                return ""
            }
            finals.length == 1 && (finals + c) in COMPOUND_FINALS -> {
                finals += c
                return ""
            }
            else -> return restartWith(initial = c)
        }
    }

    private fun feedVowel(v: Char): String {
        when {
            vowel.isEmpty() && finals.isEmpty() -> {
                vowel = v.toString()
                return ""
            }
            finals.isEmpty() -> {
                if (vowel.length == 1 && (vowel + v) in COMPOUND_VOWELS) {
                    vowel += v
                    return ""
                }
                return restartWith(vowel = v.toString())
            }
            else -> {
                // The last final consonant starts the next syllable: 각 + ㅏ → 가가.
                val moving = finals.last()
                finals = finals.dropLast(1)
                return restartWith(initial = moving, vowel = v.toString())
            }
        }
    }

    private fun restartWith(initial: Char? = null, vowel: String = ""): String {
        val done = composing
        this.initial = initial
        this.vowel = vowel
        finals = ""
        return done
    }

    companion object {
        private const val CHO = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"
        private const val JUNG = "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ"
        /** Final consonants; index 0 (a space) is "no final". */
        private const val JONG = " ㄱㄲㄳㄴㄵㄶㄷㄹㄺㄻㄼㄽㄾㄿㅀㅁㅂㅄㅅㅆㅇㅈㅊㅋㅌㅍㅎ"
        private const val VOWELS = "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅛㅜㅠㅡㅣ"

        private val COMPOUND_VOWELS = mapOf(
            "ㅗㅏ" to 'ㅘ', "ㅗㅐ" to 'ㅙ', "ㅗㅣ" to 'ㅚ',
            "ㅜㅓ" to 'ㅝ', "ㅜㅔ" to 'ㅞ', "ㅜㅣ" to 'ㅟ', "ㅡㅣ" to 'ㅢ'
        )
        private val COMPOUND_FINALS = mapOf(
            "ㄱㅅ" to 'ㄳ', "ㄴㅈ" to 'ㄵ', "ㄴㅎ" to 'ㄶ', "ㄹㄱ" to 'ㄺ', "ㄹㅁ" to 'ㄻ',
            "ㄹㅂ" to 'ㄼ', "ㄹㅅ" to 'ㄽ', "ㄹㅌ" to 'ㄾ', "ㄹㅍ" to 'ㄿ', "ㄹㅎ" to 'ㅀ', "ㅂㅅ" to 'ㅄ'
        )

        /** Standard 2-set layout on QWERTY. */
        private const val PLAIN = "ㅁㅠㅊㅇㄷㄹㅎㅗㅑㅓㅏㅣㅡㅜㅐㅔㅂㄱㄴㅅㅕㅍㅈㅌㅛㅋ" // a..z
        private val SHIFTED = mapOf('q' to 'ㅃ', 'w' to 'ㅉ', 'e' to 'ㄸ', 'r' to 'ㄲ', 't' to 'ㅆ', 'o' to 'ㅒ', 'p' to 'ㅖ')

        fun jamoFor(key: Char, shift: Boolean): Char? {
            val lower = key.lowercaseChar()
            if (lower !in 'a'..'z') return null
            if (shift) SHIFTED[lower]?.let { return it }
            return PLAIN[lower - 'a']
        }

        private fun render(initial: Char?, vowel: String, finals: String): String {
            val v = when (vowel.length) {
                0 -> null
                1 -> vowel[0]
                else -> COMPOUND_VOWELS[vowel]
            }
            val f = when (finals.length) {
                0 -> null
                1 -> finals[0]
                else -> COMPOUND_FINALS[finals]
            }
            if (initial != null && v != null) {
                val cho = CHO.indexOf(initial)
                val jung = JUNG.indexOf(v)
                val jong = f?.let { JONG.indexOf(it) } ?: 0
                if (cho >= 0 && jung >= 0 && jong >= 0) {
                    return (0xAC00 + (cho * 21 + jung) * 28 + jong).toChar().toString()
                }
            }
            return buildString {
                initial?.let(::append)
                v?.let(::append)
                f?.let(::append)
            }
        }
    }
}
