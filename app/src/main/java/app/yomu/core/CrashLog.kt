package app.yomu.core

import android.content.Context
import android.os.Build
import app.yomu.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes the reason for a crash to a file, so the app can show it on the next start and it
 * can be sent along for a fix.
 */
object CrashLog {

    private lateinit var file: File

    fun install(context: Context) {
        file = File(context.filesDir, "last_crash.txt")
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val time = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.GERMANY).format(Date())
                file.writeText(
                    buildString {
                        appendLine("Yomu ${BuildConfig.VERSION_NAME} · Android ${Build.VERSION.RELEASE} · ${Build.MODEL}")
                        appendLine("$time · Thread: ${thread.name}")
                        appendLine()
                        append(error.stackTraceToString().take(12_000))
                    },
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun read(): String? = if (::file.isInitialized && file.exists()) runCatching { file.readText() }.getOrNull() else null

    fun clear() {
        if (::file.isInitialized) file.delete()
    }
}
