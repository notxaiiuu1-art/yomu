package eu.kanade.tachiyomi.network

import okhttp3.MediaType
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.Source
import okio.buffer

interface ProgressListener {
    fun update(bytesRead: Long, contentLength: Long, done: Boolean)
}

class ProgressResponseBody(
    private val delegate: ResponseBody,
    private val listener: ProgressListener,
) : ResponseBody() {

    private val tracked: BufferedSource by lazy { track(delegate.source()).buffer() }

    override fun contentType(): MediaType? = delegate.contentType()
    override fun contentLength(): Long = delegate.contentLength()
    override fun source(): BufferedSource = tracked

    private fun track(source: Source): Source = object : ForwardingSource(source) {
        var total = 0L
        override fun read(sink: Buffer, byteCount: Long): Long {
            val read = super.read(sink, byteCount)
            if (read != -1L) total += read
            listener.update(total, delegate.contentLength(), read == -1L)
            return read
        }
    }
}
