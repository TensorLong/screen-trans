package com.galaxy.airviewdictionary.data.local.secure

/*
    Play Integrity API 정상응답 수신 여부
 */
enum class IntegrityResponse(
    val description: String,
) {
    SUCCESS(
        description = "Play Integrity API 정상응답 수신",
    ),
    HTTP_ERROR(
        description = "Play Integrity API 통신 오류",
    ),
    UNKNOWN_PACKAGE(
        description = "com.galaxy.airviewdictionary 가 아닌 패키지에서의 api 호출",
    ),
    FAILED(
        description = "Play Integrity API 정상응답 수신하지 못함",
    ),
}
