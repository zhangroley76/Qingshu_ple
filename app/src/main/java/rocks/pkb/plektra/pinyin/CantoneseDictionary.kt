package rocks.pkb.plektra.pinyin

import android.content.Context
import android.util.Log
import java.util.concurrent.Executors

/**
 * Toneless Jyutping → Cantonese words, from the rime-cantonese lexicon (CC BY 4.0,
 * https://github.com/rime/rime-cantonese).
 *
 * The asset is prepared at build time (scripts/fetch-cantonese-dictionary.sh): one line per
 * reading, `reading<TAB>weight<TAB>word1/word2/...`, sorted by reading, words best first.
 * Readings are Jyutping without tones or spaces ("neihou" → 你好); "#" lines are initials-only
 * abbreviations ("#nh" → 你好). The file is kept as one string, searched by binary search.
 */
class CantoneseDictionary private constructor(private val text: String, private val lineStarts: IntArray) {

    /** Words for exactly [key], best first; empty when there are none. */
    fun lookup(key: String): List<String> {
        val line = lowerBound(key)
        if (line >= lineStarts.size || compareKey(line, key) != 0) return emptyList()
        return words(line)
    }

    /** Whether some reading starts with [prefix] (so more letters can still make a word). */
    fun hasPrefix(prefix: String): Boolean {
        val line = lowerBound(prefix)
        return line < lineStarts.size && keyStartsWith(line, prefix)
    }

    /**
     * The best words of longer readings that start with [prefix] ("neih" → 你好): the first word of
     * each, highest weight first. Looks at no more than [scan] readings.
     */
    fun completions(prefix: String, limit: Int, scan: Int = 400): List<String> {
        val found = ArrayList<Pair<Long, String>>()
        var line = lowerBound(prefix)
        var seen = 0
        while (line < lineStarts.size && seen < scan && keyStartsWith(line, prefix)) {
            if (keyLength(line) != prefix.length) {
                val first = words(line).firstOrNull()
                if (first != null) found += weight(line) to first
            }
            line++
            seen++
        }
        return found.sortedByDescending { it.first }.map { it.second }.distinct().take(limit)
    }

    private fun lowerBound(key: String): Int {
        var low = 0
        var high = lineStarts.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (compareKey(mid, key) < 0) low = mid + 1 else high = mid
        }
        return low
    }

    private fun keyEnd(line: Int): Int = text.indexOf('\t', lineStarts[line])

    private fun keyLength(line: Int): Int = keyEnd(line) - lineStarts[line]

    private fun compareKey(line: Int, key: String): Int {
        val start = lineStarts[line]
        val length = keyEnd(line) - start
        val n = minOf(length, key.length)
        for (i in 0 until n) {
            val diff = text[start + i] - key[i]
            if (diff != 0) return diff
        }
        return length - key.length
    }

    private fun keyStartsWith(line: Int, prefix: String): Boolean =
        keyLength(line) >= prefix.length && text.regionMatches(lineStarts[line], prefix, 0, prefix.length)

    private fun weight(line: Int): Long {
        val tab = keyEnd(line)
        val next = text.indexOf('\t', tab + 1)
        return text.substring(tab + 1, next).toLongOrNull() ?: 0L
    }

    private fun words(line: Int): List<String> {
        val tab = text.indexOf('\t', keyEnd(line) + 1)
        val end = text.indexOf('\n', tab).let { if (it < 0) text.length else it }
        return text.substring(tab + 1, end).split('/').filter { it.isNotEmpty() }
    }

    companion object {
        private const val TAG = "QingshuCantonese"
        const val ASSET = "cantonese/jyutping.txt"

        @Volatile
        private var loaded: CantoneseDictionary? = null
        @Volatile
        private var loading = false
        private val loader = Executors.newSingleThreadExecutor { r -> Thread(r, "qingshu-yue-dict").apply { isDaemon = true } }

        /** The dictionary if loaded; otherwise starts loading it in the background and returns null. */
        fun get(context: Context, onLoaded: () -> Unit = {}): CantoneseDictionary? {
            loaded?.let { return it }
            if (loading) return null
            loading = true
            val app = context.applicationContext
            loader.execute {
                try {
                    val started = System.currentTimeMillis()
                    val text = app.assets.open(ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() }
                    loaded = fromText(text)
                    Log.i(TAG, "Dictionary ready in ${System.currentTimeMillis() - started} ms (${loaded?.lineStarts?.size} entries)")
                    android.os.Handler(android.os.Looper.getMainLooper()).post(onLoaded)
                } catch (error: Exception) {
                    Log.e(TAG, "Dictionary failed to load", error)
                } finally {
                    loading = false
                }
            }
            return null
        }

        /** For tests and loading: [text] must already be sorted by reading. */
        fun fromText(text: String): CantoneseDictionary {
            val starts = ArrayList<Int>(300_000)
            var i = 0
            while (i < text.length) {
                val end = text.indexOf('\n', i).let { if (it < 0) text.length else it }
                val tab = text.indexOf('\t', i)
                if (end > i && tab in i until end && text.indexOf('\t', tab + 1) in tab + 1 until end) starts += i
                i = end + 1
            }
            return CantoneseDictionary(text, starts.toIntArray())
        }
    }
}
