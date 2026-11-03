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
     * Qingjian's `InputOptions`. The values other than the traditional variant are the engine's own
     * defaults, so sending them changes nothing else.
     */
    fun optionsJson(context: Context): String {
        val variant = traditional(context).takeIf { it != TRADITIONAL_OFF } ?: TRADITIONAL_TAIWAN
        return JSONObject()
            .put("fuzzy", JSONArray())
            .put("full_width_punctuation", true)
            .put("english_in_chinese", true)
            .put("chinese_first", false)
            .put("traditional_variant", variant)
            .toString()
    }
}
