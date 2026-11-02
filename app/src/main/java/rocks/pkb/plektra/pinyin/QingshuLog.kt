package rocks.pkb.plektra.pinyin

import java.util.concurrent.TimeUnit

/**
 * Reads this app's recent log lines (keyboard service included, it runs in the same app), so they
 * can be attached to the debug report on the home screen. An app may read its own logs without
 * any permission.
 */
object QingshuLog {
    fun dump(maxLines: Int = 3000): String {
        return try {
            val process = ProcessBuilder("logcat", "-d", "-v", "time", "-t", maxLines.toString())
                .redirectErrorStream(true)
                .start()
            val text = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor(3, TimeUnit.SECONDS)
            text
        } catch (error: Exception) {
            "(could not read logcat: ${error.message})"
        }
    }
}
