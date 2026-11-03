package rocks.pkb.plektra.pinyin

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import rocks.pkb.plektra.SettingsManager

/** Qingshu's Chinese options, stored with Plektra's preferences and sent to the engine. */
object QingshuChineseSettings {
    const val TRADITIONAL_OFF = "off"
    const val TRADITIONAL_TAIWAN = "taiwan"
    const val TRADITIONAL_HONGKONG = "hongkong"
    const val TRADITIONAL_STANDARD = "standard"

    /** Broadcast so the keyboard re-sends the options to the engine. */
    const val ACTION_CHANGED = "io.github.zhangroley76.qingshu.CHINESE_SETTINGS_CHANGED"

    private const val KEY_TRADITIONAL = "qingshu_traditional"
    private const val KEY_FUZZY = "qingshu_fuzzy"
    private const val KEY_DOMAINS = "qingshu_domains"
    private const val KEY_GENERAL_DICTIONARY = "qingshu_general_dictionary"
    private const val KEY_CORNER_QUOTES = "qingshu_corner_quotes"
    private const val KEY_ENGLISH_IN_CHINESE = "qingshu_english_in_chinese"
    private const val KEY_CHINESE_FIRST = "qingshu_chinese_first"

    /** Qingjian's bundled domain dictionaries (`dicts/<id>.qj`) with their labels. */
    val DOMAINS = listOf(
        "idioms" to "成语", "poetry_lines" to "诗词", "places" to "地名",
        "historical_figures" to "历史人物", "it_computing" to "IT / 计算机", "medicine" to "医学",
        "law" to "法律", "finance" to "财经", "food" to "饮食", "animals" to "动物", "automotive" to "汽车"
    )

    /** As in Qingjian: only idioms are on until the user picks more. */
    fun domains(context: Context): Set<String> =
        SettingsManager.getPreferences(context).getStringSet(KEY_DOMAINS, setOf("idioms")).orEmpty()

    fun setDomains(context: Context, domains: Set<String>) {
        SettingsManager.getPreferences(context).edit().putStringSet(KEY_DOMAINS, domains.toSet()).apply()
        notifyChanged(context)
    }

    /** Qingjian's large general vocabulary (`dicts/general.qj`), on by default as in Qingjian. */
    fun generalDictionary(context: Context): Boolean =
        SettingsManager.getPreferences(context).getBoolean(KEY_GENERAL_DICTIONARY, true)

    fun setGeneralDictionary(context: Context, on: Boolean) {
        SettingsManager.getPreferences(context).edit().putBoolean(KEY_GENERAL_DICTIONARY, on).apply()
        notifyChanged(context)
    }

    /** Quotation marks as 「」 and 『』 (Taiwan / Hong Kong style) instead of “” and ‘’. */
    fun cornerQuotes(context: Context): Boolean =
        SettingsManager.getPreferences(context).getBoolean(KEY_CORNER_QUOTES, false)

    fun setCornerQuotes(context: Context, on: Boolean) {
        SettingsManager.getPreferences(context).edit().putBoolean(KEY_CORNER_QUOTES, on).apply()
        notifyChanged(context)
    }

    /** Qingjian's fuzzy pinyin rules (`FuzzyRules::NAMES`) with their labels. */
    val FUZZY_RULES = listOf(
        "z_zh" to "z = zh", "c_ch" to "c = ch", "s_sh" to "s = sh",
        "n_l" to "n = l", "f_h" to "f = h", "l_r" to "l = r",
        "an_ang" to "an = ang", "en_eng" to "en = eng", "in_ing" to "in = ing"
    )

    fun fuzzy(context: Context): Set<String> =
        SettingsManager.getPreferences(context).getStringSet(KEY_FUZZY, emptySet()).orEmpty()

    fun setFuzzy(context: Context, rules: Set<String>) {
        SettingsManager.getPreferences(context).edit().putStringSet(KEY_FUZZY, rules.toSet()).apply()
        notifyChanged(context)
    }

    /** English words among the pinyin candidates ("hello" while in Chinese); on by default, as in Qingjian. */
    fun englishInChinese(context: Context): Boolean =
        SettingsManager.getPreferences(context).getBoolean(KEY_ENGLISH_IN_CHINESE, true)

    fun setEnglishInChinese(context: Context, on: Boolean) {
        SettingsManager.getPreferences(context).edit().putBoolean(KEY_ENGLISH_IN_CHINESE, on).apply()
        notifyChanged(context)
    }

    /** Chinese candidates always before English words. */
    fun chineseFirst(context: Context): Boolean =
        SettingsManager.getPreferences(context).getBoolean(KEY_CHINESE_FIRST, false)

    fun setChineseFirst(context: Context, on: Boolean) {
        SettingsManager.getPreferences(context).edit().putBoolean(KEY_CHINESE_FIRST, on).apply()
        notifyChanged(context)
    }

    fun traditional(context: Context): String =
        SettingsManager.getPreferences(context).getString(KEY_TRADITIONAL, TRADITIONAL_OFF) ?: TRADITIONAL_OFF

    fun setTraditional(context: Context, variant: String) {
        SettingsManager.getPreferences(context).edit().putString(KEY_TRADITIONAL, variant).apply()
        notifyChanged(context)
    }

    fun notifyChanged(context: Context) {
        context.sendBroadcast(android.content.Intent(ACTION_CHANGED).setPackage(context.packageName))
    }

    /**
     * Qingjian's `InputOptions`. Options without a Qingshu setting keep the engine's defaults.
     */
    fun optionsJson(context: Context): String {
        val variant = traditional(context).takeIf { it != TRADITIONAL_OFF } ?: TRADITIONAL_TAIWAN
        return JSONObject()
            .put("fuzzy", JSONArray(FUZZY_RULES.map { it.first }.filter { it in fuzzy(context) }))
            .put("full_width_punctuation", true)
            .put("english_in_chinese", englishInChinese(context))
            .put("chinese_first", chineseFirst(context))
            .put("traditional_variant", variant)
            .put("domains", JSONArray(DOMAINS.map { it.first }.filter { it in domains(context) }))
            .put("disabled_dictionaries", JSONArray(if (generalDictionary(context)) emptyList() else listOf("general")))
            .toString()
    }
}
