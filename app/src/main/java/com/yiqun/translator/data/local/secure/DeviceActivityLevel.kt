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
enum class DeviceActivityLevel(
    val description: String,
) {
    LEVEL_1(
        description = "10 or fewer standard API integrity token requests for this app on this device in the last hour",
    ),
    LEVEL_2(
        description = "11 to 25 standard API integrity token requests for this app on this device in the last hour",
    ),
    LEVEL_3(
        description = "26 to 50 standard API integrity token requests for this app on this device in the last hour",
    ),
    LEVEL_4(
        description = "More than 50 standard API integrity token requests for this app on this device in the last hour",
    ),
    UNEVALUATED(
        description = "Recent device activity was not evaluated. Possible reasons include:\n" +
                "- The device is not trusted enough.\n" +
                "- Google Play does not recognize the installed app version.\n" +
                "- The device has a technical issue.",
    ),
}
