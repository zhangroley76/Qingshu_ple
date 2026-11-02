package app.qingjian.inputmethod.core

/**
 * JNI entry points of Qingjian's Rust engine (`libqingjian_android.so`, GPL-3.0-or-later,
 * https://github.com/qingjian-team/qingjian).
 *
 * The exported symbols are named after this class (`Java_app_qingjian_inputmethod_core_Native_*`),
 * so this object must keep exactly this package and class name. Only the calls Qingshu uses are
 * declared; the library exports more.
 *
 * Every function that returns a String returns one frame as JSON (see [rocks.pkb.plektra.pinyin.PinyinFrame]),
 * or null if the engine failed. Handles must only be used from the main thread.
 */
internal object Native {
    init {
        System.loadLibrary("qingjian_android")
    }

    /** Returns 0 on failure; the reason is logged under the `qingjian` tag. */
    external fun create(dataDir: String, userDir: String): Long

    external fun destroy(handle: Long)

    external fun push(handle: Long, codePoint: Int): String?

    external fun backspace(handle: Long): String?

    external fun select(handle: Long, index: Int): String?

    external fun commitRaw(handle: Long): String?

    external fun clear(handle: Long): String?

    /** Space while composing: commits the first candidate, or the typed letters if there is none. */
    external fun space(handle: Long): String?

    /** Converts an ASCII punctuation mark to its full-width Chinese form (pairs quotes); returns the text to commit. */
    external fun punctuate(handle: Long, c: Char): String?

    external fun reset(handle: Long)

    // --- Offline association (Qingshu patch, third_party/qingjian/0001-qingshu-association.patch) ---

    /** Opens the language model (lm.qj) for association; returns 0 on failure. */
    external fun createAssociation(modelPath: String): Long

    external fun destroyAssociation(handle: Long)

    /** Likely next words after [before] (text before the cursor), as a JSON array of strings. */
    external fun associate(handle: Long, before: String, limit: Int): String?
}
