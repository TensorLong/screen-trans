package com.yiqun.translator.data.remote.ai.chatgpt

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class SenseGroupRequestCacheTest {

    @After
    fun tearDown() {
        SenseGroupRequestCache.clear()
    }

    @Test
    fun cacheReturnsOnlyExactSenseGroupRequestMatches() {
        val group = SenseGroup("careful decision", "", 25..41)

        SenseGroupRequestCache.put(
            model = "openai/gpt-4o-mini",
            endpointUrl = "https://openrouter.ai/api/v1/chat/completions",
            word = "decision",
            sentence = "The committee made a careful decision after reviewing the evidence.",
            pointedTokenOffset = 30,
            sourceLanguageCode = "en",
            targetLanguageCode = "zh-CN",
            senseGroup = group,
        )

        assertEquals(
            group,
            SenseGroupRequestCache.get(
                model = "openai/gpt-4o-mini",
                endpointUrl = "https://openrouter.ai/api/v1/chat/completions",
                word = "decision",
                sentence = "The committee made a careful decision after reviewing the evidence.",
                pointedTokenOffset = 30,
                sourceLanguageCode = "en",
                targetLanguageCode = "zh-CN",
            )
        )
        assertNull(
            SenseGroupRequestCache.get(
                model = "openai/gpt-4o-mini",
                endpointUrl = "https://openrouter.ai/api/v1/chat/completions",
                word = "careful",
                sentence = "The committee made a careful decision after reviewing the evidence.",
                pointedTokenOffset = 30,
                sourceLanguageCode = "en",
                targetLanguageCode = "zh-CN",
            )
        )
    }

    @Test
    fun concurrentLoadsForSameRequestShareSingleInFlightCall() = runBlocking {
        val group = SenseGroup("will make careful choice", "将做出谨慎选择", 5..29)
        val loadCount = AtomicInteger(0)
        val releaseLoader = CompletableDeferred<Unit>()

        val requests = List(3) {
            async(Dispatchers.Default) {
                SenseGroupRequestCache.getOrLoad(
                    model = "debug-model",
                    endpointUrl = "http://127.0.0.1:8097/v1/chat/completions",
                    word = "will",
                    sentence = "They will make careful choice",
                    pointedTokenOffset = 5,
                    sourceLanguageCode = "en",
                    targetLanguageCode = "zh",
                ) {
                    loadCount.incrementAndGet()
                    releaseLoader.await()
                    group
                }
            }
        }

        while (loadCount.get() == 0) {
            Thread.sleep(5)
        }
        releaseLoader.complete(Unit)

        assertEquals(listOf(group, group, group), requests.awaitAll())
        assertEquals(1, loadCount.get())
        assertEquals(1, SenseGroupRequestCache.size())
    }
}
