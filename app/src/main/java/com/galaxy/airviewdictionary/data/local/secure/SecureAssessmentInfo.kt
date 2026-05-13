package com.galaxy.airviewdictionary.data.local.secure

/**
 * Play Integrity API 확인 결과
 */
data class SecureAssessmentInfo(
    val deviceInspection: DeviceInspection? = null,
    val integrityResponse: IntegrityResponse? = null,
    val verdictAppRecognition: VerdictAppRecognition? = null,
    val verdictDeviceRecognition: VerdictDeviceRecognition? = null,
    val deviceActivityLevel: DeviceActivityLevel? = null,
    val verdictAppLicensing: VerdictAppLicensing? = null,
    val verdictPlayProtect: VerdictPlayProtect? = null,
)