package com.yiqun.translator.data.local.secure

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
        description = "The app is running on a supported Android device with Google Play services. The device passes system integrity checks and meets Android compatibility requirements.",
    ),
    MEETS_BASIC_INTEGRITY(
        description = "The app is running on a device that passes basic system integrity checks. On Android 13 and later, Android platform key attestation is required. The device may not meet Android compatibility requirements or may not be approved to run Google Play services.",
    ),
    MEETS_STRONG_INTEGRITY(
        description = "The app is running on a supported Android device with Google Play services and strong system integrity guarantees such as hardware-backed boot integrity. On Android 13 and later, a recent security update is required.",
    ),
    UNEVALUATED(
        description = "The app is running on a device with signs of attack or system compromise, or it is not running on a real device that can pass Google Play integrity checks.",
    ),
}
