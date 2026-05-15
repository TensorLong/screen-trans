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
        var shouldNotifyFirstReference = false
        synchronized(lock) {
            if (referenceCount == 0) {
                avdCoroutineScope = CoroutineScope(Dispatchers.IO + Job())
                shouldNotifyFirstReference = true
            }
            referenceCount++
        }
        if (shouldNotifyFirstReference) {
            onFirstReference()
        }
    }

    fun release() {
        var shouldNotifyZeroReferences = false
        synchronized(lock) {
            if (referenceCount > 0) {
                referenceCount--
                if (referenceCount == 0) {
                    shouldNotifyZeroReferences = true
                }
            }
        }
        if (shouldNotifyZeroReferences) {
            onZeroReferences()
            avdCoroutineScope.cancel()
        }
    }

    protected fun launchInAVDCoroutineScope(block: suspend CoroutineScope.() -> Unit): Job {
        return avdCoroutineScope.launch(block = block)
    }

    protected open fun onFirstReference() {

    }

    protected open fun onZeroReferences() {

    }

}
