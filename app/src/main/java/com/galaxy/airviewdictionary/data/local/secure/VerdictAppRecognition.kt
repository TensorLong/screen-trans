package com.galaxy.airviewdictionary.data.local.secure

/*
    appIntegrity: {
          // PLAY_RECOGNIZED, UNRECOGNIZED_VERSION, or UNEVALUATED.
          appRecognitionVerdict: "PLAY_RECOGNIZED"
          // The package name of the app.
          // This field is populated iff appRecognitionVerdict != UNEVALUATED.
          packageName: "com.package.name"
          // The sha256 digest of app certificates (base64-encoded URL-safe).
          // This field is populated iff appRecognitionVerdict != UNEVALUATED.
          certificateSha256Digest: ["6a6a1474b5cbbb2b1aa57e0bc3"]
          // The version of the app.
          // This field is populated iff appRecognitionVerdict != UNEVALUATED.
          versionCode: "42"
    }
 */
enum class VerdictAppRecognition(
    val description: String,
) {
    PLAY_RECOGNIZED(
        description = "앱과 인증서가 Google Play에서 배포된 버전과 일치합니다.",
    ),
    UNRECOGNIZED_VERSION(
        description = "인증서나 패키지 이름이 Google Play 기록과 일치하지 않습니다.",
    ),
    UNEVALUATED(
        description = "애플리케이션 무결성이 평가되지 않았습니다. 기기를 충분히 신뢰할 수 없는 등 필요한 요구사항을 충족하지 못했습니다.",
    ),
}
