package rocks.pkb.plektra.pinyin

/**
 * Romaji → hiragana as typed on a Japanese keyboard IME: Hepburn and Kunrei spellings, doubled
 * consonants for っ (kitte → きって), `nn` / `n'` / n before a consonant for ん, `x` or `l` for
 * small kana (xa → ぁ, ltu → っ), and `-` for ー.
 */
object RomajiKana {
    private val TABLE: Map<String, String> = buildMap {
        val vowels = "aiueo"
        fun row(prefix: String, kana: String) {
            // kana: five characters for a i u e o (a space where the combination doesn't exist)
            vowels.forEachIndexed { i, v -> if (kana[i] != ' ') put(prefix + v, kana[i].toString()) }
        }
        fun yoon(prefix: String, base: String) {
            put(prefix + "a", base + "ゃ"); put(prefix + "u", base + "ゅ"); put(prefix + "o", base + "ょ")
            put(prefix + "e", base + "ぇ"); put(prefix + "i", base + "ぃ")
        }
        row("", "あいうえお")
        row("k", "かきくけこ"); row("g", "がぎぐげご")
        row("s", "さしすせそ"); row("z", "ざじずぜぞ")
        row("t", "たちつてと"); row("d", "だぢづでど")
        row("n", "なにぬねの")
        row("h", "はひふへほ"); row("b", "ばびぶべぼ"); row("p", "ぱぴぷぺぽ")
        row("m", "まみむめも")
        row("y", "や ゆ よ")
        row("r", "らりるれろ"); row("l", "ぁぃぅぇぉ")
        row("w", "わ   を")
        row("x", "ぁぃぅぇぉ")
        row("f", "ふ ふ ふ")
        put("fa", "ふぁ"); put("fi", "ふぃ"); put("fe", "ふぇ"); put("fo", "ふぉ"); put("fyu", "ふゅ")
        put("va", "ゔぁ"); put("vi", "ゔぃ"); put("vu", "ゔ"); put("ve", "ゔぇ"); put("vo", "ゔぉ")
        put("shi", "し"); put("chi", "ち"); put("tsu", "つ"); put("ji", "じ"); put("fu", "ふ")
        put("wi", "うぃ"); put("we", "うぇ"); put("ye", "いぇ")
        put("ti", "ち"); put("tu", "つ"); put("si", "し"); put("zi", "じ"); put("hu", "ふ"); put("di", "ぢ"); put("du", "づ")
        put("thi", "てぃ"); put("dhi", "でぃ"); put("twu", "とぅ"); put("dwu", "どぅ")
        put("tsa", "つぁ"); put("tsi", "つぃ"); put("tse", "つぇ"); put("tso", "つぉ")
        yoon("ky", "き"); yoon("gy", "ぎ"); yoon("sy", "し"); yoon("zy", "じ")
        yoon("ty", "ち"); yoon("dy", "ぢ"); yoon("ny", "に"); yoon("hy", "ひ")
        yoon("by", "び"); yoon("py", "ぴ"); yoon("my", "み"); yoon("ry", "り")
        put("sha", "しゃ"); put("shu", "しゅ"); put("she", "しぇ"); put("sho", "しょ")
        put("cha", "ちゃ"); put("chu", "ちゅ"); put("che", "ちぇ"); put("cho", "ちょ")
        put("ja", "じゃ"); put("ju", "じゅ"); put("je", "じぇ"); put("jo", "じょ")
        put("jya", "じゃ"); put("jyu", "じゅ"); put("jyo", "じょ")
        put("xya", "ゃ"); put("xyu", "ゅ"); put("xyo", "ょ"); put("lya", "ゃ"); put("lyu", "ゅ"); put("lyo", "ょ")
        put("xtu", "っ"); put("ltu", "っ"); put("xtsu", "っ"); put("ltsu", "っ"); put("xwa", "ゎ"); put("lwa", "ゎ")
        put("nn", "ん"); put("n'", "ん"); put("xn", "ん")
        put("-", "ー")
    }

    /** Longest romaji spelling in [TABLE]. */
    private const val MAX_KEY = 4

    /** Hiragana for [romaji] plus the letters that don't make a kana yet ("niho" → "に" + "ho"). */
    fun convert(romaji: String): Pair<String, String> {
        val out = StringBuilder()
        var i = 0
        val s = romaji.lowercase()
        while (i < s.length) {
            val c = s[i]
            // Doubled consonant: っ (but "nn" is ん).
            if (i + 1 < s.length && c == s[i + 1] && c in DOUBLING) {
                out.append('っ')
                i += 1
                continue
            }
            // n before a consonant (or before a y-less vowel-less letter) is ん.
            if (c == 'n' && i + 1 < s.length && s[i + 1] !in "aiueoyn'") {
                out.append('ん')
                i += 1
                continue
            }
            var matched = false
            for (len in minOf(MAX_KEY, s.length - i) downTo 1) {
                val kana = TABLE[s.substring(i, i + len)] ?: continue
                out.append(kana)
                i += len
                matched = true
                break
            }
            if (!matched) {
                // Could still become a kana with more letters: keep it pending.
                val rest = s.substring(i)
                if (TABLE.keys.any { it.startsWith(rest) }) return out.toString() to rest
                // A letter that never makes a kana: keep it as typed.
                out.append(s[i])
                i += 1
            }
        }
        return out.toString() to ""
    }

    /** Pending "n" at the end means ん when the reading is converted. */
    fun reading(romaji: String): String {
        val (kana, pending) = convert(romaji)
        return if (pending == "n") kana + "ん" else kana + pending
    }

    fun toKatakana(hiragana: String): String = buildString {
        for (c in hiragana) append(if (c in 'ぁ'..'ゖ') c + 0x60 else c)
    }

    private const val DOUBLING = "kgsztdhbpmyrwfcjvl"
}
