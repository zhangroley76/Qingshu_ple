package rocks.pkb.plektra.pinyin

import android.content.Context
import rocks.pkb.plektra.SettingsManager

/** Qingshu's input languages on top of the Android keyboard language: English, 中文, 粵語, 한국어, 日本語. */
object QingshuMode {
    const val MODE_ENGLISH = "en"
    const val MODE_CHINESE = "zh"
    const val MODE_KOREAN = "ko"
    const val MODE_JAPANESE = "ja"
    const val MODE_CANTONESE = "yue"

    /** The optional languages, in Shift+Space order. */
    val EXTRA_LANGUAGES = listOf(MODE_CANTONESE, MODE_KOREAN, MODE_JAPANESE)

    /** The current mode, for UI pieces that don't have the service at hand (the language button). */
    @Volatile
    var mode: String = MODE_ENGLISH

    /** Pinyin is the current input language. */
    val chineseActive: Boolean get() = mode == MODE_CHINESE

    const val LANGUAGE_LABEL = "中"

    /** Kept for installs from before the Korean mode: true meant Chinese. */
    const val PREF_CHINESE_MODE = "qingshu_chinese_mode"
    private const val PREF_MODE = "qingshu_input_mode"
    private const val PREF_EXTRA_LANGUAGES = "qingshu_extra_languages"

    fun label(mode: String): String? = when (mode) {
        MODE_CHINESE -> "中"
        MODE_KOREAN -> "한"
        MODE_JAPANESE -> "あ"
        MODE_CANTONESE -> "粵"
        else -> null
    }

    fun toastLabel(mode: String): String = when (mode) {
        MODE_CHINESE -> "中文"
        MODE_KOREAN -> "한국어"
        MODE_JAPANESE -> "日本語"
        MODE_CANTONESE -> "粵語"
        else -> "English"
    }

    fun storedMode(context: Context): String {
        val prefs = SettingsManager.getPreferences(context)
        return prefs.getString(PREF_MODE, null)
            ?: if (prefs.getBoolean(PREF_CHINESE_MODE, false)) MODE_CHINESE else MODE_ENGLISH
    }

    fun storeMode(context: Context, mode: String) {
        SettingsManager.getPreferences(context).edit()
            .putString(PREF_MODE, mode)
            .putBoolean(PREF_CHINESE_MODE, mode == MODE_CHINESE)
            .apply()
    }

    /** Extra languages switched on in settings, besides English and Chinese. */
    fun extraLanguages(context: Context): Set<String> =
        SettingsManager.getPreferences(context).getStringSet(PREF_EXTRA_LANGUAGES, emptySet()).orEmpty()

    fun setExtraLanguages(context: Context, languages: Set<String>) {
        SettingsManager.getPreferences(context).edit().putStringSet(PREF_EXTRA_LANGUAGES, languages.toSet()).apply()
    }

    /** Shift+Space order: English → 中文 → 粵語 / 한국어 / 日本語 (those switched on) → English. */
    fun cycle(context: Context, from: String): String {
        val order = listOf(MODE_ENGLISH, MODE_CHINESE) +
            EXTRA_LANGUAGES.filter { it in extraLanguages(context) }
        val index = order.indexOf(from)
        return order[(index + 1).mod(order.size)]
    }
}
