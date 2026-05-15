package com.yiqun.translator.data.remote.ai.chatgpt

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

    fun clear() = synchronized(cache) {
        cache.clear()
    }

    fun size(): Int = synchronized(cache) {
        cache.size
    }
}
