package com.galaxy.airviewdictionary.data.local.secure

/*
    accountDetails: {
          // This field can be LICENSED, UNLICENSED, or UNEVALUATED.
          appLicensingVerdict: "LICENSED"
    }
 */
enum class VerdictAppLicensing(
    val description: String,
) {
    LICENSED(
        description = "사용자에게 앱 권한이 있습니다. 즉, 사용자가 기기에서 Google Play를 통해 앱을 설치했거나 업데이트했습니다.",
    ),
    UNLICENSED(
        description = "사용자에게 앱 사용 권한이 없습니다. 예를 들어 사용자가 앱을 사이드로드한 경우 또는 Google Play에서 앱을 획득하지 않은 경우에 이러한 상황이 발생합니다. 사용자에게 GET_LICENSED 대화상자를 표시하여 이 문제를 해결할 수 있습니다.",
    ),
    UNEVALUATED(
        description = "필요한 요구사항을 충족하지 못하여 라이선스 세부정보가 평가되지 않았습니다.\n" +
                "이는 다음을 비롯하여 여러 가지 이유로 발생할 수 있습니다.\n" +
                "- 기기를 충분히 신뢰할 수 없습니다.\n" +
                "- 기기에 설치된 앱 버전을 Google Play에서 알 수 없습니다.\n" +
                "- 사용자가 Google Play에 로그인하지 않았습니다.",
    ),
}
