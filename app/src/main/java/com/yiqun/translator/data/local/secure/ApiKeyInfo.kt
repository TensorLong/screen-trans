package com.yiqun.translator.data.local.secure

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import java.security.MessageDigest


/**
 */
object ApiKeyInfo {

    fun getApiKeyAzure(context: Context): String? {
        return SecureStore.get(context, SecureStoreKey.API_KEY_AZURE)?.get()
    }

    fun setApiKeyAzure(context: Context, apiKeyAzure: String) {
        SecureStore.set(context, SecureStoreKey.API_KEY_AZURE, apiKeyAzure)
    }

    fun getApiKeyVersionAzure(context: Context): Int? {
        return SecureStore.get(context, SecureStoreKey.API_KEY_VERSION_AZURE)?.get()?.toInt()
    }

    fun setApiKeyVersionAzure(context: Context, apiKeyVersionAzure: Int) {
        SecureStore.set(context, SecureStoreKey.API_KEY_VERSION_AZURE, apiKeyVersionAzure.toString())
    }

    fun getApiKeyDeepl(context: Context): String? {
        return SecureStore.get(context, SecureStoreKey.API_KEY_DEEPL)?.get()
    }

    fun setApiKeyDeepl(context: Context, apiKeyDeepl: String) {
        SecureStore.set(context, SecureStoreKey.API_KEY_DEEPL, apiKeyDeepl)
    }

    fun getApiKeyVersionDeepl(context: Context): Int? {
        return SecureStore.get(context, SecureStoreKey.API_KEY_VERSION_DEEPL)?.get()?.toInt()
    }

    fun setApiKeyVersionDeepl(context: Context, apiKeyVersionDeepl: Int) {
        SecureStore.set(context, SecureStoreKey.API_KEY_VERSION_DEEPL, apiKeyVersionDeepl.toString())
    }

    fun getApiKeyPapago(context: Context): String? {
        return SecureStore.get(context, SecureStoreKey.API_KEY_PAPAGO)?.get()
    }

    fun setApiKeyPapago(context: Context, apiKeyPapago: String) {
        SecureStore.set(context, SecureStoreKey.API_KEY_PAPAGO, apiKeyPapago)
    }

    fun getApiKeyVersionPapago(context: Context): Int? {
        return SecureStore.get(context, SecureStoreKey.API_KEY_VERSION_PAPAGO)?.get()?.toInt()
    }

    fun setApiKeyVersionPapago(context: Context, apiKeyVersionPapago: Int) {
        SecureStore.set(context, SecureStoreKey.API_KEY_VERSION_PAPAGO, apiKeyVersionPapago.toString())
    }

    fun getApiKeyYandex(context: Context): String? {
        return SecureStore.get(context, SecureStoreKey.API_KEY_YANDEX)?.get()
    }

    fun setApiKeyYandex(context: Context, apiKeyYandex: String) {
        SecureStore.set(context, SecureStoreKey.API_KEY_YANDEX, apiKeyYandex)
    }

    fun getApiKeyVersionYandex(context: Context): Int? {
        return SecureStore.get(context, SecureStoreKey.API_KEY_VERSION_YANDEX)?.get()?.toInt()
    }

    fun setApiKeyVersionYandex(context: Context, apiKeyVersionYandex: Int) {
        SecureStore.set(context, SecureStoreKey.API_KEY_VERSION_YANDEX, apiKeyVersionYandex.toString())
    }

    fun getApiKeyChatgpt(context: Context): String? {
        return SecureStore.get(context, SecureStoreKey.API_KEY_CHATGPT)?.get()
    }

    fun setApiKeyChatgpt(context: Context, apiKeyChatgpt: String) {
        SecureStore.set(context, SecureStoreKey.API_KEY_CHATGPT, apiKeyChatgpt)
    }

    fun getApiKeyVersionChatgpt(context: Context): Int? {
        return SecureStore.get(context, SecureStoreKey.API_KEY_VERSION_CHATGPT)?.get()?.toInt()
    }

    fun setApiKeyVersionChatgpt(context: Context, apiKeyVersionChatgpt: Int) {
        SecureStore.set(context, SecureStoreKey.API_KEY_VERSION_CHATGPT, apiKeyVersionChatgpt.toString())
    }

    fun getApiBaseUrlChatgpt(context: Context): String? {
        return SecureStore.get(context, SecureStoreKey.API_BASE_URL_CHATGPT)?.get()
    }

    fun setApiBaseUrlChatgpt(context: Context, baseUrl: String) {
        SecureStore.set(context, SecureStoreKey.API_BASE_URL_CHATGPT, baseUrl)
    }

    fun getApiModelChatgpt(context: Context): String? {
        return SecureStore.get(context, SecureStoreKey.API_MODEL_CHATGPT)?.get()
    }

    fun setApiModelChatgpt(context: Context, model: String) {
        SecureStore.set(context, SecureStoreKey.API_MODEL_CHATGPT, model)
    }


    fun apiKeyAvailable(context: Context): Boolean {
        return getApiKeyAzure(context) != null && getApiKeyAzure(context)!!.isNotEmpty()
                && getApiKeyVersionAzure(context) != null
                && getApiKeyDeepl(context) != null && getApiKeyDeepl(context)!!.isNotEmpty()
                && getApiKeyVersionDeepl(context) != null
                && getApiKeyPapago(context) != null && getApiKeyPapago(context)!!.isNotEmpty()
                && getApiKeyVersionPapago(context) != null
                && getApiKeyYandex(context) != null && getApiKeyYandex(context)!!.isNotEmpty()
                && getApiKeyVersionYandex(context) != null
                && getApiKeyChatgpt(context) != null && getApiKeyChatgpt(context)!!.isNotEmpty()
                && getApiKeyVersionChatgpt(context) != null
    }

    fun chatgptKeyAvailable(context: Context): Boolean {
        return !getApiKeyChatgpt(context).isNullOrBlank() && !getApiBaseUrlChatgpt(context).isNullOrBlank()
    }

    /**
     * Returns the previously-cached ChatGPT model list IF the stored fingerprint
     * (SHA-256 hash of "baseUrl|apiKey", first 16 hex chars) still matches the
     * current (baseUrl, apiKey) pair. Otherwise returns null so the caller will
     * fetch fresh data from the network.
     *
     * No raw API key is ever stored in the cache record — only its hash.
     */
    fun getCachedModelsChatgpt(context: Context): List<String>? {
        val currentFingerprint = computeChatgptModelsFingerprint(context) ?: return null
        val storedFingerprint = SecureStore.get(context, SecureStoreKey.API_MODELS_CACHE_FINGERPRINT_CHATGPT)?.get()
        if (storedFingerprint != currentFingerprint) return null
        val cachedJson = SecureStore.get(context, SecureStoreKey.API_MODELS_CACHE_CHATGPT)?.get() ?: return null
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            Gson().fromJson<List<String>>(cachedJson, type)?.takeIf { it.isNotEmpty() }
        } catch (_: JsonSyntaxException) {
            null
        }
    }

    /**
     * Persists a freshly-fetched ChatGPT model list together with a fingerprint
     * derived from the current (baseUrl, apiKey). When either changes the
     * fingerprint will no longer match on the next read and the cache lookup
     * misses, triggering a fresh fetch.
     */
    fun setCachedModelsChatgpt(context: Context, models: List<String>) {
        val fingerprint = computeChatgptModelsFingerprint(context) ?: return
        val json = Gson().toJson(models)
        SecureStore.set(context, SecureStoreKey.API_MODELS_CACHE_CHATGPT, json)
        SecureStore.set(context, SecureStoreKey.API_MODELS_CACHE_FINGERPRINT_CHATGPT, fingerprint)
    }

    /**
     * Hex-encoded SHA-256 of "${trimmedBaseUrl}|${trimmedApiKey}", truncated to
     * the first 16 chars. Returns null when either input is blank so callers
     * skip the cache rather than risking a collision on empty credentials.
     */
    private fun computeChatgptModelsFingerprint(context: Context): String? {
        val baseUrl = getApiBaseUrlChatgpt(context)?.trim().orEmpty()
        val apiKey = getApiKeyChatgpt(context)?.trim().orEmpty()
        if (baseUrl.isEmpty() || apiKey.isEmpty()) return null
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$baseUrl|$apiKey".toByteArray(Charsets.UTF_8))
        val hex = StringBuilder(digest.size * 2)
        for (b in digest) {
            val v = b.toInt() and 0xff
            hex.append(HEX_CHARS[v ushr 4])
            hex.append(HEX_CHARS[v and 0x0f])
        }
        return hex.substring(0, 16)
    }

    private val HEX_CHARS = "0123456789abcdef".toCharArray()

}
