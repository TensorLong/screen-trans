package com.galaxy.airviewdictionary.data.remote.translation.deepl

import android.content.Context
import com.deepl.api.TextResult
import com.deepl.api.Translator
import com.galaxy.airviewdictionary.data.local.secure.ApiKeyInfo
import com.galaxy.airviewdictionary.data.remote.translation.TranslationKit
import com.galaxy.airviewdictionary.data.remote.translation.TranslationKitType
import com.galaxy.airviewdictionary.data.remote.translation.Language
import com.galaxy.airviewdictionary.data.remote.translation.Transaction
import com.galaxy.airviewdictionary.data.remote.translation.TranslationResponse
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeepLKit @Inject constructor(@ApplicationContext val context: Context) : TranslationKit() {

    private lateinit var translator: Translator

    override fun available(): Boolean {
        return ApiKeyInfo.apiKeyAvailable(context)
    }

    override val supportedLanguagesAsSource: List<Language> by lazy {
        supportedSourceLanguageCodes.map { Language(it.lowercase()).apply { supportKitTypes.add(TranslationKitType.DEEPL) } }
    }

    override val supportedLanguagesAsTarget: List<Language> by lazy {
        supportedTargetLanguageCodes.map { Language(it.lowercase()).apply { supportKitTypes.add(TranslationKitType.DEEPL) } }
    }

    override fun isSupportedAsSource(code: String, targetLanguageCode: String): Boolean {
        return supportedLanguagesAsSource.any { it.code.equals(code, ignoreCase = true) } && supportedLanguagesAsTarget.any { it.code.equals(targetLanguageCode, ignoreCase = true) }
    }

    override fun isSupportedAsTarget(code: String, sourceLanguageCode: String): Boolean {
        return supportedLanguagesAsTarget.any { it.code.equals(code, ignoreCase = true) } && supportedLanguagesAsSource.any { it.code.equals(sourceLanguageCode, ignoreCase = true) }
    }

    override fun isLanguageSwappable(sourceLanguageCode: String, targetLanguageCode: String): Boolean {
        return isSupportedAsSource(targetLanguageCode, sourceLanguageCode) && isSupportedAsTarget(sourceLanguageCode, targetLanguageCode)
    }

    private fun getOwnTargetLanguageCode(languageCode: String): String {
        return when (languageCode) {
            "en" -> "EN-US"
            "pt" -> "PT-PT"
            "zh-CN" -> "ZH-HANS"
            "zh-TW" -> "ZH-HANT"
            else -> languageCode
        }
    }

    override suspend fun request(
        sourceLanguageCode: String,
        targetLanguageCode: String,
        sourceText: String
    ): TranslationResponse {
        return try {
            if (!::translator.isInitialized) {
                if (available()) {
                    translator = Translator(ApiKeyInfo.getApiKeyDeepl(context))
                } else {
                    throw IllegalStateException("api key might not have been initialized.")
                }
            }
            val textResult: TextResult = withContext(Dispatchers.IO) {
                translator.translateText(
                    sourceText,
                    if (sourceLanguageCode == "auto") null else sourceLanguageCode,
                    getOwnTargetLanguageCode(targetLanguageCode),
                )
            }
            return TranslationResponse.Success(
                Transaction(
                    sourceLanguageCode = sourceLanguageCode,
                    targetLanguageCode = targetLanguageCode,
                    sourceText = sourceText,
                    translationKitType = TranslationKitType.DEEPL,
                    detectedLanguageCode = textResult.detectedSourceLanguage,
                    resultText = textResult.text
                )
            )
        } catch (e: Exception) {
            TranslationResponse.Error(e)
        }
    }

    companion object {
        val supportedSourceLanguageCodes = arrayOf(
            "auto", // Auto
            "AR", // Arabic
            "BG", // Bulgarian
            "CS", // Czech
            "DA", // Danish
            "DE", // German
            "EL", // Greek
            "EN", // English
            "ES", // Spanish
            "ET", // Estonian
            "FI", // Finnish
            "FR", // French
            "HU", // Hungarian
            "ID", // Indonesian
            "IT", // Italian
            "JA", // Japanese
            "KO", // Korean
            "LT", // Lithuanian
            "LV", // Latvian
            "NB", // Norwegian Bokmål
            "NL", // Dutch
            "PL", // Polish
            "PT", // Portuguese
            "RO", // Romanian
            "RU", // Russian
            "SK", // Slovak
            "SL", // Slovenian
            "SV", // Swedish
            "TR", // Turkish
            "UK", // Ukrainian
            "ZH", // Chinese
        )

        val supportedTargetLanguageCodes = arrayOf(
            "AR", // Arabic
            "BG", // Bulgarian
            "CS", // Czech
            "DA", // Danish
            "DE", // German
            "EL", // Greek
//            "EN", // English
            "EN-GB", // en-gb --------------- English (British)
            "en", // en-us --------------- English (American)
            "ES", // Spanish
            "ET", // Estonian
            "FI", // Finnish
            "FR", // French
            "HU", // Hungarian
            "ID", // Indonesian
            "IT", // Italian
            "JA", // Japanese
            "KO", // Korean
            "LT", // Lithuanian
            "LV", // Latvian
            "NB", // Norwegian Bokmål
            "NL", // Dutch
            "PL", // Polish
//            "PT", // Portuguese  (unspecified variant for backward compatibility; please select PT-BR or PT-PT instead)
            "PT-BR", // pt-br --------------- Portuguese (Brazilian)
            "pt", // pt-pt --------------- Portuguese (excluding Brazilian)
            "RO", // Romanian
            "RU", // Russian
            "SK", // Slovak
            "SL", // Slovenian
            "SV", // Swedish
            "TR", // Turkish
            "UK", // Ukrainian
//            "ZH", // Chinese  Chinese (unspecified variant for backward compatibility; please select ZH-HANS or ZH-HANT instead)
            "zh-CN", // zh-hans --------------- Chinese (simplified)
            "zh-TW", // zh-hant --------------- Chinese (traditional)
        )
    }
}