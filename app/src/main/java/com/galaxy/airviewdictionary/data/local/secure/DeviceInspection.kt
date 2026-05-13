package com.galaxy.airviewdictionary.data.local.secure

/*
    앱 내 디바이스 환경 분석 결과
 */
enum class DeviceInspection(
    val description: String,
) {
    KEYSTORE_NOT_AVAILABLE(
        description = "키스토어 사용이 불가능 합니다.",
    ),
    PLAYSTORE_UPDATE_REQUIRED(
        description = "플레이 스토어 업데이트가 필요합니다.",
    ),
    INTEGRITY_FAILURES_EXCEEDED(
        description = "Integrity 확인 실패.",
    ),

}
