package com.yiqun.translator.data.remote.ai.chatgpt

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException

class SenseGroupRetryPolicyTest {

    private fun httpException(code: Int): HttpException =
        HttpException(Response.error<Any>(code, "".toResponseBody("application/json".toMediaType())))

    private fun timeoutCancellation(): TimeoutCancellationException {
        try {
            runBlocking { withTimeout(1) { delay(1_000) } }
        } catch (e: TimeoutCancellationException) {
            return e
        }
        fail("withTimeout did not time out")
        throw AssertionError()
    }

    @Test
    fun connectivityAndTimeoutFailuresAreTransient() {
        assertTrue(SenseGroupRetryPolicy.isTransient(IOException("reset")))
        assertTrue(SenseGroupRetryPolicy.isTransient(SocketTimeoutException("read timed out")))
        assertTrue(SenseGroupRetryPolicy.isTransient(timeoutCancellation()))
    }

    @Test
    fun retryableHttpCodesAreTransient() {
        assertTrue(SenseGroupRetryPolicy.isTransient(httpException(408)))
        assertTrue(SenseGroupRetryPolicy.isTransient(httpException(429)))
        assertTrue(SenseGroupRetryPolicy.isTransient(httpException(500)))
        assertTrue(SenseGroupRetryPolicy.isTransient(httpException(503)))
    }

    @Test
    fun permanentFailuresAreNotTransient() {
        assertFalse(SenseGroupRetryPolicy.isTransient(httpException(402)))
        assertFalse(SenseGroupRetryPolicy.isTransient(httpException(400)))
        assertFalse(SenseGroupRetryPolicy.isTransient(httpException(401)))
        assertFalse(SenseGroupRetryPolicy.isTransient(IllegalStateException("parse")))
    }

    @Test
    fun retriesStopAtMaxAttemptsEvenForTransientErrors() {
        val transient = IOException("blip")
        assertTrue(SenseGroupRetryPolicy.shouldRetry(1, transient))
        assertTrue(SenseGroupRetryPolicy.shouldRetry(SenseGroupRetryPolicy.MAX_ATTEMPTS - 1, transient))
        assertFalse(SenseGroupRetryPolicy.shouldRetry(SenseGroupRetryPolicy.MAX_ATTEMPTS, transient))
    }

    @Test
    fun permanentErrorsAreNeverRetried() {
        assertFalse(SenseGroupRetryPolicy.shouldRetry(1, httpException(402)))
        assertFalse(SenseGroupRetryPolicy.shouldRetry(1, IllegalStateException()))
    }

    @Test
    fun backoffGrowsWithAttempts() {
        assertTrue(SenseGroupRetryPolicy.backoffMs(1) > 0)
        assertTrue(SenseGroupRetryPolicy.backoffMs(2) > SenseGroupRetryPolicy.backoffMs(1))
    }

    @Test
    fun attemptBudgetCoversMeasuredWorstCaseLatency() {
        // Worst measured OpenRouter completion was 6.6s (cold provider);
        // the budget must cover it with margin yet stay far below the
        // transport-level 60s read timeout that used to gate failures.
        assertTrue(SenseGroupRetryPolicy.ATTEMPT_TIMEOUT_MS >= 10_000L)
        assertTrue(SenseGroupRetryPolicy.ATTEMPT_TIMEOUT_MS <= 20_000L)
        assertEquals(3, SenseGroupRetryPolicy.MAX_ATTEMPTS)
    }

    @Test
    fun terminalQuotaCodesMapToQuotaEvent() {
        assertEquals(SenseGroupErrorEvent.Quota, SenseGroupRetryPolicy.errorEventFor(httpException(402)))
        assertEquals(SenseGroupErrorEvent.Quota, SenseGroupRetryPolicy.errorEventFor(httpException(429)))
    }

    @Test
    fun terminalHttpFailuresMapToNetworkEventWithCode() {
        val event = SenseGroupRetryPolicy.errorEventFor(httpException(503))
        assertTrue(event is SenseGroupErrorEvent.Network)
        assertEquals(503, (event as SenseGroupErrorEvent.Network).code)
    }

    @Test
    fun terminalNonHttpFailuresMapToNetworkEventWithoutCode() {
        val io = SenseGroupRetryPolicy.errorEventFor(IOException("offline"))
        assertNotNull(io)
        assertTrue(io is SenseGroupErrorEvent.Network)
        assertEquals(null, (io as SenseGroupErrorEvent.Network).code)

        val timeout = SenseGroupRetryPolicy.errorEventFor(timeoutCancellation())
        assertTrue(timeout is SenseGroupErrorEvent.Network)
        assertEquals(null, (timeout as SenseGroupErrorEvent.Network).code)
    }
}
