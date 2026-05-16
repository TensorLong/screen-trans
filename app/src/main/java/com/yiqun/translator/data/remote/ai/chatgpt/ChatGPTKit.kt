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
        return configuredModel(
            model = ApiKeyInfo.getApiModelChatgpt(context),
            baseUrl = ApiKeyInfo.getApiBaseUrlChatgpt(context),
        )
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
        pointedTokenOffset: Int? = null,
        sourceLanguageCode: String,
        targetLanguageCode: String,
    ): SenseGroup? {
        if (word.isBlank() || sentence.isBlank()) return null
        if (!sentence.contains(word)) {
            Timber.tag(TAG).w("senseGroupAt: word [$word] not in sentence [$sentence]")
            return null
        }
        val endpointUrl = resolveEndpointUrl(CHAT_COMPLETIONS_PATH)
        val model = resolveModel()
        SenseGroupRequestCache.get(
            model = model,
            endpointUrl = endpointUrl,
            word = word,
            sentence = sentence,
            pointedTokenOffset = pointedTokenOffset,
            sourceLanguageCode = sourceLanguageCode,
            targetLanguageCode = targetLanguageCode,
        )?.let { cached ->
            Timber.tag(TAG).d("senseGroupAt() request cache hit word=[$word] chunk=[${cached.text}]")
            return cached
        }

        val systemMessage = mapOf(
            "role" to "system",
            "content" to SenseGroupChunkPolicy.SYSTEM_PROMPT
        )

        val userPayload = JSONObject().apply {
            put("word", word)
            put("sentence", sentence)
            if (pointedTokenOffset != null) {
                put("pointed_word_start", pointedTokenOffset)
                put("pointed_word_end", pointedTokenOffset + word.length)
            }
            put("source_language", sourceLanguageCode)
        }.toString()

        val userMessage = mapOf(
            "role" to "user",
            "content" to userPayload
        )

        val requestBody = mapOf(
            "model" to model,
            "messages" to listOf(systemMessage, userMessage),
            "max_tokens" to 80,
            "response_format" to mapOf("type" to "json_object"),
            "temperature" to 0.0,
        )
        Timber.tag(TAG).d("senseGroupAt() word=[$word] sentence=[$sentence]")

        val jsonRequestBody = Gson().toJson(requestBody)
            .toRequestBody("application/json".toMediaType())

        val response: ChatGPTResponse = try {
            chatGPTService.send(
                url = endpointUrl,
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
            val rawChunkText = json.optString("chunk", "").trim()
            val chunkText = SenseGroupChunkPolicy.refineChunk(
                sentence = sentence,
                modelChunk = rawChunkText,
                word = word,
                pointedTokenOffset = pointedTokenOffset,
            ).orEmpty()
            val translation = json.optString("translation", "").trim()
                .takeIf { rawChunkText == chunkText }
                .orEmpty()
            if (chunkText.isEmpty()) {
                Timber.tag(TAG).w("senseGroupAt() empty chunk in response: $raw")
                return null
            }
            val range = chunkCharRange(sentence, chunkText, pointedTokenOffset)
            if (range == null) {
                Timber.tag(TAG).w("senseGroupAt() chunk [$chunkText] not found in sentence [$sentence]")
                return null
            }
            if (pointedTokenOffset != null && !range.containsExclusive(pointedTokenOffset)) {
                Timber.tag(TAG).w("senseGroupAt() chunk [$chunkText] does not contain pointed offset [$pointedTokenOffset]")
                return null
            }
            if (!chunkText.contains(word)) {
                Timber.tag(TAG).w("senseGroupAt() chunk [$chunkText] does not contain word [$word]")
                return null
            }
            Timber.tag(TAG).d("senseGroupAt() OK word=[$word] chunk=[$chunkText] tr=[$translation] range=${range.first}..${range.last}")
            SenseGroup(chunkText, translation, range).also { senseGroup ->
                SenseGroupRequestCache.put(
                    model = model,
                    endpointUrl = endpointUrl,
                    word = word,
                    sentence = sentence,
                    pointedTokenOffset = pointedTokenOffset,
                    sourceLanguageCode = sourceLanguageCode,
                    targetLanguageCode = targetLanguageCode,
                    senseGroup = senseGroup,
                )
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "senseGroupAt() parse failed: $raw")
            null
        }
    }

    companion object {
        const val BASE_URL = "https://api.example.com/"
        const val DEFAULT_MODEL = "gpt-4o-mini"
        const val OPENROUTER_DEFAULT_MODEL = "openai/gpt-4o-mini"
        const val CHAT_COMPLETIONS_PATH = "v1/chat/completions"
        const val MODELS_PATH = "v1/models"

        fun endpointUrl(baseUrl: String?, path: String): String {
            val base = baseUrl?.trim()?.takeIf { it.isNotEmpty() } ?: BASE_URL
            val trimmedBase = base.trimEnd('/')
            val trimmedPath = path.trimStart('/')
            val normalizedPath = if (trimmedBase.endsWith("/v1") && trimmedPath.startsWith("v1/")) {
                trimmedPath.removePrefix("v1/")
            } else {
                trimmedPath
            }
            return "$trimmedBase/$normalizedPath"
        }

        fun configuredModel(model: String?, baseUrl: String? = null): String {
            val trimmed = model?.trim()?.takeIf { it.isNotEmpty() }
            if (isOpenRouterBaseUrl(baseUrl) && (trimmed == null || trimmed == DEFAULT_MODEL)) {
                return OPENROUTER_DEFAULT_MODEL
            }
            return trimmed ?: DEFAULT_MODEL
        }

        fun modelIds(response: ModelListResponse): List<String> {
            return response.data
                .map { it.id.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .sorted()
        }

        fun chunkCharRange(
            sentence: String,
            chunkText: String,
            pointedTokenOffset: Int? = null,
        ): IntRange? {
            val chunk = chunkText.takeIf { it.isNotEmpty() } ?: return null
            val occurrences = mutableListOf<IntRange>()
            var searchFrom = 0
            while (searchFrom <= sentence.length) {
                val start = sentence.indexOf(chunk, startIndex = searchFrom)
                if (start < 0) break
                val end = start + chunk.length
                occurrences.add(start..end)
                searchFrom = (start + 1).coerceAtMost(sentence.length + 1)
            }
            if (occurrences.isEmpty()) return null
            if (pointedTokenOffset == null) return occurrences.first()

            return occurrences.firstOrNull { it.containsExclusive(pointedTokenOffset) }
                ?: occurrences.minByOrNull { distanceToExclusiveRange(pointedTokenOffset, it) }
        }

        private fun isOpenRouterBaseUrl(baseUrl: String?): Boolean {
            val normalized = baseUrl?.trim()?.lowercase().orEmpty()
            return normalized.startsWith("https://openrouter.ai/") ||
                    normalized == "https://openrouter.ai" ||
                    normalized.startsWith("http://openrouter.ai/") ||
                    normalized == "http://openrouter.ai"
        }

        private fun IntRange.containsExclusive(offset: Int): Boolean {
            return offset >= first && offset < last
        }

        private fun distanceToExclusiveRange(offset: Int, range: IntRange): Int {
            return when {
                offset < range.first -> range.first - offset
                offset >= range.last -> offset - range.last + 1
                else -> 0
            }
        }
    }
}
