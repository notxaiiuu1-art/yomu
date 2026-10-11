package app.yomu.ai

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import app.yomu.Yomu
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Speaks script lines with the phone's own voices (Google TTS on the Pixel, works offline).
 * Each speaker type gets its own voice; without chosen voices it varies the pitch instead.
 */
class ReadAloud(context: Context) {

    private val ready = CompletableDeferred<Boolean>()
    private val pending = ConcurrentHashMap<String, CompletableDeferred<Unit>>()

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready.complete(status == TextToSpeech.SUCCESS)
    }

    init {
        tts.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) {
                    utteranceId?.let { pending.remove(it)?.complete(Unit) }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    utteranceId?.let { pending.remove(it)?.complete(Unit) }
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    utteranceId?.let { pending.remove(it)?.complete(Unit) }
                }

                override fun onStop(utteranceId: String?, interrupted: Boolean) {
                    utteranceId?.let { pending.remove(it)?.complete(Unit) }
                }
            },
        )
    }

    suspend fun voices(language: String): List<Voice> {
        if (!ready.await()) return emptyList()
        return runCatching { tts.voices.orEmpty() }.getOrDefault(emptySet())
            .filter { it.locale.language == language }
            .sortedWith(compareBy({ it.isNetworkConnectionRequired }, { it.name }))
    }

    /** Speaks one line and returns when it has been spoken (or the coroutine is cancelled). */
    suspend fun speak(line: SpokenLine, language: String) {
        if (!ready.await()) throw IllegalStateException("Sprachausgabe ist auf diesem Gerät nicht verfügbar.")
        val lang = language.ifBlank { "de" }
        configure(line.speaker, lang)

        val id = UUID.randomUUID().toString()
        val done = CompletableDeferred<Unit>()
        pending[id] = done
        tts.speak(line.text, TextToSpeech.QUEUE_FLUSH, Bundle(), id)
        try {
            done.await()
        } catch (e: CancellationException) {
            tts.stop()
            throw e
        } finally {
            pending.remove(id)
        }
    }

    fun stop() {
        tts.stop()
        pending.values.forEach { it.complete(Unit) }
        pending.clear()
    }

    private suspend fun configure(speaker: Speaker, language: String) {
        val prefs = Yomu.prefs
        val chosenName = when (speaker) {
            Speaker.FEMALE, Speaker.CHILD -> prefs.voiceFemale.get()
            Speaker.MALE -> prefs.voiceMale.get()
            Speaker.NARRATOR -> prefs.voiceNarrator.get()
        }
        val available = voices(language)
        val chosen = available.firstOrNull { it.name == chosenName }

        if (chosen != null) {
            tts.voice = chosen
            tts.setPitch(if (speaker == Speaker.CHILD) 1.3f else 1.0f)
        } else {
            // No voice picked: use different installed voices where possible, plus pitch, so
            // women, men and the narrator still sound clearly different.
            val offline = available.filter { !it.isNetworkConnectionRequired }.ifEmpty { available }
            val voice = when (speaker) {
                Speaker.MALE -> offline.getOrNull(1) ?: offline.firstOrNull()
                Speaker.NARRATOR -> offline.getOrNull(2) ?: offline.firstOrNull()
                else -> offline.firstOrNull()
            }
            if (voice != null) tts.voice = voice else tts.language = Locale.forLanguageTag(language)
            tts.setPitch(
                when (speaker) {
                    Speaker.FEMALE -> 1.15f
                    Speaker.MALE -> 0.8f
                    Speaker.CHILD -> 1.4f
                    Speaker.NARRATOR -> 1.0f
                },
            )
        }
        tts.setSpeechRate(prefs.ttsRate.get())
    }

    /** Plays a short sample, used by the voice picker in the settings. */
    suspend fun preview(voiceName: String, language: String) {
        if (!ready.await()) return
        voices(language).firstOrNull { it.name == voiceName }?.let { tts.voice = it }
        tts.setPitch(1f)
        tts.setSpeechRate(Yomu.prefs.ttsRate.get())
        val sample = if (language == "de") "Hallo! So klinge ich beim Vorlesen." else "Hi! This is how I sound when reading."
        tts.speak(sample, TextToSpeech.QUEUE_FLUSH, Bundle(), "preview")
    }
}
