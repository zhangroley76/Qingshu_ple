package rocks.pkb.plektra.pinyin

/** Process-wide flag read by UI pieces that don't have the service at hand (the language button). */
object QingshuMode {
    /** Pinyin is the current input language. */
    @Volatile
    var chineseActive: Boolean = false

    const val LANGUAGE_LABEL = "中"
    const val PREF_CHINESE_MODE = "qingshu_chinese_mode"
}
