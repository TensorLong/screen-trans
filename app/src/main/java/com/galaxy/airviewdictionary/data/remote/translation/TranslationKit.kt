package com.galaxy.airviewdictionary.data.remote.translation

import com.galaxy.airviewdictionary.data.remote.translation.Language


abstract class TranslationKit {

    protected val TAG: String = javaClass.simpleName

    abstract fun available(): Boolean

    abstract val supportedLanguagesAsSource: List<Language>

    abstract val supportedLanguagesAsTarget: List<Language>

    /**
     * Language codes for source languages supported by Translator.
     */
    abstract fun isSupportedAsSource(code: String, targetLanguageCode: String): Boolean

    /**
     * Language codes for target languages supported by Translator.
     */
    abstract fun isSupportedAsTarget(code: String, sourceLanguageCode: String): Boolean

    abstract fun isLanguageSwappable(sourceLanguageCode: String, targetLanguageCode: String): Boolean

    /**
     * Request translation.
     * This function would block current thread and coroutine cannot be properly suspended.
     * Therefore, it must be used within 'viewModelScope.launch' syntax.
     *
     * [TranslationResponse] Translation response object
     */
    abstract suspend fun request(
        sourceLanguageCode: String,
        targetLanguageCode: String,
        sourceText: String
    ): TranslationResponse

}


