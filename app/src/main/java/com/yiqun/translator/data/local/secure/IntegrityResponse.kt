package com.yiqun.translator.data.local.secure

/*
 */
enum class IntegrityResponse(
    val description: String,
) {
    SUCCESS(
        description = "Received a successful Play Integrity API response",
    ),
    HTTP_ERROR(
        description = "Play Integrity API communication error",
    ),
    UNKNOWN_PACKAGE(
        description = "API call came from a package other than com.yiqun.translator",
    ),
    FAILED(
        description = "Did not receive a successful Play Integrity API response",
    ),
}
