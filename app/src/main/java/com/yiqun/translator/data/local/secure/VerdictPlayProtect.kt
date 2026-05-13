package com.yiqun.translator.data.local.secure

/*
    environmentDetails: {
        playProtectVerdict: "NO_ISSUES"
    }

    NO_ISSUES
 */
enum class VerdictPlayProtect(
    val description: String,
) {
    NO_ISSUES(
        description = "Play Protect is enabled and found no app issues on the device.",
    ),
    NO_DATA(
        description = "Play Protect is enabled but no scan has been performed yet. The device or Play Store app may have been reset recently.",
    ),
    POSSIBLE_RISK(
        description = "Play Protect is disabled.",
    ),
    MEDIUM_RISK(
        description = "Play Protect is enabled and found potentially harmful apps installed on the device.",
    ),
    HIGH_RISK(
        description = "Play Protect is enabled and found harmful apps installed on the device.",
    ),
    UNEVALUATED(
        description = "The Play Protect verdict was not evaluated.\n" +
                "Possible reasons include:\n" +
                "- The device is not trusted enough.\n" +
                "- Games only: the user account does not have a Play license for the game.",
    ),
}
