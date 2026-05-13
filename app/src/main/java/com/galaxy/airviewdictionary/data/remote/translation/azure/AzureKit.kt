package com.galaxy.airviewdictionary.data.remote.translation.azure

import android.content.Context
import com.galaxy.airviewdictionary.data.local.secure.ApiKeyInfo
import com.galaxy.airviewdictionary.data.remote.translation.TranslationKit
import com.galaxy.airviewdictionary.data.remote.translation.TranslationKitType
import com.galaxy.airviewdictionary.di.AzureRetrofit
import com.galaxy.airviewdictionary.data.remote.translation.Language
import com.galaxy.airviewdictionary.data.remote.translation.Transaction
import com.galaxy.airviewdictionary.data.remote.translation.TranslationResponse
import com.google.gson.Gson
import com.google.gson.JsonParser
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class AzureKit @Inject constructor(@ApplicationContext val context: Context, @AzureRetrofit private val azureService: AzureService) : TranslationKit() {

    override fun available(): Boolean {
        return ApiKeyInfo.apiKeyAvailable(context)
    }

    private val supportedSourceLanguageCodes: List<String> by lazy {
        mutableListOf(*supportedLanguageCodes)
            .apply { add(0, "auto") }
            .toList()
    }

    private val supportedTargetLanguageCodes: List<String> by lazy {
        supportedLanguageCodes.toList()
    }

    override val supportedLanguagesAsSource: List<Language> by lazy {
        supportedSourceLanguageCodes.map { Language(it).apply { supportKitTypes.add(TranslationKitType.AZURE) } }
    }

    override val supportedLanguagesAsTarget: List<Language> by lazy {
        supportedTargetLanguageCodes.map { Language(it).apply { supportKitTypes.add(TranslationKitType.AZURE) } }
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

    data class AzureText(val Text: String)

    override suspend fun request(
        sourceLanguageCode: String,
        targetLanguageCode: String,
        sourceText: String
    ): TranslationResponse {
        val gson = Gson()
        val requestList = listOf(AzureText(sourceText))
        val json = gson.toJson(requestList)
        val requestBody = json.toRequestBody("application/json".toMediaType())

        Timber.tag(TAG).d("===== [$sourceText] [$json] [$requestBody]")

        return try {
            if (!available()) {
                throw IllegalStateException("api key might not have been initialized.")
            }
            val responseBody = azureService.send(
                translatorKey = ApiKeyInfo.getApiKeyAzure(context) ?: "unknown_key",  // Azure 서비스 키
                resourceLocation = API_LOCATION,  // Azure 서비스 리소스
                from = if (sourceLanguageCode == "auto") null else getOwnLanguageCode(sourceLanguageCode),
                to = getOwnLanguageCode(targetLanguageCode),
                body = requestBody
            )
            val responseString = responseBody.string()
            val jsonResponse = JsonParser.parseString(responseString).asJsonArray
            val translations = jsonResponse[0].asJsonObject
                .getAsJsonArray("translations")
            val resultText = translations[0].asJsonObject.get("text").asString

            val detectedLanguageCode = if (sourceLanguageCode == "auto") {
                jsonResponse[0].asJsonObject
                    .getAsJsonObject("detectedLanguage")
                    .get("language").asString
            } else {
                sourceLanguageCode
            }

            TranslationResponse.Success(
                Transaction(
                    sourceLanguageCode = detectedLanguageCode,
                    targetLanguageCode = targetLanguageCode,
                    sourceText = sourceText,
                    translationKitType = TranslationKitType.AZURE,
                    detectedLanguageCode = detectedLanguageCode,
                    resultText = resultText
                )
            )
        } catch (e: Exception) {  // 모든 예외를 포착하여 처리
            TranslationResponse.Error(e)
        }
    }

    private fun getOwnLanguageCode(languageCode: String): String {
        return when (languageCode) {
            "ku" -> "kmr"
            "ny" -> "nya"
            "zh-CN" -> "zh-hans"
            "zh-TW" -> "zh-Hant"
            else -> languageCode
        }
    }

    companion object {
        const val BASE_URL = "https://api.cognitive.microsofttranslator.com/"
        const val API_LOCATION = "southeastasia"

        val supportedLanguageCodes = arrayOf(
            "af", // Afrikaans
            "am", // Amharic
            "ar", // Arabic
            "as", // Assamese
            "az", // Azerbaijani
            "ba", // Bashkir
            "bg", // Bulgarian
            "bho", // Bhojpuri
            "bn", // Bangla
            "bo", // Tibetan
            "brx", // Bodo
            "bs", // Bosnian
            "ca", // Catalan
            "cs", // Czech
            "cy", // Welsh
            "da", // Danish
            "de", // German
            "doi", // Dogri
            "dsb", // Lower Sorbian
            "dv", // Divehi
            "el", // Greek
            "en", // English
            "es", // Spanish
            "et", // Estonian
            "eu", // Basque
            "fa", // Persian
            "fi", // Finnish
            "fil", // Filipino
            "fj", // Fijian
            "fo", // Faroese
            "fr", // French
            "ga", // Irish
            "gl", // Galician
            "gom", // Goan Konkani
            "gu", // Gujarati
            "ha", // Hausa
            "he", // Hebrew
            "hi", // Hindi
            "hr", // Croatian
            "hsb", // Upper Sorbian
            "ht", // Haitian Creole
            "hu", // Hungarian
            "hy", // Armenian
            "id", // Indonesian
            "ig", // Igbo
            "ikt", // Western Canadia
            "is", // Icelandic
            "it", // Italian
            "iu", // Inuktitut
            "ja", // Japanese
            "ka", // Georgian
            "kk", // Kazakh
            "km", // Khmer
            "ku", // kmr --------------- Kurdish
            "kn", // Kannada
            "ko", // Korean
            "ks", // Kashmiri
            "ku", // Kurdish
            "ky", // Kyrgyz
            "ln", // Lingala
            "lo", // Lao
            "lt", // Lithuanian
            "lug", // Ganda
            "lv", // Latvian
            "lzh", // Literary Chines
            "mai", // Maithili
            "mg", // Malagasy
            "mi", // Māori
            "mk", // Macedonian
            "ml", // Malayalam
            "mni", // Manipuri
            "mr", // Marathi
            "ms", // Malay
            "mt", // Maltese
            "mww", // mww --------------- Mont Dao (Latin)
            "my", // Burmese
            "nb", // Norwegian Bokmål
            "ne", // Nepali
            "nl", // Dutch
            "nso", // Northern Sotho
            "ny", // nya --------------- Nyanja
            "or", // Odia
            "otq", // otq --------------- Otomi
            "pa", // Punjabi
            "pl", // Polish
            "prs", // Dari
            "ps", // Pashto
            "pt", // Portuguese
//            "pt-PT", // pt-pt --------------- Portuguese
            "ro", // Romanian
            "ru", // Russian
            "run", // Rundi
            "rw", // Kinyarwanda
            "sd", // Sindhi
            "si", // Sinhala
            "sk", // Slovak
            "sl", // Slovenian
            "sm", // Samoan
            "sn", // Shona
            "so", // Somali
            "sq", // Albanian
            "sr-Cyrl", // sr-cyrl --------------- Serbian (Cyrillic)
            "sr-Latn", // sr-latn --------------- Serbian (Latin)
            "st", // Southern Sotho
            "sv", // Swedish
            "sw", // Swahili
            "ta", // Tamil
            "te", // Telugu
            "th", // Thai
            "ti", // Tigrinya
            "tk", // Turkmen
            "tlh-Latn", // tlh-latn --------------- Klingon (Latin)
            "tlh-Piqd", // tlh-piqd --------------- Klingon
            "tn", // Tswana
            "to", // Tongan
            "tr", // Turkish
            "tt", // Tatar
            "ty", // Tahitian
            "ug", // Uyghur
            "uk", // Ukrainian
            "ur", // Urdu
            "uz", // Uzbek
            "vi", // Vietnamese
            "xh", // Xhosa
            "yo", // Yoruba
            "yua", // yua --------------- Yucatec Maya
            "yue", // Cantonese
            "zh-CN", // zh-hans --------------- Chinese (Simplified)
            "zh-TW", // zh-hant --------------- Chinese (Traditional)
            "zu", // Zulu

        )
    }
}
