package rocks.pkb.plektra.pinyin

import android.content.Context
import android.util.Log
import java.util.concurrent.Executors

/**
 * Kana → kanji dictionary (SKK-JISYO.L, GPL-2.0-or-later, https://github.com/skk-dev/dict).
 *
 * The asset is prepared at build time (scripts/fetch-japanese-dictionary.sh): one entry per line,
 * `reading<TAB>cand1/cand2/...`, sorted by reading, annotations removed. Okuri-ari readings end
 * with the romaji letter of the okurigana's row, as in SKK ("かk" → 書, for 書く 書き 書いて).
 * The whole file is kept as one string and searched by binary search over line starts.
 */
class JapaneseDictionary private constructor(private val text: String, private val lineStarts: IntArray) {

    /** Candidates for exactly [key], best first; empty when there are none. */
    fun lookup(key: String): List<String> {
        var low = 0
        var high = lineStarts.size - 1
        while (low <= high) {
            val mid = (low + high) ushr 1
            val start = lineStarts[mid]
            val tab = text.indexOf('\t', start)
            val cmp = compareKey(start, tab, key)
            when {
                cmp < 0 -> low = mid + 1
                cmp > 0 -> high = mid - 1
                else -> {
                    val end = text.indexOf('\n', tab).let { if (it < 0) text.length else it }
                    return text.substring(tab + 1, end).split('/').filter { it.isNotEmpty() }
                }
            }
        }
        return emptyList()
    }

    private fun compareKey(start: Int, end: Int, key: String): Int {
        val length = end - start
        val n = minOf(length, key.length)
        for (i in 0 until n) {
            val diff = text[start + i] - key[i]
            if (diff != 0) return diff
        }
        return length - key.length
    }

    companion object {
        private const val TAG = "QingshuJapanese"
        const val ASSET = "japanese/skk-jisyo.txt"

        @Volatile
        private var loaded: JapaneseDictionary? = null
        @Volatile
        private var loading = false
        private val loader = Executors.newSingleThreadExecutor { r -> Thread(r, "qingshu-ja-dict").apply { isDaemon = true } }

        /** The dictionary if loaded; otherwise starts loading it in the background and returns null. */
        fun get(context: Context, onLoaded: () -> Unit = {}): JapaneseDictionary? {
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
        fun fromText(text: String): JapaneseDictionary {
            val starts = ArrayList<Int>(200_000)
            var i = 0
            while (i < text.length) {
                val end = text.indexOf('\n', i).let { if (it < 0) text.length else it }
                if (end > i && text.indexOf('\t', i).let { it in i until end }) starts += i
                i = end + 1
            }
            return JapaneseDictionary(text, starts.toIntArray())
        }
    }
}
