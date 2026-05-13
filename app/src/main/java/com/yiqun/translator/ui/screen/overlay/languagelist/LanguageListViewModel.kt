package com.yiqun.translator.ui.screen.overlay.languagelist

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yiqun.translator.data.local.preference.PreferenceRepository
import com.yiqun.translator.data.remote.translation.TranslationKitType
import com.yiqun.translator.data.remote.translation.TranslationRepository
import com.yiqun.translator.data.remote.translation.Language
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber


@Suppress("UNCHECKED_CAST")
class LanguageListViewModelFactory(
    private val applicationContext: Context,
    private val preferenceRepository: PreferenceRepository,
    private val translationRepository: TranslationRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LanguageListViewModel::class.java)) {
            return LanguageListViewModel(
                applicationContext = applicationContext,
                preferenceRepository = preferenceRepository,
                translationRepository = translationRepository,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel Class")
    }
}

class LanguageListViewModel(
    private val applicationContext: Context,
    val preferenceRepository: PreferenceRepository,
    val translationRepository: TranslationRepository,
) : ViewModel() {

    private val TAG = javaClass.simpleName

    val supportedLanguagesAsSource: List<Language> = translationRepository.supportedLanguagesAsSource

    val supportedLanguagesAsTarget: List<Language> = translationRepository.supportedLanguagesAsTarget

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //                                                                                            //
    //                                       preference                                           //
    //                                                                                            //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    val sourceLanguageCodeHistoryFlow = preferenceRepository.sourceLanguageCodeHistoryFlow
        .map { stringList ->
            stringList.map { languageCode -> translationRepository.getSupportedSourceLanguage(languageCode) }
        }

    val targetLanguageCodeHistoryFlow = preferenceRepository.targetLanguageCodeHistoryFlow
        .map { stringList ->
            stringList.map { languageCode -> translationRepository.getSupportedTargetLanguage(languageCode) }
        }

    /**
     */
    fun updateLanguage(isSourceLanguage: Boolean, language: Language, oppositeLanguage: Language) {
        viewModelScope.launch {
            val kitType: TranslationKitType = preferenceRepository.translationKitTypeFlow.first()
            val commonKitTypes = mutableListOf<TranslationKitType>().apply {
                if (language.supportKitTypes.contains(kitType) && oppositeLanguage.supportKitTypes.contains(kitType)) {
                    add(kitType)
                }
                addAll(language.supportKitTypes.intersect(oppositeLanguage.supportKitTypes.toSet()).filter { it != kitType })
            }

            for (currentKitType in commonKitTypes) {
                val isSupported = if (isSourceLanguage) {
                    translationRepository.isSupportedAsSource(currentKitType, language.code, oppositeLanguage.code)
                } else {
                    translationRepository.isSupportedAsTarget(currentKitType, language.code, oppositeLanguage.code)
                }
                Timber.tag(TAG).d("updateLanguage kitType $kitType language $language oppositeLanguage $oppositeLanguage isSupported $isSupported")
                if (isSupported) {
                    preferenceRepository.addOrUpdateLanguageHistory(language.code, isSourceLanguage)
                    preferenceRepository.update(PreferenceRepository.TRANSLATION_KIT_TYPE, currentKitType.name)
                    break
                }
            }
        }
    }

    init {
        Timber.tag(TAG).i("#### init ####")
        translationRepository.acquire()
    }

    override fun onCleared() {
        translationRepository.release()
        super.onCleared()
    }
}








