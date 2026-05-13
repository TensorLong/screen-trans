package com.galaxy.airviewdictionary.data

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
                avdCoroutineScope = CoroutineScope(Dispatchers.IO + Job()) // 스코프 재생성
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

    // 자원 해제 등 비동기 작업 수행
    protected open fun onZeroReferences() {

    }

}

