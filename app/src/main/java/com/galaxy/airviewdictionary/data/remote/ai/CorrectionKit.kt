package com.galaxy.airviewdictionary.data.remote.ai


abstract class CorrectionKit {

    protected val TAG: String = javaClass.simpleName

    abstract fun available(): Boolean

    /**
     * Request correction.
     * This function would block current thread and coroutine cannot be properly suspended.
     * Therefore, it must be used within 'viewModelScope.launch' syntax.
     *
     * [String] correction
     */
    abstract suspend fun request(
        sourceLanguageCode: String,
        sourceText: String,
    ): String

}


