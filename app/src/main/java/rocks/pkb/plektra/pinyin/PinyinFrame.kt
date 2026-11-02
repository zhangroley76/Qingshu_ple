package rocks.pkb.plektra.pinyin

import org.json.JSONObject

/**
 * One engine frame: what to commit, what is still being composed, and the candidates.
 * Mirrors the subset of Qingjian's `Frame` JSON that Qingshu uses.
 */
data class PinyinFrame(
    /** Text to commit now; empty when nothing is committed. */
    val commit: String,
    /** UTF-16 units to delete before the cursor before committing (normally 0). */
    val replace: Int,
    /** Composition shown inline, e.g. `ni'hao` or `我想去chi'fan`; empty when not composing. */
    val preedit: String,
    val candidates: List<String>,
) {
    val isComposing: Boolean get() = preedit.isNotEmpty()

    companion object {
        val EMPTY = PinyinFrame("", 0, "", emptyList())

        fun parse(json: String?): PinyinFrame? {
            if (json.isNullOrEmpty()) return null
            return try {
                val obj = JSONObject(json)
                val array = obj.optJSONArray("candidates")
                val candidates = buildList {
                    if (array != null) {
                        // Keep every entry, even an empty one: the list index is the engine's select() index.
                        for (i in 0 until array.length()) {
                            add(array.optJSONObject(i)?.optString("text").orEmpty())
                        }
                    }
                }
                PinyinFrame(
                    commit = obj.optString("commit"),
                    replace = obj.optInt("replace"),
                    preedit = obj.optString("preedit"),
                    candidates = candidates,
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}
