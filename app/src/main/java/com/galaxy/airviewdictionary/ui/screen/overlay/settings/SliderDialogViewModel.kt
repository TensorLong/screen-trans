package com.galaxy.airviewdictionary.ui.screen.overlay.settings

import android.speech.tts.Voice
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.galaxy.airviewdictionary.data.local.preference.PreferenceRepository
import com.galaxy.airviewdictionary.data.local.tts.TTSRepository
import com.galaxy.airviewdictionary.data.remote.translation.TranslationKitType
import com.galaxy.airviewdictionary.data.remote.translation.TranslationRepository
import com.galaxy.airviewdictionary.extensions.language
import com.galaxy.airviewdictionary.data.remote.translation.TranslationResponse
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber


@Suppress("UNCHECKED_CAST")
class SliderDialogViewModelFactory(
    private val preferenceRepository: PreferenceRepository,
    private val translationRepository: TranslationRepository,
    private val ttsRepository: TTSRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SliderDialogViewModel::class.java)) {
            return SliderDialogViewModel(
                preferenceRepository = preferenceRepository,
                translationRepository = translationRepository,
                ttsRepository = ttsRepository,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel Class")
    }
}

class SliderDialogViewModel(
    val preferenceRepository: PreferenceRepository,
    val translationRepository: TranslationRepository,
    private val ttsRepository: TTSRepository,
) : ViewModel() {

    private val TAG = javaClass.simpleName

    fun isLanguageSwappable(sourceLanguageCode: String, targetLanguageCode: String, kitType: TranslationKitType): Boolean {
        return translationRepository.isLanguageSwappable(sourceLanguageCode, targetLanguageCode, kitType)
    }

    fun playSampleVoice(text_: String = "I speak at this rate.") {
        viewModelScope.launch {
            val ttsSpeechRate = preferenceRepository.ttsSpeechRateFlow.first()
            val voice: Voice? = ttsRepository.currentVoiceFlow.first()
            var text = text_

            voice?.let {
                if (voice.language.code != "en") {
                    val response: TranslationResponse = translationRepository.request(
                        TranslationKitType.GOOGLE,
                        "en",
                        voice.language.code,
                        text
                    )
                    if (response is TranslationResponse.Success) {
                        text = response.result.resultText ?: text
                    }
                }
            }

            ttsRepository.playTTS(text, ttsSpeechRate)
        }
    }

    init {
        Timber.tag(TAG).i("#### init ####")
        translationRepository.acquire()
        ttsRepository.acquire()
    }

    override fun onCleared() {
        translationRepository.release()
        ttsRepository.release()
        super.onCleared()
    }
}








