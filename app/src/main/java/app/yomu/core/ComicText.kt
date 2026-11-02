package app.yomu.core

import app.yomu.Yomu
import java.io.File
import java.security.MessageDigest

/**
 * The words inside each comic, as far as read-aloud has read them (speech bubbles, narration).
 * One small text file per comic, one line per bubble, no duplicates. Used by the library search.
 */
object ComicText {

    /** Enough for many chapters of dialogue; keeps the search fast. */
    private const val MAX_BYTES = 400_000

    private val dir by lazy { File(Yomu.app.filesDir, "comic_text").apply { mkdirs() } }

    private fun fileFor(key: String): File {
        val hash = MessageDigest.getInstance("SHA-1").digest(key.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(dir, "$hash.txt")
    }

    @Synchronized
    fun add(key: String, lines: List<String>) {
        val clean = lines.map { it.replace(Regex("\\s+"), " ").trim() }.filter { it.length >= 2 }
        if (clean.isEmpty()) return
        runCatching {
            val file = fileFor(key)
            val existing = if (file.exists()) file.readLines().toHashSet() else HashSet()
            val fresh = clean.filter { existing.add(it) }
            if (fresh.isEmpty() || file.length() > MAX_BYTES) return
            file.appendText(fresh.joinToString("\n", postfix = "\n"))
        }
    }

    @Synchronized
    fun read(key: String): List<String> =
        runCatching { fileFor(key).takeIf { it.exists() }?.readLines()?.filter { it.isNotBlank() } }.getOrNull().orEmpty()
}
