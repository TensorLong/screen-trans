package com.galaxy.airviewdictionary.data.remote.translation.yandex

import android.content.Context
import com.galaxy.airviewdictionary.data.local.secure.ApiKeyInfo
import com.galaxy.airviewdictionary.data.remote.translation.TranslationKit
import com.galaxy.airviewdictionary.di.YandexRetrofit
import com.galaxy.airviewdictionary.data.remote.translation.Language
import com.galaxy.airviewdictionary.data.remote.translation.Transaction
import com.galaxy.airviewdictionary.data.remote.translation.TranslationResponse
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class YandexKit @Inject constructor(@ApplicationContext val context: Context, @YandexRetrofit private val yandexService: YandexService) : TranslationKit() {

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
        supportedSourceLanguageCodes.map {
            Language(it).apply {
//                supportKitTypes.add(TranslationKitType.YANDEX)
            }
        }
    }

    override val supportedLanguagesAsTarget: List<Language> by lazy {
        supportedTargetLanguageCodes.map {
            Language(it).apply {
//            supportKitTypes.add(TranslationKitType.YANDEX)
            }
        }
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

    private fun createRequestBody(sourceLanguageCode: String, targetLanguageCode: String, texts: List<String>): RequestBody {
        val jsonBody = JSONObject().apply {
            put("sourceLanguageCode", sourceLanguageCode)
            put("targetLanguageCode", targetLanguageCode)
            put("texts", texts)
            put("folderId", FOLDER_ID)
        }

        return jsonBody.toString().toRequestBody("application/json".toMediaTypeOrNull())
    }

    override suspend fun request(
        sourceLanguageCode: String,
        targetLanguageCode: String,
        sourceText: String
    ): TranslationResponse {
        val requestBody = createRequestBody(sourceLanguageCode, targetLanguageCode, listOf(sourceText))

        return try {
            if (!available()) {
                throw IllegalStateException("api key might not have been initialized.")
            }
            val responseBody = yandexService.send(
                bearerToken = "Api-Key ${ApiKeyInfo.getApiKeyYandex(context) ?: "unknown_key"}",
                body = requestBody
            )

            Timber.tag(TAG).i("----------------responseBody.string() ----------- ${responseBody.string()}")

            val gson = Gson()
            val response = gson.fromJson(responseBody.string(), YandexTranslationResponse::class.java)
            val resultText = response.translations.firstOrNull()?.text ?: throw IllegalStateException("No translation found")

            TranslationResponse.Success(
                Transaction(
                    sourceLanguageCode = sourceLanguageCode,
                    targetLanguageCode = targetLanguageCode,
                    sourceText = sourceText,
//                    translationKitType = TranslationKitType.YANDEX,
                    detectedLanguageCode = sourceLanguageCode,
                    resultText = resultText
                )
            )
        } catch (e: Exception) {  // 모든 예외를 포착하여 처리
            e.printStackTrace()
            TranslationResponse.Error(e)
        }
    }

    companion object {
        const val BASE_URL = "https://translate.api.cloud.yandex.net/"

        const val FOLDER_ID = "b1g9gngt8nttve8toohl" // default folder id
//        const val FOLDER_ID = "aje6om764u8naib41q21" // 서비스 계정 아이디
//        const val FOLDER_ID = "aje66uqg3d2a472k3s6v" // api key id

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
            "kmr", // Kurdish
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
            "nya", // Nyanja
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
            "zh-Hans", // zh-hans --------------- Chinese (Simplified)
            "zh-Hant", // zh-hant --------------- Chinese (Traditional)
            "zu", // Zulu

        )
    }
}

data class YandexTranslationResponse(
    @SerializedName("translations")
    val translations: List<YandexTranslation>
)

data class YandexTranslation(
    @SerializedName("text")
    val text: String
)

fun fetchSupportedLanguages(folderId: String, iamToken: String) {
    CoroutineScope(Dispatchers.IO).launch {
        val client = OkHttpClient()

        val jsonBody = JSONObject()
        jsonBody.put("folderId", folderId)

        val requestBody = RequestBody.create("application/json".toMediaType(), jsonBody.toString())

        val request = Request.Builder()
            .url("https://translate.api.cloud.yandex.net/translate/v2/languages")
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Api-Key $iamToken")
            .build()

        client.newCall(request).execute().use { response ->
//            if (!response.isSuccessful) {
//                throw Exception("Unexpected response code: ${response.code}, message: ${response.message}")
//            }
            Timber.tag("YandexKit").i("--------------------------- ${response.body?.string()}")
        }
    }
}