package com.yiqun.translator.data.remote.ai.chatgpt

import android.content.Context
import com.yiqun.translator.data.local.secure.ApiKeyInfo
import com.yiqun.translator.di.ChatGPTRetrofit
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import retrofit2.HttpException
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class ChatGPTKit @Inject constructor(@ApplicationContext val context: Context, @ChatGPTRetrofit private val chatGPTService: ChatGPTService) {

    private val TAG: String = javaClass.simpleName

    fun available(): Boolean {
        return ApiKeyInfo.chatgptKeyAvailable(context)
    }

    private fun resolveEndpointUrl(path: String): String {
        return endpointUrl(ApiKeyInfo.getApiBaseUrlChatgpt(context), path)
    }

    private fun resolveModel(): String {
        return configuredModel(ApiKeyInfo.getApiModelChatgpt(context))
    }

    private fun authorizationHeader(): String {
        return "Bearer ${ApiKeyInfo.getApiKeyChatgpt(context) ?: "unknown_key"}"
    }

    private fun logApiFailure(where: String, e: Throwable) {
        if (e is HttpException) {
            val body = try { e.response()?.errorBody()?.string() } catch (_: Throwable) { null }
            Timber.tag(TAG).w(e, "%s HTTP %d body=%s", where, e.code(), body ?: "<no body>")
        } else {
            Timber.tag(TAG).w(e, "%s failed", where)
        }
    }

    suspend fun fetchModels(): List<String> {
        val response = chatGPTService.models(
            url = resolveEndpointUrl(MODELS_PATH),
            apiKey = authorizationHeader(),
        )
        return modelIds(response)
    }

    suspend fun testConfiguredModel(): String {
        val systemMessage = mapOf(
            "role" to "system",
            "content" to "Reply with exactly: OK"
        )
        val userMessage = mapOf(
            "role" to "user",
            "content" to "Connection test"
        )
        val requestBody = mapOf(
            "model" to resolveModel(),
            "messages" to listOf(systemMessage, userMessage),
            "max_tokens" to 8,
            "temperature" to 0.0,
        )
        val jsonRequestBody = Gson().toJson(requestBody)
            .toRequestBody("application/json".toMediaType())

        val response: ChatGPTResponse = chatGPTService.send(
            url = resolveEndpointUrl(CHAT_COMPLETIONS_PATH),
            apiKey = authorizationHeader(),
            body = jsonRequestBody
        )
        return response.choices.firstOrNull()?.message?.content?.trim().orEmpty()
    }

    suspend fun senseGroupAt(
        word: String,
        sentence: String,
        sourceLanguageCode: String,
        targetLanguageCode: String,
    ): SenseGroup? {
        if (word.isBlank() || sentence.isBlank()) return null
        if (!sentence.contains(word)) {
            Timber.tag(TAG).w("senseGroupAt: word [$word] not in sentence [$sentence]")
            return null
        }

        val systemMessage = mapOf(
            "role" to "system",
            "content" to (
                "You are a linguist. The user is reading a sentence and is pointing " +
                "at a specific word inside it. Your job is to identify the SENSE GROUP " +
                "(also known as a semantic chunk, thought unit, or 意群) that contains " +
                "that pointed word, and translate JUST that chunk into the target language. " +
                "\n\n" +
                "A sense group is a phrase-level unit smaller than the full sentence: " +
                "typically a noun phrase, a verb phrase, a prepositional phrase, or a " +
                "subordinate clause. It is the minimal contiguous span of words that " +
                "carries a self-contained meaning and gives the pointed word its " +
                "in-context interpretation." +
                "\n\n" +
                "Rules (all MUST be followed):" +
                "\n - The chunk MUST contain the pointed word." +
                "\n - The chunk MUST appear VERBATIM in the sentence — copy the substring " +
                "exactly, do not paraphrase, do not normalize punctuation, do not change " +
                "case." +
                "\n - Prefer the SMALLEST meaningful chunk. Do NOT return the entire " +
                "sentence unless the sentence is itself a single short phrase." +
                "\n - Translate ONLY the chunk, not the surrounding sentence." +
                "\n\n" +
                "Respond ONLY with valid JSON of the form:" +
                "\n{\"chunk\":\"<verbatim span from the sentence>\",\"translation\":\"<translation in target language>\"}"
            )
        )

        val userPayload = JSONObject().apply {
            put("word", word)
            put("sentence", sentence)
            put("source_language", sourceLanguageCode)
            put("target_language", targetLanguageCode)
        }.toString()

        val userMessage = mapOf(
            "role" to "user",
            "content" to userPayload
        )

        val requestBody = mapOf(
            "model" to resolveModel(),
            "messages" to listOf(systemMessage, userMessage),
            "max_tokens" to 400,
            "response_format" to mapOf("type" to "json_object"),
            "temperature" to 0.0,
        )
        Timber.tag(TAG).d("senseGroupAt() word=[$word] sentence=[$sentence]")

        val jsonRequestBody = Gson().toJson(requestBody)
            .toRequestBody("application/json".toMediaType())

        val response: ChatGPTResponse = try {
            chatGPTService.send(
                url = resolveEndpointUrl(CHAT_COMPLETIONS_PATH),
                apiKey = authorizationHeader(),
                body = jsonRequestBody
            )
        } catch (e: Exception) {
            logApiFailure("senseGroupAt()", e)
            return null
        }

        val raw = response.choices.firstOrNull()?.message?.content
        if (raw.isNullOrBlank()) {
            Timber.tag(TAG).w("senseGroupAt() empty response")
            return null
        }

        return try {
            val json = JSONObject(raw)
            val chunkText = json.optString("chunk", "").trim()
            val translation = json.optString("translation", "").trim()
            if (chunkText.isEmpty()) {
                Timber.tag(TAG).w("senseGroupAt() empty chunk in response: $raw")
                return null
            }
            val start = sentence.indexOf(chunkText)
            if (start < 0) {
                Timber.tag(TAG).w("senseGroupAt() chunk [$chunkText] not found in sentence [$sentence]")
                return null
            }
            if (!chunkText.contains(word)) {
                Timber.tag(TAG).w("senseGroupAt() chunk [$chunkText] does not contain word [$word]")
                return null
            }
            val end = start + chunkText.length
            Timber.tag(TAG).d("senseGroupAt() OK word=[$word] chunk=[$chunkText] tr=[$translation] range=$start..$end")
            SenseGroup(chunkText, translation, start..end)
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "senseGroupAt() parse failed: $raw")
            null
        }
    }

    companion object {
        const val BASE_URL = "https://api.example.com/"
        const val DEFAULT_MODEL = "gpt-4o-mini"
        const val CHAT_COMPLETIONS_PATH = "v1/chat/completions"
        const val MODELS_PATH = "v1/models"

        fun endpointUrl(baseUrl: String?, path: String): String {
            val base = baseUrl?.trim()?.takeIf { it.isNotEmpty() } ?: BASE_URL
            return base.trimEnd('/') + "/" + path.trimStart('/')
        }

        fun configuredModel(model: String?): String {
            return model?.trim()?.takeIf { it.isNotEmpty() } ?: DEFAULT_MODEL
        }

        fun modelIds(response: ModelListResponse): List<String> {
            return response.data
                .map { it.id.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .sorted()
        }
    }
}