package com.galaxy.airviewdictionary.data.remote.translation.goolge

import android.util.LruCache
import com.galaxy.airviewdictionary.data.remote.translation.TranslationKit
import com.galaxy.airviewdictionary.data.remote.translation.TranslationKitType
import com.galaxy.airviewdictionary.data.remote.translation.Language
import com.galaxy.airviewdictionary.data.remote.translation.Transaction
import com.galaxy.airviewdictionary.data.remote.translation.TranslationResponse
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton


/**
 *
 */
@Singleton
class GoogleMlKit @Inject constructor() : TranslationKit() {

    companion object {
        private const val NUM_TRANSLATORS = 3
    }

    private val modelManager: RemoteModelManager = RemoteModelManager.getInstance()

    private var pendingDownloads = HashMap<String, Task<Void>>()

    private lateinit var modelCodes: List<String>

    private var translators: LruCache<TranslatorOptions, Translator> =
        object : LruCache<TranslatorOptions, Translator>(NUM_TRANSLATORS) {
            override fun create(options: TranslatorOptions): Translator {
                val translator = Translation.getClient(options)
                return translator
            }

            override fun entryRemoved(
                evicted: Boolean,
                key: TranslatorOptions,
                oldValue: Translator,
                newValue: Translator?,
            ) {
                oldValue.close()
            }
        }

    override fun available(): Boolean {
        return true
    }

    fun available(sourceLanguageCode: String, targetLanguageCode: String): Boolean {
        return modelCodes.contains(sourceLanguageCode) && modelCodes.contains(targetLanguageCode)
    }

    fun downloadLanguage(languageCode: String) {
        if (modelCodes.contains(languageCode)) {
            return
        }
        val model: TranslateRemoteModel = TranslateRemoteModel.Builder(languageCode).build()
        var downloadTask: Task<Void>?
        if (pendingDownloads.containsKey(languageCode)) {
            downloadTask = pendingDownloads[languageCode]
            if (downloadTask != null && !downloadTask.isCanceled) {
                return
            }
        }

        val downloadModelConditions = DownloadConditions.Builder()
            .requireWifi()
            .build()

        downloadTask = modelManager
            .download(model, downloadModelConditions)
            .addOnCompleteListener {
                pendingDownloads.remove(languageCode)
                fetchDownloadedModels()
            }
        pendingDownloads[languageCode] = downloadTask
    }

    private fun fetchDownloadedModels() {
        modelManager
            .getDownloadedModels(TranslateRemoteModel::class.java)
            .addOnSuccessListener { remoteModels ->
                modelCodes = remoteModels.map { model ->
                    Timber.tag(TAG).d("availableModel ${model.language} ${Language(model.language).displayName}")
                    model.language
                }
            }
    }

    private fun Translator.downloadModelIfNeededAsync(): Task<Void> = downloadModelIfNeeded()

    private fun Translator.translateAsync(text: String): Task<String> = translate(text)

    override suspend fun request(
        sourceLanguageCode: String,
        targetLanguageCode: String,
        sourceText: String
    ): TranslationResponse {
        val sourceLangCode = TranslateLanguage.fromLanguageTag(sourceLanguageCode)!!
        val targetLangCode = TranslateLanguage.fromLanguageTag(targetLanguageCode)!!
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(sourceLangCode)
            .setTargetLanguage(targetLangCode)
            .build()

        val translator = translators[options]

        return try {
            translator.downloadModelIfNeededAsync().await()
            val translatedText = translator.translateAsync(sourceText).await()

            TranslationResponse.Success(
                Transaction(
                    sourceLanguageCode = sourceLanguageCode,
                    targetLanguageCode = targetLanguageCode,
                    sourceText = sourceText,
                    translationKitType = TranslationKitType.GOOGLE,
                    detectedLanguageCode = sourceLangCode,
                    resultText = translatedText
                )
            )
        } catch (e: Exception) {
            TranslationResponse.Error(e)
        }
    }

    private val availableLanguages: List<Language> = TranslateLanguage.getAllLanguages().map { Language(it).apply { supportKitTypes.add(TranslationKitType.GOOGLE) } }

    override val supportedLanguagesAsSource: List<Language> = availableLanguages

    override val supportedLanguagesAsTarget: List<Language> = availableLanguages

    override fun isSupportedAsSource(code: String, targetLanguageCode: String): Boolean {
        return TranslateLanguage.fromLanguageTag(code) != null && TranslateLanguage.fromLanguageTag(targetLanguageCode) != null
    }

    override fun isSupportedAsTarget(code: String, sourceLanguageCode: String): Boolean {
        return TranslateLanguage.fromLanguageTag(code) != null && TranslateLanguage.fromLanguageTag(sourceLanguageCode) != null
    }

    override fun isLanguageSwappable(sourceLanguageCode: String, targetLanguageCode: String): Boolean {
        return isSupportedAsSource(targetLanguageCode, sourceLanguageCode) && isSupportedAsTarget(sourceLanguageCode, targetLanguageCode)
    }

    init {
        fetchDownloadedModels()
    }

    fun close() {
        translators.evictAll()
    }
}
