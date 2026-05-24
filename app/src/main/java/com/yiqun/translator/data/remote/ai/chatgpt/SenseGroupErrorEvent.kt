package com.yiqun.translator.data.remote.ai.chatgpt

/**
 * Side-channel event surfaced by [ChatGPTKit.senseGroupAt] when the call
 * cannot return a usable [SenseGroup]. The UI layer collects these to show a
 * transient localized snackbar/toast so the user can distinguish a real model
 * failure from the normal whole-sentence fallback.
 */
sealed interface SenseGroupErrorEvent {

    /** HTTP 402 / 429 — free-tier quota exhausted or rate-limited. */
    object Quota : SenseGroupErrorEvent

    /** Model returned 200 OK but content was blank or had no usable chunk
     *  (typical reasoning-blackout symptom from some free models). */
    object Empty : SenseGroupErrorEvent

    /** Other transport / HTTP error. [code] is the HTTP status if available. */
    data class Network(val code: Int? = null) : SenseGroupErrorEvent

    /** Response body could not be parsed as the expected JSON schema. */
    data class Parse(val message: String) : SenseGroupErrorEvent
}
