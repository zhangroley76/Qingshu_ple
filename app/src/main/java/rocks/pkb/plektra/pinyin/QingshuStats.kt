package rocks.pkb.plektra.pinyin

import android.content.Context

/** Counts the Chinese characters committed with pinyin, for the typing stats screen. Never their text. */
object QingshuStats {
    private const val PREFS = "qingshu_stats"
    private const val KEY_CHINESE_CHARS = "chinese_chars"
    private const val KEY_CHINESE_COMMITS = "chinese_commits"

    private var pendingChars = 0L
    private var pendingCommits = 0L

    fun onChineseCommitted(context: Context, text: String) {
        val han = text.count { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN }
        if (han == 0) return
        pendingChars += han
        pendingCommits += 1
        if (pendingCommits >= 20) save(context)
    }

    fun save(context: Context) {
        if (pendingChars == 0L && pendingCommits == 0L) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putLong(KEY_CHINESE_CHARS, prefs.getLong(KEY_CHINESE_CHARS, 0) + pendingChars)
            .putLong(KEY_CHINESE_COMMITS, prefs.getLong(KEY_CHINESE_COMMITS, 0) + pendingCommits)
            .apply()
        pendingChars = 0
        pendingCommits = 0
    }

    fun chineseChars(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_CHINESE_CHARS, 0) + pendingChars

    fun reset(context: Context) {
        pendingChars = 0
        pendingCommits = 0
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
