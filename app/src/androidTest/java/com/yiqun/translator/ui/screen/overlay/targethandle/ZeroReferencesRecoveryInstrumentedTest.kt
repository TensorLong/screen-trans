package com.yiqun.translator.ui.screen.overlay.targethandle

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yiqun.translator.data.local.capture.CaptureRepository
import com.yiqun.translator.data.local.capture.CaptureResponse
import com.yiqun.translator.data.local.capture.NoMediaProjectionTokenException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression test for the zero-references / dead-pipeline bug:
 *
 * Before the fix: after the last reference was released, `state` was left at `Ready`.
 * Every subsequent `request()` skipped `start()` (state == Ready) and starved on a
 * dead pipeline, returning `CaptureTimeoutException` after 700 ms — silently, forever.
 *
 * Assertions:
 *   (a) A Ready state with null virtualDisplay/imageReader (dead-pipeline shape) no longer
 *       causes a 700 ms timeout starve; `start()` runs and surfaces
 *       `NoMediaProjectionTokenException` immediately.
 *   (b) `onZeroReferences()` resets `state` to `Uninitialized`.
 *
 * No real MediaProjection grant is needed: the null-token path in `start()` throws
 * synchronously and the exception is caught and returned as `CaptureResponse.Error`.
 */
@RunWith(AndroidJUnit4::class)
class ZeroReferencesRecoveryInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    // ---------------------------------------------------------------------------
    // (a) Dead-pipeline Ready state routes through start(), not 700 ms timeout
    // ---------------------------------------------------------------------------

    @Test
    fun deadPipelineReadyStateRoutesToStartNotTimeout() {
        CaptureRepository.mediaProjectionToken = null
        val repository = CaptureRepository(context)
        repository.acquire()
        try {
            // Simulate the post-zero-references broken shape:
            // state = Ready but virtualDisplay/imageReader are null (cleared resources).
            // Before the fix, request() would skip start() (state == Ready) and starve
            // on the dead pipeline, returning CaptureTimeoutException after 700 ms.
            // After the fix, isPipelineUsable() detects the null resources, calls start(),
            // which hits the null token and returns NoMediaProjectionTokenException at once.
            forceState(repository, "Ready")

            val response = runBlocking { repository.request() }

            assertTrue(
                "Expected Error(NoMediaProjectionTokenException), not Error(CaptureTimeoutException) — " +
                        "dead-pipeline Ready must trigger start(), not a 700 ms starve. Got: $response",
                response is CaptureResponse.Error && response.t is NoMediaProjectionTokenException,
            )
        } finally {
            repository.release()
        }
    }

    // ---------------------------------------------------------------------------
    // (b) onZeroReferences() resets state to Uninitialized
    // ---------------------------------------------------------------------------

    @Test
    fun onZeroReferencesResetsStateToUninitialized() {
        CaptureRepository.mediaProjectionToken = null
        val repository = CaptureRepository(context)
        repository.acquire()

        // Force state to Ready, simulating a completed session.
        forceState(repository, "Ready")
        assertEquals(
            "Precondition: state must be Ready before release",
            "Ready",
            readStateName(repository),
        )

        // release() drops the reference count to zero, which calls onZeroReferences().
        repository.release()

        assertEquals(
            "onZeroReferences() must reset state to Uninitialized so the next request() " +
                    "goes through start() instead of starving on a dead pipeline",
            "Uninitialized",
            readStateName(repository),
        )
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    /**
     * Sets the private `state` field of [repository] to the enum constant whose name
     * matches [stateName] ("Ready" or "Uninitialized"). Comparing by name avoids
     * visibility issues with the private enum type.
     */
    private fun forceState(repository: CaptureRepository, stateName: String) {
        val field = CaptureRepository::class.java.getDeclaredField("state").also {
            it.isAccessible = true
        }
        val constant = field.type.enumConstants!!.first { (it as Enum<*>).name == stateName }
        field.set(repository, constant)
    }

    /** Returns the `name` of the current `state` field value. */
    private fun readStateName(repository: CaptureRepository): String {
        val field = CaptureRepository::class.java.getDeclaredField("state").also {
            it.isAccessible = true
        }
        return (field.get(repository) as Enum<*>).name
    }
}
