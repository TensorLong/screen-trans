package com.yiqun.translator.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AVDRepositoryTest {

    @Test
    fun firstAndZeroReferenceHooksRunOnlyAtLifecycleEdges() {
        val repository = HookedRepository()

        repository.acquire()
        repository.acquire()
        repository.release()
        repository.release()

        assertEquals(1, repository.firstReferenceCount)
        assertEquals(1, repository.zeroReferenceCount)
    }

    private class HookedRepository : AVDRepository() {
        var firstReferenceCount = 0
        var zeroReferenceCount = 0

        override fun onFirstReference() {
            firstReferenceCount++
        }

        override fun onZeroReferences() {
            zeroReferenceCount++
        }
    }
}
