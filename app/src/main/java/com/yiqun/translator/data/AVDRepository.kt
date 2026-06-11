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
        // Capture the scope we owned inside the lock: if another thread re-acquires
        // before the cancel below runs, cancelling the field would kill the NEW scope.
        var scopeToCancel: CoroutineScope? = null
        synchronized(lock) {
            if (referenceCount > 0) {
                referenceCount--
                if (referenceCount == 0) {
                    scopeToCancel = avdCoroutineScope
                }
            }
        }
        scopeToCancel?.let { scope ->
            onZeroReferences()
            scope.cancel()
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
