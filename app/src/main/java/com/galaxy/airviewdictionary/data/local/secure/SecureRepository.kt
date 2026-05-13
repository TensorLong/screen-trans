package com.galaxy.airviewdictionary.data.local.secure

import android.content.Context
import com.galaxy.airviewdictionary.BuildConfig
import com.galaxy.airviewdictionary.data.AVDRepository
import com.galaxy.airviewdictionary.data.remote.firebase.FireDatabase
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.StandardIntegrityException
import com.google.android.play.core.integrity.StandardIntegrityManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton


/**
 * api 키를 안전하게 공급한다.
 */
@Singleton
class SecureRepository @Inject constructor(@ApplicationContext val context: Context) : AVDRepository() {

    companion object {
        var VERDICT_APP_RECOGNITION_FAILED = false
    }

    private val _secureAssessmentInfoFlow = MutableStateFlow<SecureAssessmentInfo?>(null)

    val secureAssessmentInfoFlow: StateFlow<SecureAssessmentInfo?> get() = _secureAssessmentInfoFlow

    private fun initApiKeyInfo() {
        if (!ApiKeyInfo.apiKeyAvailable(context)) {
            securityAssessment()
        }
    }

    /**
     * 보안 정책 확인
     */
    private fun securityAssessment() {
        // 키스토어 사용가능 여부
        val isSecureStoreSupported = SecureStore.isSupported()
        Timber.tag(TAG).d("SecureStore.isSupported() : $isSecureStoreSupported")

        if (!isSecureStoreSupported) {
            _secureAssessmentInfoFlow.value = SecureAssessmentInfo(
                deviceInspection = DeviceInspection.KEYSTORE_NOT_AVAILABLE,
            )
            return
        }

        launchInAVDCoroutineScope {
            // 이전 판정기록이 있는지 확인
            Timber.tag(TAG).d("VERDICT_APP_RECOGNITION_FAILED : $VERDICT_APP_RECOGNITION_FAILED")
            // 신뢰할수 없는 기기로 판단된 기록이 있는 경우
            if (VERDICT_APP_RECOGNITION_FAILED) {
                _secureAssessmentInfoFlow.value = SecureAssessmentInfo(
                    verdictAppRecognition = VerdictAppRecognition.UNEVALUATED,
                )
            } else {
                // Play Integrity API 요청
                playIntegrity()
            }
        }
    }

    private fun generateRequestHash(): SecureString {
        val uniqueData = StringBuilder()
            .append(context.packageName) // 앱 패키지 이름
            .append(System.currentTimeMillis()) // 현재 시간
            .toString()

        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(uniqueData.toByteArray())
        return SecureString(hashBytes.joinToString("") { "%02x".format(it) }) // 해시 값을 16진수 문자열로 변환
    }

    /**
     * Play Integrity API 요청
     */
    fun playIntegrity(
        apiKeyVersionAzure: Int = 1,
        apiKeyVersionDeepl: Int = 1,
        apiKeyVersionPapago: Int = 1,
        apiKeyVersionYandex: Int = 1,
        apiKeyVersionChatgpt: Int = 1,
        retry: Boolean = false
    ) {
        Timber.tag(TAG).i("=========================== playIntegrity ==========================")

        launchInAVDCoroutineScope {
            try {
                /**
                 * 무결성 토큰 제공자 준비
                 */
                val standardIntegrityManager: StandardIntegrityManager =
                    IntegrityManagerFactory.createStandard(context);

                val requestHash: SecureString = generateRequestHash()
                val token: String = standardIntegrityManager.prepareIntegrityToken(
                    StandardIntegrityManager.PrepareIntegrityTokenRequest
                        .builder()
                        .setCloudProjectNumber(0L) // TODO: Set your Cloud project number
                        .build()
                )
                    .await()
                    .request(
                        StandardIntegrityManager.StandardIntegrityTokenRequest.builder()
                            .setRequestHash(requestHash.get())
                            .build()
                    )
                    .await()
                    .token()
                Timber.tag(TAG).d("requestHash : $requestHash")
                Timber.tag(TAG).d("Integrity Token : $token")

                /**
                 * 서버에 무결성 확인 결과 요청
                 */
                val client = OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build()

                val encodedToken: String = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    // API 26 이상 (java.util.Base64)
                    java.util.Base64.getEncoder().encodeToString(token.toByteArray())
                } else {
                    // API 23~25 (android.util.Base64)
                    android.util.Base64.encodeToString(token.toByteArray(), android.util.Base64.DEFAULT)
                }

                val requestBody = FormBody.Builder()
                    .add("token", encodedToken)
                    .build()

                val url = if (BuildConfig.DEBUG) {
                    "" // TODO: Set your test Play Integrity verification endpoint
                } else {
                    "" // TODO: Set your Play Integrity verification endpoint
                }
                val request = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build()

                val success = try {
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            response.body?.string()?.let { responseBody ->
                                securityAnalysis(
                                    requestHash = requestHash,
                                    playIntegrityResponse = responseBody,
                                    apiKeyVersionAzure = apiKeyVersionAzure,
                                    apiKeyVersionDeepl = apiKeyVersionDeepl,
                                    apiKeyVersionPapago = apiKeyVersionPapago,
                                    apiKeyVersionYandex = apiKeyVersionYandex,
                                    apiKeyVersionChatgpt = apiKeyVersionChatgpt
                                )
                                Timber.tag(TAG).i("Request success !!!!!!")
                                return@use true
                            }
                        }
                        Timber.tag(TAG).e("Request failed: ${response.code}")
                        false
                    }
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "playIntegrity request failed with exception")
                    false
                }

                if (!success) {
                    if (!retry) {
                        delay(5000) // Wait 5 seconds before retrying
                        Timber.tag(TAG).w("playIntegrity failed, retrying... ")
                        playIntegrity(
                            apiKeyVersionAzure = apiKeyVersionAzure,
                            apiKeyVersionDeepl = apiKeyVersionDeepl,
                            apiKeyVersionPapago = apiKeyVersionPapago,
                            apiKeyVersionYandex = apiKeyVersionYandex,
                            apiKeyVersionChatgpt = apiKeyVersionChatgpt,
                            retry = true
                        )
                    } else {
                        _secureAssessmentInfoFlow.value = SecureAssessmentInfo(
                            deviceInspection = DeviceInspection.INTEGRITY_FAILURES_EXCEEDED,
                        )
                    }
                }
            } catch (e: StandardIntegrityException) {
                _secureAssessmentInfoFlow.value = SecureAssessmentInfo(
                    deviceInspection = DeviceInspection.PLAYSTORE_UPDATE_REQUIRED,
                )
            }
        }
    }

    /**
     * 각 평가항목 분석
     */
    private fun securityAnalysis(
        requestHash: SecureString,
        playIntegrityResponse: String,
        apiKeyVersionAzure: Int = 1,
        apiKeyVersionDeepl: Int = 1,
        apiKeyVersionPapago: Int = 1,
        apiKeyVersionYandex: Int = 1,
        apiKeyVersionChatgpt: Int = 1,
    ): Boolean {
        val json = JSONObject(playIntegrityResponse)
        Timber.tag(TAG).d("Response JSON: $json")

        /*
            {
                  "status": "SUCCESS",
                  "playIntegrity": {
                        "appIntegrity": {
                              "appRecognitionVerdict": "PLAY_RECOGNIZED",
                              "packageName": "com.galaxy.airviewdictionary",
                              "certificateSha256Digest": [
                                    "-qf3x-r86ALLMIQCTwkL4DceVnATUEoiBlia1yn0luM"
                              ],
                              "versionCode": "19301"
                        },
                        "deviceIntegrity": {
                              "deviceRecognitionVerdict": [
                                    "MEETS_BASIC_INTEGRITY",
                                    "MEETS_DEVICE_INTEGRITY",
                                    "MEETS_STRONG_INTEGRITY"
                              ],
                              "recentDeviceActivity": {
                                    "deviceActivityLevel": "LEVEL_1"
                              },
                              "deviceAttributes": {
                                    "sdkVersion": 33
                              }
                        },
                        "accountDetails": {
                            "appLicensingVerdict": "LICENSED"
                        },
                        "environmentDetails": {
                            "playProtectVerdict": "NO_ISSUES"
                        }
                  },
                  "apiKeyChatgpt": "wdA+nZ880qM3ZpAyC3HLycbdJSbW0fq61NiQuPy7pu7UIngvFTIigK1Drv+uP+XxCMWEKF6KYz+jRJYqQvV7fNSeon+5PNyNozr1P6vdR69kCwyDcSz2wxk+LiSpzYHqVeC7o5KSMbXUNa95c4eaqF6SaESs+I9Bc50Ci7UIpjmyXsQ8O+ecSMOfZZC96hjvG8rhE/wl7aOF/GCIwo9E7qn1Mb48/83t2FzyHPPbRWo\u003d",
                  "apiKeyPapago": "urpxGZHvke8866ODbtA3UGIaVB2hCRBXkWtgEcSVIhawEagAIg4Z7Ir4sQDMHu3tbxnsoumoecTwBJEEPGd05Q\u003d\u003d",
                  "apiKeyAzure": "J6BYvtMj9m1125gHLm6jnx3jEyhlEmtSDRP63Xq0r8R0xTAxntx5bs4ndJotVYvyMjEsJzkZrMCuK/pgHqiClaKLuarHyqgzM7AIO5Ri/mRlv4TRoFltVe60k91Rba49",
                  "apiKeyYandex": "i1V3u/RwOLU5mQkRaxacXWJV+oygWh5iAy9yD3Vc719JPVuimRFrLKMjzj3OW3b2",
                  "apiKeyDeepl": "Oqo4cKFcFeg0scAQQCU4fOi7ZhJ/O/Fq8/R9uk5fZkXktKo571PBySxrwkTfNMBZ"
            }
         */

        val status: String = json.getString("status")
        Timber.tag(TAG).d("status: $status")
        if (status != "SUCCESS") {
            _secureAssessmentInfoFlow.value = SecureAssessmentInfo(
                integrityResponse = IntegrityResponse.entries.find { it.name == status } ?: IntegrityResponse.FAILED
            )
            return false
        }

        val playIntegrity: JSONObject = json.getJSONObject("playIntegrity")

        // verdictAppRecognition
        val appIntegrity: JSONObject = playIntegrity.getJSONObject("appIntegrity")
        val verdictAppRecognition: VerdictAppRecognition = VerdictAppRecognition.valueOf(appIntegrity.optString("appRecognitionVerdict", "UNEVALUATED"))
        Timber.tag(TAG).d("verdictAppRecognition: $verdictAppRecognition")

        // verdictDeviceRecognition, deviceActivityLevel
        val deviceIntegrity: JSONObject = playIntegrity.getJSONObject("deviceIntegrity")
        val deviceRecognitionVerdicts: List<String> = deviceIntegrity.optJSONArray("deviceRecognitionVerdict")
            ?.let { array -> List(array.length()) { index -> array.getString(index) } }
            ?: emptyList()
        Timber.tag(TAG).d("deviceRecognitionVerdicts: $deviceRecognitionVerdicts")
        Timber.tag(TAG).d("deviceRecognitionVerdicts.contains(\"MEETS_DEVICE_INTEGRITY\"): ${deviceRecognitionVerdicts.contains("MEETS_DEVICE_INTEGRITY")}")
        Timber.tag(TAG).d("deviceRecognitionVerdicts.contains(\"MEETS_STRONG_INTEGRITY\"): ${deviceRecognitionVerdicts.contains("MEETS_STRONG_INTEGRITY")}")
        Timber.tag(TAG).d("deviceRecognitionVerdicts.contains(\"MEETS_BASIC_INTEGRITY\"): ${deviceRecognitionVerdicts.contains("MEETS_BASIC_INTEGRITY")}")

        val verdictDeviceRecognition: VerdictDeviceRecognition =
            if (deviceRecognitionVerdicts.contains("MEETS_DEVICE_INTEGRITY")) {
                VerdictDeviceRecognition.MEETS_DEVICE_INTEGRITY
            } else if (deviceRecognitionVerdicts.contains("MEETS_STRONG_INTEGRITY")) {
                VerdictDeviceRecognition.MEETS_STRONG_INTEGRITY
            } else if (deviceRecognitionVerdicts.contains("MEETS_BASIC_INTEGRITY")) {
                VerdictDeviceRecognition.MEETS_BASIC_INTEGRITY
            } else {
                VerdictDeviceRecognition.UNEVALUATED
            }
        Timber.tag(TAG).d("verdictDeviceRecognition: $verdictDeviceRecognition")

        val recentDeviceActivity: JSONObject = deviceIntegrity.getJSONObject("recentDeviceActivity")
        Timber.tag(TAG).d("recentDeviceActivity: $recentDeviceActivity")

        val deviceActivityLevel: DeviceActivityLevel = DeviceActivityLevel.valueOf(recentDeviceActivity.optString("deviceActivityLevel", "UNEVALUATED"))
        Timber.tag(TAG).d("deviceActivityLevel: $deviceActivityLevel")

        // verdictAppLicensing
        val accountDetails: JSONObject = playIntegrity.getJSONObject("accountDetails")
        val verdictAppLicensing: VerdictAppLicensing = VerdictAppLicensing.valueOf(accountDetails.optString("appLicensingVerdict", "UNEVALUATED"))
        Timber.tag(TAG).d("verdictAppLicensing: $verdictAppLicensing")

        // verdictPlayProtect
        val environmentDetails: JSONObject = playIntegrity.getJSONObject("environmentDetails")
        val verdictPlayProtect: VerdictPlayProtect = VerdictPlayProtect.valueOf(environmentDetails.optString("playProtectVerdict", "UNEVALUATED"))
        Timber.tag(TAG).d("verdictPlayProtect: $verdictPlayProtect")

        _secureAssessmentInfoFlow.value = SecureAssessmentInfo(
            verdictAppRecognition = verdictAppRecognition,
            verdictDeviceRecognition = verdictDeviceRecognition,
            deviceActivityLevel = deviceActivityLevel,
            verdictAppLicensing = verdictAppLicensing,
            verdictPlayProtect = verdictPlayProtect,
        )

        if (
            verdictAppRecognition == VerdictAppRecognition.PLAY_RECOGNIZED
            && (verdictDeviceRecognition == VerdictDeviceRecognition.MEETS_DEVICE_INTEGRITY || verdictDeviceRecognition == VerdictDeviceRecognition.MEETS_STRONG_INTEGRITY)
            && (deviceActivityLevel == DeviceActivityLevel.LEVEL_1 || deviceActivityLevel == DeviceActivityLevel.LEVEL_2)
            && verdictAppLicensing == VerdictAppLicensing.LICENSED
            && verdictPlayProtect == VerdictPlayProtect.NO_ISSUES
        ) {
            Timber.tag(TAG).d("apiKeyAzure: ${decryptReceivedApiKey(requestHash.get(), json.optString("apiKeyAzure", "Unknown"))}")
            Timber.tag(TAG).d("apiKeyDeepl: ${decryptReceivedApiKey(requestHash.get(), json.optString("apiKeyDeepl", "Unknown"))}")
            Timber.tag(TAG).d("apiKeyPapago: ${decryptReceivedApiKey(requestHash.get(), json.optString("apiKeyPapago", "Unknown"))}")
            Timber.tag(TAG).d("apiKeyYandex: ${decryptReceivedApiKey(requestHash.get(), json.optString("apiKeyYandex", "Unknown"))}")
            Timber.tag(TAG).d("apiKeyChatgpt: ${decryptReceivedApiKey(requestHash.get(), json.optString("apiKeyChatgpt", "Unknown"))}")
            ApiKeyInfo.setApiKeyAzure(context, decryptReceivedApiKey(requestHash.get(), json.optString("apiKeyAzure", "Unknown")))
            ApiKeyInfo.setApiKeyVersionAzure(context, apiKeyVersionAzure)
            ApiKeyInfo.setApiKeyDeepl(context, decryptReceivedApiKey(requestHash.get(), json.optString("apiKeyDeepl", "Unknown")))
            ApiKeyInfo.setApiKeyVersionDeepl(context, apiKeyVersionDeepl)
            ApiKeyInfo.setApiKeyPapago(context, decryptReceivedApiKey(requestHash.get(), json.optString("apiKeyPapago", "Unknown")))
            ApiKeyInfo.setApiKeyVersionPapago(context, apiKeyVersionPapago)
            ApiKeyInfo.setApiKeyYandex(context, decryptReceivedApiKey(requestHash.get(), json.optString("apiKeyYandex", "Unknown")))
            ApiKeyInfo.setApiKeyVersionYandex(context, apiKeyVersionYandex)
            ApiKeyInfo.setApiKeyChatgpt(context, decryptReceivedApiKey(requestHash.get(), json.optString("apiKeyChatgpt", "Unknown")))
            ApiKeyInfo.setApiKeyVersionChatgpt(context, apiKeyVersionChatgpt)
        } else if (
            verdictAppRecognition == VerdictAppRecognition.UNEVALUATED
            || verdictAppRecognition == VerdictAppRecognition.UNRECOGNIZED_VERSION
            || verdictDeviceRecognition == VerdictDeviceRecognition.UNEVALUATED
            || verdictAppLicensing == VerdictAppLicensing.UNEVALUATED
        ) {
            FireDatabase.badIntegrityReport(
                verdictAppRecognition = verdictAppRecognition,
                verdictDeviceRecognition = verdictDeviceRecognition,
                deviceActivityLevel = deviceActivityLevel,
                verdictAppLicensing = verdictAppLicensing,
                verdictPlayProtect = verdictPlayProtect
            )
        }
        return true
    }

    private fun decryptReceivedApiKey(key: String, encryptedData: String): String {
        val keyBytes = key.toByteArray()
        val secretKey = SecretKeySpec(keyBytes, 0, 16, "AES")
        val cipher = Cipher.getInstance("AES")
        cipher.init(Cipher.DECRYPT_MODE, secretKey)
        val encryptedBytes: ByteArray = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            java.util.Base64.getDecoder().decode(encryptedData)
        } else {
            android.util.Base64.decode(encryptedData, android.util.Base64.DEFAULT)
        }
        val originalBytes = cipher.doFinal(encryptedBytes)
        return String(originalBytes)
    }

    fun increaseTrialCount(): Int {
        val trialCount = getTrialCount() + 1
        SecureStore.set(context, SecureStoreKey.TRANSLATE_TRIAL_COUNT, trialCount.toString())
        return trialCount
    }

    fun getTrialCount(): Int {
        return SecureStore.get(context, SecureStoreKey.TRANSLATE_TRIAL_COUNT)?.get()?.toInt() ?: 0
    }

    init {
        Timber.tag(TAG).i("=========================== SecureRepository ==========================")
        // api key 정보
        initApiKeyInfo()
    }

    override fun onZeroReferences() {

    }
}







