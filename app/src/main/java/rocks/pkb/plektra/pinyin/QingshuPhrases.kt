package rocks.pkb.plektra.pinyin

import android.content.Context
import android.util.Log
import app.qingjian.inputmethod.core.Native
import org.json.JSONObject

/**
 * The user's Chinese phrases (青简「常用语」). A phrase with a code is offered as a candidate when
 * that code is typed in pinyin, e.g. code `yx` for an email address.
 */
object QingshuPhrases {
    data class Phrase(val text: String, val code: String, val group: String)

    /** Broadcast so the keyboard re-reads the phrases. */
    const val ACTION_CHANGED = "io.github.zhangroley76.qingshu.PHRASES_CHANGED"

    fun list(context: Context): List<Phrase> = run(context, JSONObject().put("op", "list"))

    fun add(context: Context, text: String, code: String): List<Phrase> =
        run(context, JSONObject().put("op", "add").put("text", text).put("group", "").put("code", normalizeCode(code)))

    fun update(context: Context, index: Int, text: String, code: String, group: String): List<Phrase> =
        run(
            context,
            JSONObject().put("op", "update").put("index", index).put("text", text)
                .put("group", group).put("code", normalizeCode(code))
        )

    fun remove(context: Context, index: Int): List<Phrase> =
        run(context, JSONObject().put("op", "remove").put("index", index))

    /** Codes are lowercase letters (the engine drops anything else). */
    fun normalizeCode(code: String): String = code.lowercase().filter { it in 'a'..'z' }

    private fun run(context: Context, request: JSONObject): List<Phrase> {
        val dir = PinyinEngine.userDir(context).apply { mkdirs() }
        val json = try {
            Native.phrases(dir.absolutePath, request.toString())
        } catch (error: Throwable) {
            Log.e(TAG, "phrases failed", error)
            null
        } ?: return emptyList()
        val array = JSONObject(json).optJSONArray("phrases") ?: return emptyList()
        if (request.optString("op") != "list") {
            context.sendBroadcast(android.content.Intent(ACTION_CHANGED).setPackage(context.packageName))
        }
        return List(array.length()) { i ->
            val item = array.getJSONObject(i)
            Phrase(item.optString("text"), item.optString("code"), item.optString("group"))
        }
    }

    private const val TAG = "QingshuPhrases"
}
