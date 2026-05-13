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
enum class DeviceActivityLevel(
    val description: String,
) {
    LEVEL_1(
        description = "지난 1시간 동안 이 기기에서 앱별로 표준 API 무결성 토큰 요청 10개 이하",
    ),
    LEVEL_2(
        description = "지난 1시간 동안 이 기기에서 앱별로 표준 API 무결성 토큰 요청 11~25개",
    ),
    LEVEL_3(
        description = "지난 1시간 동안 이 기기에서 앱별로 표준 API 무결성 토큰 요청 26~50세",
    ),
    LEVEL_4(
        description = "지난 1시간 동안 이 기기에서 앱별로 표준 API 무결성 토큰 요청 50개 초과",
    ),
    UNEVALUATED(
        description = "\t최근 기기 활동이 평가되지 않았습니다. 다음과 같은 이유로 이러한 문제가 발생할 수 있습니다.\n" +
                "- 기기를 충분히 신뢰할 수 없습니다.\n" +
                "- 기기에 설치된 앱 버전을 Google Play에서 알 수 없습니다.\n" +
                "- 기기의 기술적 문제입니다.",
    ),
}
