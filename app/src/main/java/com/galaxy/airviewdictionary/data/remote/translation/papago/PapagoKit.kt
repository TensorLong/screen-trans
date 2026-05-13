package com.galaxy.airviewdictionary.data.remote.translation.papago

import android.content.Context
import com.galaxy.airviewdictionary.data.local.secure.ApiKeyInfo
import com.galaxy.airviewdictionary.data.remote.translation.Language
import com.galaxy.airviewdictionary.data.remote.translation.Transaction
import com.galaxy.airviewdictionary.data.remote.translation.TranslationKit
import com.galaxy.airviewdictionary.data.remote.translation.TranslationKitType
import com.galaxy.airviewdictionary.data.remote.translation.TranslationResponse
import com.galaxy.airviewdictionary.di.PapagoRetrofit
import com.google.gson.JsonParser
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.FormBody
import okhttp3.RequestBody
import timber.log.Timber
import java.net.URLDecoder
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class PapagoKit @Inject constructor(@ApplicationContext val context: Context, @PapagoRetrofit private val papagoService: PapagoService) : TranslationKit() {

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
        supportedSourceLanguageCodes.map { Language(it).apply { supportKitTypes.add(TranslationKitType.PAPAGO) } }
    }

    override val supportedLanguagesAsTarget: List<Language> by lazy {
        supportedTargetLanguageCodes.map { Language(it).apply { supportKitTypes.add(TranslationKitType.PAPAGO) } }
    }

    override fun isSupportedAsSource(code: String, targetLanguageCode: String): Boolean {
        if (code == targetLanguageCode && supportedLanguageCodes.contains(code)) {
            return true
        }

        return when {
            (code == "auto" || code == "ko" || code == "en") -> supportedLanguageCodes.any { it.equals(targetLanguageCode, ignoreCase = true) }
            (targetLanguageCode == "ko" || targetLanguageCode == "en") -> supportedLanguageCodes.any { it.equals(code, ignoreCase = true) }

            (code == "ja") -> supportedJaLanguageCodes.any { it.equals(targetLanguageCode, ignoreCase = true) }
            (targetLanguageCode == "ja") -> supportedJaLanguageCodes.any { it.equals(code, ignoreCase = true) }

            (code == "zh-CN") -> supportedZhCnLanguageCodes.any { it.equals(targetLanguageCode, ignoreCase = true) }
            (targetLanguageCode == "zh-CN") -> supportedZhCnLanguageCodes.any { it.equals(code, ignoreCase = true) }

            else -> false
        }
    }

    override fun isSupportedAsTarget(code: String, sourceLanguageCode: String): Boolean {
        if (code == sourceLanguageCode && supportedLanguageCodes.contains(code)) {
            return true
        }

        return when {
            (code == "ko" || code == "en") -> supportedLanguageCodes.any { it.equals(sourceLanguageCode, ignoreCase = true) }
            (sourceLanguageCode == "ko" || sourceLanguageCode == "en") -> supportedLanguageCodes.any { it.equals(code, ignoreCase = true) }

            (code == "ja") -> supportedJaLanguageCodes.any { it.equals(sourceLanguageCode, ignoreCase = true) }
            (sourceLanguageCode == "ja") -> supportedJaLanguageCodes.any { it.equals(code, ignoreCase = true) }

            (code == "zh-CN") -> supportedZhCnLanguageCodes.any { it.equals(sourceLanguageCode, ignoreCase = true) }
            (sourceLanguageCode == "zh-CN") -> supportedZhCnLanguageCodes.any { it.equals(code, ignoreCase = true) }

            else -> false
        }
    }

    override fun isLanguageSwappable(sourceLanguageCode: String, targetLanguageCode: String): Boolean {
        return isSupportedAsSource(targetLanguageCode, sourceLanguageCode) && isSupportedAsTarget(sourceLanguageCode, targetLanguageCode)
    }

    override suspend fun request(
        sourceLanguageCode: String,
        targetLanguageCode: String,
        sourceText: String
    ): TranslationResponse {
        Timber.tag(TAG).d("request sourceLanguageCode $sourceLanguageCode targetLanguageCode $targetLanguageCode sourceText $sourceText")
        return try {
            if (!available()) {
                throw IllegalStateException("API key might not have been initialized.")
            }

            // Encode text for URL safety
            val encodedText = URLEncoder.encode(sourceText, "UTF-8")

            // Build request body
            val requestBody: RequestBody = FormBody.Builder()
                .add("source", sourceLanguageCode)
                .add("target", targetLanguageCode)
                .add("text", encodedText)
                .build()

            val credentials = ApiKeyInfo.getApiKeyPapago(context)?.split("|")
                ?.takeIf { it.size == 2 }
                ?.let { it[0].toCharArray() to it[1].toCharArray() }
                ?: ("unknown_id".toCharArray() to "unknown_secret".toCharArray())

            Timber.tag(TAG).d("getApiKeyPapago [${ApiKeyInfo.getApiKeyPapago(context)}]")
            Timber.tag(TAG).d("clientId ${credentials.first.joinToString("")} clientSecret [${credentials.second.joinToString("")}]")

            // Make API request
            val response = papagoService.send(
                clientId = credentials.first.joinToString(""),
                clientSecret = credentials.second.joinToString(""),
                body = requestBody
            )

            credentials.first.fill('\u0000')  // Clear clientId characters
            credentials.second.fill('\u0000') // Clear clientSecret characters


            val responseString = response.string()
            Timber.tag(TAG).d("sourceLanguageCode $sourceLanguageCode responseString  [${responseString}]")
            val jsonResponse = JsonParser.parseString(responseString).asJsonObject
            val translatedTextEncoded = jsonResponse["message"]
                .asJsonObject["result"]
                .asJsonObject["translatedText"]
                .asString
            Timber.tag(TAG).d("translatedTextEncoded $translatedTextEncoded")
            val resultText = URLDecoder.decode(translatedTextEncoded, "UTF-8")
            Timber.tag(TAG).d("resultText $resultText")

            TranslationResponse.Success(
                Transaction(
                    sourceLanguageCode = sourceLanguageCode,
                    targetLanguageCode = targetLanguageCode,
                    sourceText = sourceText,
                    translationKitType = TranslationKitType.PAPAGO,
                    detectedLanguageCode = sourceLanguageCode,
                    resultText = resultText
                )
            )
        } catch (e: Exception) {
            Timber.tag(TAG).e("e $e")
            TranslationResponse.Error(e)
        }
    }

    companion object {
        const val BASE_URL = "https://papago.apigw.ntruss.com/"

        val supportedLanguageCodes = arrayOf(
            "de", // German
            "en", // English
            "es", // Spanish
            "fr", // French
            "id", // Indonesian
            "it", // Italian
            "ja", // Japanese
            "ko", // Korean
            "ru", // Russian
            "th", // Thai
            "vi", // Vietnamese
            "zh-CN", // Chinese (Simplified)
            "zh-TW", // Chinese (Traditional)
        )

        val supportedJaLanguageCodes = arrayOf(
            "en", // English
            "fr", // French
            "id", // Indonesian
            "ja", // Japanese
            "ko", // Korean
            "th", // Thai
            "vi", // Vietnamese
            "zh-CN", // Chinese (Simplified)
            "zh-TW", // Chinese (Traditional)
        )

        val supportedZhCnLanguageCodes = arrayOf(
            "en", // English
            "ja", // Japanese
            "ko", // Korean
            "zh-CN", // Chinese (Simplified)
            "zh-TW", // Chinese (Traditional)
        )
    }
}

