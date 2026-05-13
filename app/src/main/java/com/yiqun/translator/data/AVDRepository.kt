package com.yiqun.translator.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

abstract class AVDRepository {

    protected val TAG = javaClass.simpleName

    private var avdCoroutineScope = CoroutineScope(Dispatchers.IO + Job())

    private val lock = Any()
    private var referenceCount = 0

    fun acquire() {
        synchronized(lock) {
            if (referenceCount == 0) {
                avdCoroutineScope = CoroutineScope(Dispatchers.IO + Job())
            }
            referenceCount++
        }
    }

    fun release() {
        synchronized(lock) {
            if (referenceCount > 0) {
                referenceCount--
                if (referenceCount == 0) {
                    avdCoroutineScope.launch {
                        onZeroReferences()
                        avdCoroutineScope.cancel()
                    }
                }
            }
        }
    }

    protected fun launchInAVDCoroutineScope(block: suspend CoroutineScope.() -> Unit): Job {
        return avdCoroutineScope.launch(block = block)
    }

    protected open fun onZeroReferences() {

    }

}

