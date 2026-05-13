package com.yiqun.translator.data.local.secure

/*
 */
enum class DeviceInspection(
    val description: String,
) {
    KEYSTORE_NOT_AVAILABLE(
        description = "Keystore is not available.",
    ),
    PLAYSTORE_UPDATE_REQUIRED(
        description = "Play Store update is required.",
    ),
    INTEGRITY_FAILURES_EXCEEDED(
        description = "Integrity checks failed.",
    ),

}
