package com.yiqun.translator.data.local.capture

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression tests for the permanent capture brick: CaptureRepository is a singleton, and
 * onZeroReferences() used to clear the projection pipeline without resetting the state
 * machine. The singleton then stayed Ready forever, every request() skipped start() and
 * starved on a dead pipeline — silently, and re-granting the projection never healed it.
 */
@RunWith(AndroidJUnit4::class)
class ZeroReferencesRecoveryInstrumentedTest {

    private lateinit var repository: CaptureRepository

    @Before
    fun setUp() {
        repository = CaptureRepository(ApplicationProvider.getApplicationContext())
        CaptureRepository.mediaProjectionToken = null
    }

    @Test
    fun onZeroReferencesResetsStateToUninitialized() {
        repository.acquire()
        forceState(repository, "Ready")

        repository.release()

        assertEquals("Uninitialized", readStateName(repository))
    }

    @Test
    fun requestAfterZeroReferencesSurfacesReauthInsteadOfSilentTimeout() {
        repository.acquire()
        forceState(repository, "Ready")
        repository.release()

        val response = runBlocking { repository.request() }

        assertTrue(
            "Expected NoMediaProjectionTokenException (re-auth trigger), got $response",
            response is CaptureResponse.Error && response.t is NoMediaProjectionTokenException,
        )
    }

    private fun forceState(target: CaptureRepository, stateName: String) {
        val field = CaptureRepository::class.java.getDeclaredField("state")
        field.isAccessible = true
        val value = field.type.enumConstants!!.first { (it as Enum<*>).name == stateName }
        field.set(target, value)
    }

    private fun readStateName(target: CaptureRepository): String {
        val field = CaptureRepository::class.java.getDeclaredField("state")
        field.isAccessible = true
        return (field.get(target) as Enum<*>).name
    }
}
