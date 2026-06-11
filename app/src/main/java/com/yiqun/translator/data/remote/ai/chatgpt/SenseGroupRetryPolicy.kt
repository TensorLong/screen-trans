package com.yiqun.translator.data.remote.ai.chatgpt

import kotlinx.coroutines.TimeoutCancellationException
import retrofit2.HttpException
import java.io.IOException

/**
 * Retry policy for sense-group model calls.
 *
 * Measured completion latency across OpenRouter models (gpt-4o-mini,
 * gemini-2.5-flash, claude-haiku-4.5, deepseek-v3, llama-3.3-70b) is
 * 0.8–2.5s typical with a 6.6s worst case (cold provider). A 12s
 * per-attempt budget covers that with margin while keeping a hung
 * connection from blocking for the transport-level 60s read / 90s call
 * timeout. Transient failures are retried with backoff and logged only;
 * an error event reaches the user solely when the final attempt fails,
 * so a request that eventually succeeds never shows an error prompt.
 */
object SenseGroupRetryPolicy {

    const val MAX_ATTEMPTS = 3
    const val ATTEMPT_TIMEOUT_MS = 12_000L
    private const val BACKOFF_STEP_MS = 400L

    fun backoffMs(attempt: Int): Long = BACKOFF_STEP_MS * attempt

    /**
     * Whether [error] is a transient failure worth retrying: connectivity
     * problems, per-attempt timeouts, request timeouts (408), rate limits
     * (429) and server errors (5xx). Quota exhaustion (402) and other
     * client errors are permanent for the current request.
     */
    fun isTransient(error: Throwable): Boolean = when (error) {
        is TimeoutCancellationException -> true
        is IOException -> true
        is HttpException -> error.code() == 408 || error.code() == 429 || error.code() in 500..599
        else -> false
    }

    fun shouldRetry(attempt: Int, error: Throwable): Boolean =
        attempt < MAX_ATTEMPTS && isTransient(error)

    /** Maps the terminal failure of a request to the user-facing error event. */
    fun errorEventFor(error: Throwable): SenseGroupErrorEvent = when {
        error is HttpException && (error.code() == 402 || error.code() == 429) ->
            SenseGroupErrorEvent.Quota
        error is HttpException -> SenseGroupErrorEvent.Network(error.code())
        else -> SenseGroupErrorEvent.Network()
    }
}
