package com.galaxy.airviewdictionary.data.local.secure

/*
    environmentDetails: {
        playProtectVerdict: "NO_ISSUES"
    }

    NO_ISSUES
    - Play 프로텍트가 사용 설정되어 있으며 문제가 발견되지 않았으므로 사용자 작업이 필요하지 않습니다.
    POSSIBLE_RISK 및 NO_DATA
    - 이러한 확인 결과를 수신하면 사용자에게 Play 프로텍트가 사용 설정되어 있고 검사를 실행했는지 확인하도록 요청합니다. NO_DATA는 드물게만 표시되어야 합니다.
    MEDIUM_RISK 및 HIGH_RISK
    - 위험 허용 범위에 따라 사용자에게 Play 프로텍트를 실행하고 Play 프로텍트 경고에 조치를 취하도록 요청할 수 있습니다. 사용자가 이러한 요구사항을 충족할 수 없다면 서버 작업에서 차단할 수 있습니다.
 */
enum class VerdictPlayProtect(
    val description: String,
) {
    NO_ISSUES(
        description = "Play 프로텍트가 사용 설정되어 있으며 기기에서 앱 문제를 발견하지 못했습니다.",
    ),
    NO_DATA(
        description = "Play 프로텍트가 사용 설정되어 있지만 아직 검사가 이루어지지 않았습니다. 기기 또는 Play 스토어 앱이 최근에 재설정되었을 수 있습니다.",
    ),
    POSSIBLE_RISK(
        description = "Play 프로텍트가 사용 중지되어 있습니다.",
    ),
    MEDIUM_RISK(
        description = "Play 프로텍트가 사용 설정되어 있으며 기기에 설치된 잠재적으로 위험한 앱을 발견했습니다.",
    ),
    HIGH_RISK(
        description = "Play 프로텍트가 사용 설정되어 있으며 기기에 설치된 위험한 앱을 발견했습니다.",
    ),
    UNEVALUATED(
        description = "Play 프로텍트 확인 결과가 평가되지 않았습니다.\n" +
                "이는 다음을 비롯하여 여러 가지 이유로 발생할 수 있습니다.\n" +
                "- 기기를 충분히 신뢰할 수 없습니다.\n" +
                "- 게임에만 적용: 사용자 계정에 게임의 Play 라이선스가 없습니다.",
    ),
}
