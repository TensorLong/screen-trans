package com.yiqun.translator.data.remote.ai.chatgpt

import kotlinx.coroutines.CompletableDeferred

object SenseGroupRequestCache {
    private const val MAX_ENTRIES = 48

    private data class Key(
        val model: String,
        val endpointUrl: String,
        val word: String,
        val sentence: String,
        val pointedTokenOffset: Int?,
        val sourceLanguageCode: String,
        val targetLanguageCode: String,
    )

    private val cache = object : LinkedHashMap<Key, SenseGroup>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, SenseGroup>?): Boolean {
            return size > MAX_ENTRIES
        }
    }
    private val inFlight = mutableMapOf<Key, CompletableDeferred<SenseGroup?>>()

    fun get(
        model: String,
        endpointUrl: String,
        word: String,
        sentence: String,
        pointedTokenOffset: Int?,
        sourceLanguageCode: String,
        targetLanguageCode: String,
    ): SenseGroup? = synchronized(cache) {
        cache[Key(model, endpointUrl, word, sentence, pointedTokenOffset, sourceLanguageCode, targetLanguageCode)]
    }

    fun put(
        model: String,
        endpointUrl: String,
        word: String,
        sentence: String,
        pointedTokenOffset: Int?,
        sourceLanguageCode: String,
        targetLanguageCode: String,
        senseGroup: SenseGroup,
    ) = synchronized(cache) {
        cache[Key(model, endpointUrl, word, sentence, pointedTokenOffset, sourceLanguageCode, targetLanguageCode)] = senseGroup
    }

    suspend fun getOrLoad(
        model: String,
        endpointUrl: String,
        word: String,
        sentence: String,
        pointedTokenOffset: Int?,
        sourceLanguageCode: String,
        targetLanguageCode: String,
        load: suspend () -> SenseGroup?,
    ): SenseGroup? {
        val key = Key(model, endpointUrl, word, sentence, pointedTokenOffset, sourceLanguageCode, targetLanguageCode)
        var shouldLoad = false
        val deferred = synchronized(cache) {
            cache[key]?.let { return it }
            inFlight[key] ?: CompletableDeferred<SenseGroup?>().also {
                inFlight[key] = it
                shouldLoad = true
            }
        }

        if (!shouldLoad) return deferred.await()

        return try {
            val result = load()
            synchronized(cache) {
                if (result != null) cache[key] = result
                inFlight.remove(key)
            }
            deferred.complete(result)
            result
        } catch (throwable: Throwable) {
            synchronized(cache) {
                inFlight.remove(key)
            }
            deferred.completeExceptionally(throwable)
            throw throwable
        }
    }

    fun clear() = synchronized(cache) {
        cache.clear()
        inFlight.values.forEach { it.cancel() }
        inFlight.clear()
    }

    fun size(): Int = synchronized(cache) {
        cache.size
    }
}
