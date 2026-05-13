package com.yiqun.translator.data.local.secure

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
        description = "The user is entitled to the app because it was installed or updated through Google Play.",
    ),
    UNLICENSED(
        description = "The user is not entitled to the app, for example because it was sideloaded or not obtained from Google Play.",
    ),
    UNEVALUATED(
        description = "Licensing details were not evaluated because required conditions were not met.\n" +
                "Possible reasons include:\n" +
                "- The device is not trusted enough.\n" +
                "- Google Play does not recognize the installed app version.\n" +
                "- The user is not signed in to Google Play.",
    ),
}
