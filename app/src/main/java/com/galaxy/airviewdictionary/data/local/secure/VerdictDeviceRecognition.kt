package com.galaxy.airviewdictionary.data.local.secure

/*
    deviceIntegrity: {
          deviceRecognitionVerdict: ["MEETS_DEVICE_INTEGRITY"]
          recentDeviceActivity: {
            // "LEVEL_2" is one of several possible values.
            deviceActivityLevel: "LEVEL_2"
          }
    }
 */
enum class VerdictDeviceRecognition(
    val description: String,
) {
    MEETS_DEVICE_INTEGRITY(
        description = "앱이 Google Play 서비스가 설치된 Android 지원 기기에서 실행 중입니다. 기기는 시스템 무결성 검사를 통과하고 Android 호환성 요구사항을 충족합니다.",
    ),
    MEETS_BASIC_INTEGRITY(
        description = "앱이 기본 시스템 무결성 검사를 통과한 기기에서 실행되며 Android 13 이상 기기의 경우 Android 플랫폼 키 증명이 필요합니다. 기기가 Android 호환성 요구사항을 충족하지 못할 수 있고 Google Play 서비스 실행이 승인되지 않을 수도 있습니다. 예를 들어 기기가 인식할 수 없는 Android 버전을 실행하거나 잠금 해제된 부트로더, 확인되지 않은 부팅을 보유하거나 제조업체의 인증을 받지 않았을 수 있습니다.",
    ),
    MEETS_STRONG_INTEGRITY(
        description = "앱이 Google Play 서비스가 적용된 Android 지원 기기에서 실행되며 하드웨어 지원 부팅 무결성 증명과 같은 시스템 무결성을 강력히 보장합니다. Android 13 이상 기기의 경우 지난 1년 이내에 보안 업데이트를 받아야 합니다. 기기는 시스템 무결성 검사를 통과하고 Android 호환성 요구사항을 충족합니다.",
    ),
    UNEVALUATED(
        description = "앱이 공격(예: API 후킹)이나 시스템 손상(예: 루팅됨) 징후가 있는 기기에서 실행되거나, 앱이 Google Play 무결성 검사를 통과하지 못한 에뮬레이터와 같은 실제 기기에서 실행되지 않습니다.",
    ),
}
