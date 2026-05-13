package com.yiqun.translator.ui.screen.overlay.voicelist

import android.content.Context
import android.speech.tts.Voice
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yiqun.translator.data.local.preference.PreferenceRepository
import com.yiqun.translator.data.local.tts.TTSRepository
import com.yiqun.translator.data.remote.translation.TranslationKitType
import com.yiqun.translator.data.remote.translation.TranslationRepository
import com.yiqun.translator.extensions.language
import com.yiqun.translator.data.remote.translation.Language
import com.yiqun.translator.data.remote.translation.TranslationResponse
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import timber.log.Timber


@Suppress("UNCHECKED_CAST")
class VoiceListViewModelFactory(
    private val applicationContext: Context,
    private val preferenceRepository: PreferenceRepository,
    private val translationRepository: TranslationRepository,
    private val ttsRepository: TTSRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(VoiceListViewModel::class.java)) {
            return VoiceListViewModel(
                applicationContext = applicationContext,
                preferenceRepository = preferenceRepository,
                translationRepository = translationRepository,
                ttsRepository = ttsRepository,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel Class")
    }
}

class VoiceListViewModel(
    private val applicationContext: Context,
    val preferenceRepository: PreferenceRepository,
    private val translationRepository: TranslationRepository,
    private val ttsRepository: TTSRepository,
) : ViewModel() {

    private val TAG = javaClass.simpleName

    fun playSampleVoice(voice: Voice, text_: String = "It's a voice like this.") {
        viewModelScope.launch {
            val ttsSpeechRate = preferenceRepository.ttsSpeechRateFlow.first()
            var text = text_

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

            ttsRepository.playTestTTS(
                text,
                ttsSpeechRate,
                voice
            )
        }
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    //                                                                                            //
    //                                       preference                                           //
    //                                                                                            //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private val ttsOrderedVoiceNamesFlow: Flow<List<String>> = preferenceRepository.ttsOrderedVoiceNamesFlow

    @OptIn(ExperimentalCoroutinesApi::class)
    val voicesFlow = ttsOrderedVoiceNamesFlow.flatMapLatest { orderedVoiceNames ->
        Timber.tag(TAG).d("orderedVoiceNames $orderedVoiceNames")
        val voiceLanguagePairList = mutableListOf<Pair<Voice, Language>>()
        val availableVoices = ttsRepository.availableVoicesFlow.filterNotNull().first()
        if (orderedVoiceNames.isEmpty()) {
            voiceLanguagePairList.addAll(
                availableVoices.map { voice -> voice to voice.language }
            )
        } else {
            orderedVoiceNames.mapNotNull { orderedVoice ->
                availableVoices.find { it.name == orderedVoice }?.also { voice ->
                    voiceLanguagePairList.add(voice to voice.language)
                }
            }
        }
        voiceLanguagePairList.sortBy { it.second.displayName }
        Timber.tag(TAG).d("ttsRepository.availableVoices ${availableVoices.map { voice -> voice.name }}")
        Timber.tag(TAG).d("voiceLanguagePairList ${voiceLanguagePairList.map { voice -> voice.first.name }}")

        availableVoices.forEach { availableVoice ->
            if (voiceLanguagePairList.none { it.first.name == availableVoice.name }) {
                voiceLanguagePairList.findLast { it.second.displayName == availableVoice.language.displayName }
                    ?.let { lastMatching ->
                        val index = voiceLanguagePairList.indexOf(lastMatching) + 1
                        voiceLanguagePairList.add(index, availableVoice to availableVoice.language)
                    } ?: voiceLanguagePairList.add(availableVoice to availableVoice.language)
            }
        }

        flowOf(
            voiceLanguagePairList.mapIndexed { index, pair ->
                Triple(index, pair.first, pair.second)
            }
        )
    }

    fun addOrUpdateOrderedVoiceNames(orderedVoices: List<Triple<Int, Voice, Language>>) {
        viewModelScope.launch {
            preferenceRepository.addOrUpdateOrderedVoiceNames(orderedVoices.map { triple -> triple.second })
            val currentVoice: Voice? = ttsRepository.currentVoiceFlow.first()
            if(currentVoice != null ) {
                val matchingVoiceItem = orderedVoices.firstOrNull { triple ->
                    triple.second.name.startsWith(currentVoice.language.code)
                }
                matchingVoiceItem?.let {
                    ttsRepository.setVoice(matchingVoiceItem.second.name)
                }
            }
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








