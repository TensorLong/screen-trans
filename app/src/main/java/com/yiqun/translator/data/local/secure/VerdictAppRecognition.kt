package com.yiqun.translator.data.local.secure

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
        description = "The app and certificate match a version distributed by Google Play.",
    ),
    UNRECOGNIZED_VERSION(
        description = "The certificate or package name does not match Google Play records.",
    ),
    UNEVALUATED(
        description = "Application integrity was not evaluated because required conditions were not met.",
    ),
}
