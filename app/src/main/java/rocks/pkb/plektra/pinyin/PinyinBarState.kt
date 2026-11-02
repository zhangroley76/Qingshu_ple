package rocks.pkb.plektra.pinyin

/** What the keyboard bar shows while composing: all candidates, the visible page and the highlight. */
data class PinyinBarState(
    val candidates: List<String>,
    /** Absolute index of the first candidate on the visible page. */
    val pageStart: Int,
    /** Absolute index of the highlighted candidate. */
    val highlighted: Int,
)
