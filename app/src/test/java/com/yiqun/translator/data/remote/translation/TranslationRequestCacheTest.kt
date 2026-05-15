package com.yiqun.translator.data.remote.translation

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TranslationRequestCacheTest {

    @After
    fun tearDown() {
        TranslationRequestCache.clear()
    }

    @Test
    fun cacheReturnsOnlyExactTranslationRequestMatches() {
        val transaction = Transaction(
            sourceLanguageCode = "en",
            targetLanguageCode = "zh-CN",
            sourceText = "careful decision",
            translationKitType = TranslationKitType.GOOGLE,
            detectedLanguageCode = "en",
            resultText = "谨慎的决定",
        )

        TranslationRequestCache.put(transaction)

        assertEquals(
            transaction,
            TranslationRequestCache.get(TranslationKitType.GOOGLE, "en", "zh-CN", "careful decision"),
        )
        assertNull(TranslationRequestCache.get(TranslationKitType.GOOGLE, "en", "ja", "careful decision"))
    }
}
