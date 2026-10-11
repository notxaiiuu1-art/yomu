package app.yomu.core

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import app.yomu.Yomu
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * Minimal image pipeline: download through the source's own client (so referer, cookies and
 * Cloudflare clearance apply), cache on disk, decode down-sampled to the size shown.
 */
object Images {

    private val memory = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 6).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    private val coverLimit = Semaphore(6)
    private val pageLimit = Semaphore(4)

    private fun md5(text: String): String =
        MessageDigest.getInstance("MD5").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    private val coverDir by lazy { File(Yomu.app.cacheDir, "covers").apply { mkdirs() } }
    val pageDir by lazy { File(Yomu.app.cacheDir, "pages").apply { mkdirs() } }

    suspend fun cover(url: String, source: Source?, targetWidth: Int): Bitmap? {
        val key = "c|$url|$targetWidth"
        memory.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            coverLimit.withPermit {
                val file = File(coverDir, md5(url))
                if (!file.exists() || file.length() == 0L) {
                    val http = source as? HttpSource
                    val client = http?.client ?: Yomu.network.client
                    val request = if (http != null) GET(url, http.headers) else GET(url)
                    client.newCall(request).awaitSuccess().use { response ->
                        val tmp = File(coverDir, file.name + ".tmp")
                        tmp.outputStream().use { out -> response.body.byteStream().copyTo(out) }
                        tmp.renameTo(file)
                    }
                }
                decode(file, targetWidth, 4096)?.also { memory.put(key, it) }
            }
        }
    }

    /** Makes sure the image for [page] is on disk and returns the file. */
    suspend fun pageFile(source: HttpSource, chapterUrl: String, page: Page): File = withContext(Dispatchers.IO) {
        val file = File(pageDir, md5("${source.id}|$chapterUrl|${page.index}"))
        if (file.exists() && file.length() > 0) return@withContext file
        pageLimit.withPermit {
            if (file.exists() && file.length() > 0) return@withPermit
            if (page.imageUrl.isNullOrEmpty()) page.imageUrl = source.getImageUrl(page)
            source.getImage(page).use { response ->
                val tmp = File(pageDir, file.name + ".tmp")
                tmp.outputStream().use { out -> response.body.byteStream().copyTo(out) }
                tmp.renameTo(file)
            }
        }
        file
    }

    suspend fun pageBitmap(file: File, targetWidth: Int): Bitmap? {
        val key = "p|${file.name}|$targetWidth"
        memory.get(key)?.let { return it }
        return withContext(Dispatchers.Default) {
            decode(file, targetWidth, 12_000)?.also { memory.put(key, it) }
        }
    }

    /**
     * Decodes [file] so it is about [targetWidth] wide and never taller than [maxHeight]
     * (very long webtoon strips would otherwise exceed what the GPU can draw).
     */
    private fun decode(file: File, targetWidth: Int, maxHeight: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= targetWidth) sample *= 2
        val scale = targetWidth.toFloat() / (bounds.outWidth / sample)
        while ((bounds.outHeight / sample) * minOf(scale, 1f) > maxHeight * 1.5f) sample *= 2

        val decoded = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            },
        ) ?: return null

        // Fine-scale to the exact width, capped by the height limit.
        val ratio = minOf(targetWidth.toFloat() / decoded.width, maxHeight.toFloat() / decoded.height, 1f)
        if (ratio >= 0.99f) return decoded
        val scaled = Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * ratio).toInt().coerceAtLeast(1),
            (decoded.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
        if (scaled !== decoded) decoded.recycle()
        return scaled
    }

    fun clearPageCache() {
        pageDir.listFiles()?.forEach { it.delete() }
    }
}
