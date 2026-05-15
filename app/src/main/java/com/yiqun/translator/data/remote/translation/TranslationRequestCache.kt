package com.yiqun.translator.data.remote.translation

object TranslationRequestCache {
    private const val MAX_ENTRIES = 64

    private data class Key(
        val kitType: TranslationKitType,
        val sourceLanguageCode: String,
        val targetLanguageCode: String,
        val sourceText: String,
    )

    private val cache = object : LinkedHashMap<Key, Transaction>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, Transaction>?): Boolean {
            return size > MAX_ENTRIES
        }
    }

    fun get(
        kitType: TranslationKitType,
        sourceLanguageCode: String,
        targetLanguageCode: String,
        sourceText: String,
    ): Transaction? = synchronized(cache) {
        cache[Key(kitType, sourceLanguageCode, targetLanguageCode, sourceText)]
    }

    fun put(transaction: Transaction) {
        val kitType = transaction.translationKitType ?: return
        val sourceLanguageCode = transaction.sourceLanguageCode ?: return
        val targetLanguageCode = transaction.targetLanguageCode ?: return
        val sourceText = transaction.sourceText ?: return
        synchronized(cache) {
            cache[Key(kitType, sourceLanguageCode, targetLanguageCode, sourceText)] = transaction
        }
    }

    fun clear() = synchronized(cache) {
        cache.clear()
    }

    fun size(): Int = synchronized(cache) {
        cache.size
    }
}
