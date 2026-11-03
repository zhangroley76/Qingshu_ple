package rocks.pkb.plektra.pinyin

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import app.qingjian.inputmethod.core.Native
import java.io.File
import java.util.concurrent.Executors

/**
 * Owns the Qingjian engine handle.
 *
 * Loading (copying the bundled data out of the APK and assembling the engine) runs on a
 * background thread; every other call happens on the main thread, as the engine requires.
 * Calls made before the engine is ready return null and the caller falls back to plain letters.
 */
class PinyinEngine(context: Context) {
    enum class State { IDLE, LOADING, READY, FAILED }

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val loader = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "qingshu-pinyin-load").apply { isDaemon = true }
    }
    private var handle = 0L
    private var associationHandle = 0L
    private val readyListeners = mutableListOf<() -> Unit>()

    var state: State = State.IDLE
        private set

    /** Why loading failed, for logs and the toast shown to the user. */
    var failureReason: String? = null
        private set

    val isReady: Boolean get() = state == State.READY && handle != 0L

    /** Starts loading if needed; [onReady] runs on the main thread once the engine can be used. */
    fun ensureLoaded(onReady: (() -> Unit)? = null) {
        if (isReady) {
            onReady?.invoke()
            return
        }
        // A failed load stays failed until resetFailure(); retrying on every key only floods the log.
        if (state == State.FAILED) return
        onReady?.let { readyListeners += it }
        if (state == State.LOADING) return
        state = State.LOADING
        loader.execute {
            val started = System.currentTimeMillis()
            val result = runCatching { load() }
            mainHandler.post {
                result.onSuccess { (created, association) ->
                    handle = created
                    associationHandle = association
                    state = State.READY
                    applySettings()
                    Log.i(TAG, "Engine ready in ${System.currentTimeMillis() - started} ms (association=${association != 0L})")
                    val listeners = readyListeners.toList()
                    readyListeners.clear()
                    listeners.forEach { it() }
                }.onFailure { error ->
                    state = State.FAILED
                    failureReason = error.message ?: error.javaClass.simpleName
                    readyListeners.clear()
                    Log.e(TAG, "Engine failed to load", error)
                }
            }
        }
    }

    /** Lets a later [ensureLoaded] try again, e.g. after the user installed the missing files. */
    fun resetFailure() {
        if (state == State.FAILED) state = State.IDLE
    }

    private fun load(): Pair<Long, Long> {
        val dataDir = File(appContext.filesDir, "qingjian/data")
        val userDir = userDir(appContext)
        userDir.mkdirs()
        extractBundledData(dataDir)
        val created = try {
            Native.create(dataDir.absolutePath, userDir.absolutePath)
        } catch (error: Throwable) {
            // UnsatisfiedLinkError / ExceptionInInitializerError: the .so is missing from the APK.
            throw IllegalStateException("libqingjian_android.so missing — run scripts/fetch-qingjian-engine.sh", error)
        }
        check(created != 0L) { "engine assembly failed (see logcat tag qingjian)" }
        return created to loadAssociation(File(dataDir, LANGUAGE_MODEL))
    }

    /** Association is optional: it needs lm.qj and an engine built with Qingshu's patch. */
    private fun loadAssociation(model: File): Long {
        if (!model.isFile) {
            Log.i(TAG, "No ${model.name}; association off")
            return 0L
        }
        return try {
            Native.createAssociation(model.absolutePath)
        } catch (error: UnsatisfiedLinkError) {
            Log.w(TAG, "Engine library has no association entry points; association off")
            0L
        }
    }

    /**
     * Copies `assets/qingjian/` to [dataDir]. The engine memory-maps its `.qj` files, so they must be
     * real files. Copies again only when the APK was updated.
     */
    private fun extractBundledData(dataDir: File) {
        val packageInfo = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
        val stamp = packageInfo.lastUpdateTime.toString()
        val stampFile = File(dataDir, ".stamp")
        if (stampFile.isFile && stampFile.readText() == stamp && File(dataDir, DICTIONARY).isFile) return

        val names = appContext.assets.list(ASSET_DIR).orEmpty()
        check(DICTIONARY in names) { "assets/$ASSET_DIR/$DICTIONARY missing — run scripts/fetch-qingjian-engine.sh" }
        dataDir.deleteRecursively()
        dataDir.mkdirs()
        copyAssets(ASSET_DIR, dataDir)
        stampFile.writeText(stamp)
    }

    /** Copies an asset folder recursively (domain dictionaries live in `dicts/`). */
    private fun copyAssets(assetPath: String, target: File) {
        target.mkdirs()
        for (name in appContext.assets.list(assetPath).orEmpty()) {
            val child = "$assetPath/$name"
            val children = appContext.assets.list(child).orEmpty()
            if (children.isNotEmpty()) {
                copyAssets(child, File(target, name))
                continue
            }
            val file = File(target, name)
            val partial = File(target, "$name.part")
            appContext.assets.open(child).use { input ->
                partial.outputStream().use { output -> input.copyTo(output, 1 shl 16) }
            }
            check(partial.renameTo(file)) { "could not write $file" }
        }
    }

    private inline fun call(block: (Long) -> String?): PinyinFrame? {
        if (!isReady) return null
        return try {
            PinyinFrame.parse(block(handle))
        } catch (error: Throwable) {
            Log.e(TAG, "Engine call failed", error)
            null
        }
    }

    fun push(c: Char): PinyinFrame? = call { Native.push(it, c.code) }
    fun backspace(): PinyinFrame? = call { Native.backspace(it) }
    fun select(index: Int): PinyinFrame? = call { Native.select(it, index) }
    fun commitRaw(): PinyinFrame? = call { Native.commitRaw(it) }
    fun clear(): PinyinFrame? = call { Native.clear(it) }
    fun space(): PinyinFrame? = call { Native.space(it) }

    /** Full-width form of [c] (or [c] itself when it has none). */
    fun punctuate(c: Char): String {
        if (!isReady) return c.toString()
        return try {
            Native.punctuate(handle, c) ?: c.toString()
        } catch (error: Throwable) {
            Log.e(TAG, "punctuate failed", error)
            c.toString()
        }
    }

    /**
     * Writes what the engine learned (the words you picked) to the user directory. The engine only
     * saves on reset, so call this when the field or the keyboard goes away.
     */
    fun flushLearning() {
        if (!isReady) return
        try {
            Native.reset(handle)
        } catch (error: Throwable) {
            Log.e(TAG, "flush failed", error)
        }
    }

    /** Sends Qingshu's Chinese settings (traditional output, fuzzy pinyin, ...) to the engine. */
    fun applySettings() {
        if (!isReady) return
        val variant = QingshuChineseSettings.traditional(appContext)
        try {
            Native.setOptions(handle, QingshuChineseSettings.optionsJson(appContext))
            Native.setTraditional(handle, variant != QingshuChineseSettings.TRADITIONAL_OFF)
        } catch (error: Throwable) {
            Log.e(TAG, "applySettings failed", error)
        }
        if (associationHandle != 0L) {
            try {
                Native.setAssociationTraditional(associationHandle, variant)
            } catch (error: UnsatisfiedLinkError) {
                Log.w(TAG, "Engine has no traditional association; association stays simplified")
            }
        }
        Log.i(TAG, "Settings applied: traditional=$variant")
    }

    /** Re-reads the user phrases after the settings screen changed them. */
    fun reloadPhrases() {
        if (!isReady) return
        try {
            Native.reloadPhrases(handle, userDir(appContext).absolutePath)
        } catch (error: Throwable) {
            Log.e(TAG, "reloadPhrases failed", error)
        }
    }

    val hasAssociation: Boolean get() = isReady && associationHandle != 0L

    /** Likely next words after [before] (the text before the cursor); empty when unavailable. */
    fun associate(before: String, limit: Int): List<String> {
        if (!hasAssociation || before.isEmpty()) return emptyList()
        return try {
            val json = Native.associate(associationHandle, before, limit) ?: return emptyList()
            val array = org.json.JSONArray(json)
            List(array.length()) { array.optString(it) }.filter { it.isNotEmpty() }
        } catch (error: Throwable) {
            Log.e(TAG, "associate failed", error)
            emptyList()
        }
    }

    fun destroy() {
        if (associationHandle != 0L) {
            try {
                Native.destroyAssociation(associationHandle)
            } catch (error: Throwable) {
                Log.e(TAG, "destroyAssociation failed", error)
            }
            associationHandle = 0L
        }
        if (handle != 0L) {
            try {
                Native.destroy(handle)
            } catch (error: Throwable) {
                Log.e(TAG, "destroy failed", error)
            }
        }
        handle = 0L
        state = State.IDLE
        loader.shutdown()
    }

    companion object {
        /** Learned words, phrases and stats; kept across updates (only the data directory is re-extracted). */
        fun userDir(context: Context): File = File(context.applicationContext.filesDir, "qingjian/user")

        private const val TAG = "QingshuPinyin"
        private const val ASSET_DIR = "qingjian"
        private const val DICTIONARY = "dict.qj"
        private const val LANGUAGE_MODEL = "lm.qj"
    }
}
