package app.yomu.ai

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import app.yomu.Yomu
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

/**
 * Speaks script lines. Two engines:
 *  - Fish Audio (cloud, realistic voices, one voice model per speaker type), or
 *  - the phone's own voices (Google TTS, free and offline), varied by voice and pitch.
 */
class ReadAloud(context: Context) {

    private val ready = CompletableDeferred<Boolean>()
    private val pending = ConcurrentHashMap<String, CompletableDeferred<Unit>>()
    private var player: MediaPlayer? = null

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

    val usesFish: Boolean get() = Yomu.prefs.useFishAudio.get()

    /** Last Fish Audio problem, shown in the reader so a fallback to the phone voice is never silent. */
    val fishError = MutableStateFlow<String?>(null)

    /** Starts generating the audio for an upcoming line, so it plays without a gap. */
    fun prefetch(line: SpokenLine, castKey: String? = null) {
        if (!usesFish) return
        Yomu.scope.launch { runCatching { FishAudio.audio(line.text, fishVoice(line, castKey)) } }
    }

    /** Speaks one line and returns when it has finished (or the coroutine is cancelled). */
    /** A character cast for this comic wins; otherwise the voice of the speaker type. */
    private fun fishVoice(line: SpokenLine, castKey: String?): String =
        castKey?.let { VoiceCasting.voiceFor(it, line.name) } ?: FishAudio.voiceFor(line.speaker)

    suspend fun speak(line: SpokenLine, language: String, castKey: String? = null) {
        if (usesFish) {
            val file = try {
                FishAudio.audio(line.text, fishVoice(line, castKey)).also { fishError.value = null }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fishError.value = (e.message ?: e.javaClass.simpleName) + " – lese mit Handy-Stimme"
                null
            }
            if (file != null) {
                withContext(Dispatchers.Main) { play(file) }
            } else {
                speakOnDevice(line, language)
            }
        } else {
            speakOnDevice(line, language)
        }
    }

    fun stop() {
        runCatching { tts.stop() }
        pending.values.forEach { it.complete(Unit) }
        pending.clear()
        player?.let { runCatching { it.stop() }; runCatching { it.release() } }
        player = null
    }

    // ---------------------------------------------------------------- Fish Audio playback

    private suspend fun play(file: File) = suspendCancellableCoroutine { cont ->
        val mp = MediaPlayer()
        player = mp
        fun finish() {
            runCatching { mp.release() }
            if (player === mp) player = null
            if (cont.isActive) cont.resume(Unit)
        }
        try {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            mp.setDataSource(file.absolutePath)
            mp.setOnCompletionListener { finish() }
            mp.setOnErrorListener { _, _, _ ->
                file.delete()
                finish()
                true
            }
            mp.prepare()
            mp.start()
        } catch (e: Exception) {
            file.delete()
            finish()
        }
        cont.invokeOnCancellation {
            runCatching { mp.stop() }
            runCatching { mp.release() }
        }
    }

    // ---------------------------------------------------------------- phone voices

    suspend fun voices(language: String): List<Voice> {
        if (!ready.await()) return emptyList()
        return runCatching { tts.voices.orEmpty() }.getOrDefault(emptySet())
            .filter { it.locale.language == language }
            .sortedWith(compareBy({ it.isNetworkConnectionRequired }, { it.name }))
    }

    private suspend fun speakOnDevice(line: SpokenLine, language: String) {
        if (!ready.await()) throw IllegalStateException("Sprachausgabe ist auf diesem Gerät nicht verfügbar.")
        configure(line.speaker, language.ifBlank { "de" })

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

    private suspend fun configure(speaker: Speaker, language: String) {
        val prefs = Yomu.prefs
        val chosenName = when (speaker) {
            Speaker.YOUNG_WOMAN, Speaker.WOMAN -> prefs.voiceFemale.get()
            Speaker.YOUNG_MAN, Speaker.MAN, Speaker.OLD_MAN -> prefs.voiceMale.get()
            Speaker.NARRATOR -> prefs.voiceNarrator.get()
        }
        val available = voices(language)
        val chosen = available.firstOrNull { it.name == chosenName }
        var rate = prefs.ttsRate.get()

        if (chosen != null) {
            tts.voice = chosen
            tts.setPitch(
                when (speaker) {
                    Speaker.YOUNG_WOMAN -> 1.1f
                    Speaker.OLD_MAN -> 0.85f
                    else -> 1.0f
                },
            )
        } else {
            // No voice picked: different installed voices where possible, plus pitch.
            val offline = available.filter { !it.isNetworkConnectionRequired }.ifEmpty { available }
            val voice = when (speaker) {
                Speaker.YOUNG_MAN, Speaker.MAN, Speaker.OLD_MAN -> offline.getOrNull(1) ?: offline.firstOrNull()
                Speaker.NARRATOR -> offline.getOrNull(2) ?: offline.firstOrNull()
                else -> offline.firstOrNull()
            }
            if (voice != null) tts.voice = voice else tts.language = Locale.forLanguageTag(language)
            tts.setPitch(
                when (speaker) {
                    Speaker.YOUNG_WOMAN -> 1.2f
                    Speaker.WOMAN -> 1.05f
                    Speaker.YOUNG_MAN -> 0.95f
                    Speaker.MAN -> 0.85f
                    Speaker.OLD_MAN -> 0.7f
                    Speaker.NARRATOR -> 1.0f
                },
            )
        }
        if (speaker == Speaker.OLD_MAN) rate *= 0.9f
        tts.setSpeechRate(rate)
    }

    /** Plays a short sample, used by the voice pickers in the settings. */
    suspend fun preview(voiceName: String, language: String) {
        if (!ready.await()) return
        voices(language).firstOrNull { it.name == voiceName }?.let { tts.voice = it }
        tts.setPitch(1f)
        tts.setSpeechRate(Yomu.prefs.ttsRate.get())
        val sample = if (language == "de") "Hallo! So klinge ich beim Vorlesen." else "Hi! This is how I sound when reading."
        tts.speak(sample, TextToSpeech.QUEUE_FLUSH, Bundle(), "preview")
    }

    /** Plays a short Fish Audio sample for a voice ID. */
    suspend fun previewFish(voiceId: String) {
        val sample = if (Yomu.prefs.aiTranslateGerman.get()) "Hallo! So klinge ich beim Vorlesen." else "Hi! This is how I sound when reading."
        val file = FishAudio.audio(sample, voiceId)
        withContext(Dispatchers.Main) { play(file) }
    }
}
