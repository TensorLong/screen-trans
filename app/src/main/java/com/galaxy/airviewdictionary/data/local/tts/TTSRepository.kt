package com.galaxy.airviewdictionary.data.local.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.galaxy.airviewdictionary.data.AVDRepository
import com.galaxy.airviewdictionary.ui.screen.overlay.translation.TTSStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TTSRepository @Inject constructor(@ApplicationContext val context: Context) : AVDRepository() {

    private var tts: TextToSpeech? = null
    private var ttsForText: TextToSpeech? = null

    val ttsStatusFlow = MutableStateFlow(TTSStatus.Uninitialized)

    val currentVoiceFlow = MutableStateFlow<Voice?>(null)

    val availableVoicesFlow = MutableStateFlow<List<Voice>>(emptyList())

    private fun initTTS(locale: Locale, attempts: Int = 0) {
        Timber.tag(TAG).i("=========================== initTTS ========================== $locale $attempts")
        tts?.shutdown()
        tts = null

        tts = TextToSpeech(context) { status ->
            launchInAVDCoroutineScope {
                delay(500)
                val success = status == TextToSpeech.SUCCESS
                        && tts?.voices?.isNotEmpty() ?: false
                        && tts?.voice != null

                Timber.tag(TAG).i("TextToSpeech result status $status success $success")

                if (success) {
                    tts?.setLanguage(locale)
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            Timber.tag(TAG).i("tts onStart $utteranceId")
                            ttsStatusFlow.value = TTSStatus.Playing
                        }

                        override fun onDone(utteranceId: String?) {
                            Timber.tag(TAG).i("tts onDone $utteranceId")
                            ttsStatusFlow.value = TTSStatus.Idle
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            Timber.tag(TAG).i("tts onError $utteranceId")
                            ttsStatusFlow.value = TTSStatus.Idle
                        }

                        override fun onStop(utteranceId: String?, interrupted: Boolean) {
                            Timber.tag(TAG).i("tts onStop $utteranceId $interrupted")
                            ttsStatusFlow.value = TTSStatus.Idle
                        }
                    })
                    ttsStatusFlow.value = TTSStatus.Idle
                    currentVoiceFlow.value = tts?.voice
                    availableVoicesFlow.value = tts?.voices?.toList() ?: emptyList()
                } else if (attempts < 10) {
                    val delayTime = (2000 + 1000 * attempts).toLong()
                    Timber.tag(TAG).w("initTTS failed, retrying in ${delayTime}ms... Attempt: ${attempts + 1}")
                    delay(delayTime)
                    initTTS(locale, attempts + 1)
                } else {
                    Timber.tag(TAG).e("initTTS completely failed after $attempts attempts.")
                    ttsStatusFlow.value = TTSStatus.Uninitialized
                }
            }
        }
    }

    private fun initTTSForText(attempts: Int = 0) {
        Timber.tag(TAG).i("=========================== initTTSForText ==========================")
        ttsForText?.shutdown()
        ttsForText = null

        ttsForText = TextToSpeech(context) { status ->
            launchInAVDCoroutineScope {
                delay(1000)
                val success = status == TextToSpeech.SUCCESS
                        && ttsForText?.voices != null
                        && ttsForText?.voice != null

                Timber.tag(TAG).i("initTTSForText result status $status success $success")

                if (!success && attempts < 10) {
                    val delayTime = (2000 + 1000 * attempts).toLong()
                    Timber.tag(TAG).w("initTTSForText failed, retrying in ${delayTime}ms... Attempt: ${attempts + 1}")
                    delay(delayTime)
                    initTTSForText(attempts + 1)
                }
            }
        }
    }

    fun stopTTS() {
        Timber.tag(TAG).i("------------ stopTTS --------------")
        tts?.stop()
    }

    private fun clearTTS() {
        Timber.tag(TAG).i("------------ clearTTS --------------")
        ttsStatusFlow.value = TTSStatus.Uninitialized
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
        try {
            tts?.shutdown()
        } catch (_: Exception) {
        }
        try {
            ttsForText?.stop()
        } catch (_: Exception) {
        }
        try {
            ttsForText?.shutdown()
        } catch (_: Exception) {
        }
    }

    private fun getCurrentLocale(): Locale {
        return context.resources.configuration.locales.get(0)
    }

    fun setVoice(voiceName: String) {
        launchInAVDCoroutineScope {
            Timber.tag(TAG).i("setVoice ${tts?.voice} to $voiceName")
            val matchingVoice = availableVoicesFlow.first()?.firstOrNull { it.name == voiceName }
            Timber.tag(TAG).i("matchingVoice  $matchingVoice")
            matchingVoice?.let {
                tts?.setVoice(it)
                currentVoiceFlow.value = it
            }
        }
    }

    fun playTTS(text: String, speechRate: Float? = 1.0f) {
        Timber.tag(TAG).i("playTTS Starting TTS playback for text: $text")
        val utteranceId = UUID.randomUUID().toString()
        speechRate?.let { tts?.setSpeechRate(it) }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun playTestTTS(text: String, speechRate: Float? = 1.0f, voice: Voice? = null) {
        Timber.tag(TAG).i("playTestTTS Starting TTS playback for text: $text")
        val utteranceId = UUID.randomUUID().toString()
        speechRate?.let { ttsForText?.setSpeechRate(it) }
        voice?.let { ttsForText?.setVoice(it) }
        val result = ttsForText?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        Timber.tag(TAG).i("playTestTTS speak result: $result")
    }

    override fun onZeroReferences() {
        Timber.tag(TAG).i("onZeroReferences called, cancelling all coroutines and clearing TTS resources")
        clearTTS()
    }

    init {
        initTTS(getCurrentLocale())
        initTTSForText()
    }
}
