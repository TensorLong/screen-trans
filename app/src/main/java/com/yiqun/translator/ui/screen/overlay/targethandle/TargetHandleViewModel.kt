package com.yiqun.translator.ui.screen.overlay.targethandle

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.Rect
import android.os.Build
import android.view.MotionEvent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeviceUnknown
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.GppMaybe
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yiqun.translator.R
import com.yiqun.translator.data.local.capture.CapturePreventedException
import com.yiqun.translator.data.local.capture.PointedCaptureCrop
import com.yiqun.translator.data.local.capture.CaptureRepository
import com.yiqun.translator.data.local.capture.CaptureResponse
import com.yiqun.translator.data.local.capture.NoMediaProjectionTokenException
import com.yiqun.translator.data.local.preference.PreferenceRepository
import com.yiqun.translator.data.local.screen.ScreenInfoHolder
import com.yiqun.translator.data.local.secure.ApiKeyInfo
import com.yiqun.translator.data.local.secure.DeviceActivityLevel
import com.yiqun.translator.data.local.secure.DeviceInspection
import com.yiqun.translator.data.local.secure.IntegrityResponse
import com.yiqun.translator.data.local.secure.SecureAssessmentInfo
import com.yiqun.translator.data.local.secure.SecureRepository
import com.yiqun.translator.data.local.secure.VerdictAppLicensing
import com.yiqun.translator.data.local.secure.VerdictAppRecognition
import com.yiqun.translator.data.local.secure.VerdictDeviceRecognition
import com.yiqun.translator.data.local.secure.VerdictPlayProtect
import com.yiqun.translator.data.local.tts.TTSRepository
import com.yiqun.translator.data.local.vision.AutoRecognitionPolicy
import com.yiqun.translator.data.local.vision.TextDetectMode
import com.yiqun.translator.data.local.vision.VisionRepository
import com.yiqun.translator.data.local.vision.model.Line
import com.yiqun.translator.data.local.vision.model.Paragraph
import com.yiqun.translator.data.local.vision.model.PointedTextToken
import com.yiqun.translator.data.local.vision.model.SenseGroupVisionText
import com.yiqun.translator.data.local.vision.model.Sentence
import com.yiqun.translator.data.local.vision.model.VisionResponse
import com.yiqun.translator.data.local.vision.model.VisionText
import com.yiqun.translator.data.local.vision.model.Word
import com.yiqun.translator.data.remote.ai.SenseGroupRepository
import com.yiqun.translator.data.remote.ai.chatgpt.SenseGroup
import com.yiqun.translator.data.remote.firebase.AnalyticsRepository
import com.yiqun.translator.data.remote.firebase.RemoteConfigRepository
import com.yiqun.translator.data.remote.translation.Transaction
import com.yiqun.translator.data.remote.translation.TranslationKitType
import com.yiqun.translator.data.remote.translation.TranslationRepository
import com.yiqun.translator.data.remote.translation.TranslationResponse
import com.yiqun.translator.extensions.finishService
import com.yiqun.translator.extensions.gotoStore
import com.yiqun.translator.extensions.openGoogleApp
import com.yiqun.translator.extensions.toPx
import com.yiqun.translator.ui.screen.overlay.dialog.DialogView
import com.yiqun.translator.ui.screen.overlay.menubar.MenuBarView
import com.yiqun.translator.ui.screen.overlay.translation.DismissRunningCommand
import com.yiqun.translator.ui.screen.overlay.translation.TTSStatus
import com.yiqun.translator.ui.screen.overlay.translation.TranslationView
import com.yiqun.translator.ui.screen.overlay.visiontext.VisionTextView
import com.yiqun.translator.ui.screen.permissions.ScreenCapturePermissionRequesterActivity
import getAverageTextBlockHeight
import getBoundingBoxUnion
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import org.json.JSONObject
import timber.log.Timber
import java.util.Locale
import kotlin.math.sqrt


@Suppress("UNCHECKED_CAST")
class TargetHandleViewModelFactory(
    private val applicationContext: Context,
    private val secureRepository: SecureRepository,
    private val remoteConfigRepository: RemoteConfigRepository,
    private val preferenceRepository: PreferenceRepository,
    private val captureRepository: CaptureRepository,
    private val visionRepository: VisionRepository,
    private val senseGroupRepository: SenseGroupRepository,
    private val translationRepository: TranslationRepository,
    private val ttsRepository: TTSRepository,
    private val analyticsRepository: AnalyticsRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TargetHandleViewModel::class.java)) {
            return TargetHandleViewModel(
                applicationContext = applicationContext,
                secureRepository = secureRepository,
                remoteConfigRepository = remoteConfigRepository,
                preferenceRepository = preferenceRepository,
                captureRepository = captureRepository,
                visionRepository = visionRepository,
                senseGroupRepository = senseGroupRepository,
                translationRepository = translationRepository,
                ttsRepository = ttsRepository,
                analyticsRepository = analyticsRepository,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel Class")
    }
}

class TargetHandleViewModel(
    private val applicationContext: Context,
    private val secureRepository: SecureRepository,
    val remoteConfigRepository: RemoteConfigRepository,
    val preferenceRepository: PreferenceRepository,
    val captureRepository: CaptureRepository,
    val visionRepository: VisionRepository,
    private val senseGroupRepository: SenseGroupRepository,
    val translationRepository: TranslationRepository,
    val ttsRepository: TTSRepository,
    val analyticsRepository: AnalyticsRepository,
) : ViewModel() {

    private val TAG = javaClass.simpleName

    private var startTime = System.nanoTime()

    private var endTime = System.nanoTime()

    /**
     */
    val motionEventFlow = MutableStateFlow(MotionEvent.INVALID_POINTER_ID)

    /**
     */
    val pointerPositionFlow = MutableStateFlow<Point?>(null)

    val activePointerSideFlow = MutableStateFlow(PointerSide.LEFT)

    private val pointerOffsetPairFlow = MutableStateFlow(
        PointerOffsetPair.default(preferenceRepository.defaultPointerOffset)
    )

    /**
     */
    val dockStateFlow = MutableStateFlow<Boolean>(false)

    /**
     */
    val visionResultFlow = MutableStateFlow<com.yiqun.translator.data.local.vision.model.Transaction?>(null)

    /**
     */
    val captureStatusFlow = MutableStateFlow(CaptureStatus.Idle)

    /**
     */
    val translateStatusFlow = MutableStateFlow(TranslateStatus.Idle)

    private var captureJob: Job? = null

    private var visionCaptureScreenRect: Rect? = null

    private var ttsAcquiredForTranslation = false


    ////////////////////////////////////////////////////////////////////////////////////////////////
    //                                                                                            //
    //                                         SecureInfo                                         //
    //                                                                                            //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private val warnDelay = 2000L

    private fun collectSecureStateFlow() {
        viewModelScope.launch {
            secureRepository.secureAssessmentInfoFlow
                .filterNotNull()
                .collect { secureAssessmentInfo: SecureAssessmentInfo ->
                    Timber.tag(TAG).d("========= secureAssessmentInfo: $secureAssessmentInfo")

                    /**
                     */
                    if (
                        secureAssessmentInfo.deviceInspection == DeviceInspection.KEYSTORE_NOT_AVAILABLE
                        || secureAssessmentInfo.integrityResponse == IntegrityResponse.UNKNOWN_PACKAGE
                        || secureAssessmentInfo.verdictAppRecognition == VerdictAppRecognition.UNEVALUATED
                        || secureAssessmentInfo.verdictAppRecognition == VerdictAppRecognition.UNRECOGNIZED_VERSION
                        || secureAssessmentInfo.verdictDeviceRecognition == VerdictDeviceRecognition.UNEVALUATED
                        || secureAssessmentInfo.verdictAppLicensing == VerdictAppLicensing.UNEVALUATED
                    ) {
                        Timber.tag(TAG).e("Untrusted device. Play Integrity API will not be requested again on app restart.")
                        Timber.tag(TAG).i("secureAssessmentInfo.deviceInspection ${secureAssessmentInfo.deviceInspection}")
                        Timber.tag(TAG).i("secureAssessmentInfo.integrityResponse ${secureAssessmentInfo.integrityResponse}")
                        Timber.tag(TAG).i("secureAssessmentInfo.verdictAppRecognition ${secureAssessmentInfo.verdictAppRecognition}")
                        Timber.tag(TAG).i("secureAssessmentInfo.verdictAppRecognition ${secureAssessmentInfo.verdictAppRecognition}")
                        Timber.tag(TAG).i("secureAssessmentInfo.verdictDeviceRecognition ${secureAssessmentInfo.verdictDeviceRecognition}")
                        Timber.tag(TAG).i("secureAssessmentInfo.verdictAppLicensing ${secureAssessmentInfo.verdictAppLicensing}")

                        sendSecureAnalytics(secureAssessmentInfo)
                        SecureRepository.VERDICT_APP_RECOGNITION_FAILED = true
                        delay(warnDelay)
                        MenuBarView.INSTANCE.clear()
                        TargetHandleView.clearAll()
                        DialogView.INSTANCE.cast(
                            applicationContext = applicationContext,
                            isGlobalAlerts = true,
                            icon = Icons.Default.DeviceUnknown,
                            dialogTitle = applicationContext.getString(R.string.message_unknown_device),
                            dialogText = applicationContext.getString(R.string.message_unknown_device_detail),
                            onConfirm = { applicationContext.finishService() },
                        )
                    }
                    /**
                     */
                    else if (
                        secureAssessmentInfo.integrityResponse == IntegrityResponse.HTTP_ERROR
                        || secureAssessmentInfo.integrityResponse == IntegrityResponse.FAILED
                    ) {
                        Timber.tag(TAG).e("Communication error. Play Integrity API will be requested again on app restart.")
                        sendSecureAnalytics(secureAssessmentInfo)
                        delay(warnDelay)
                        MenuBarView.INSTANCE.clear()
                        TargetHandleView.clearAll()
                        DialogView.INSTANCE.cast(
                            applicationContext = applicationContext,
                            isGlobalAlerts = true,
                            icon = Icons.Default.GppMaybe,
                            dialogTitle = applicationContext.getString(R.string.message_authentication_error),
                            dialogText = applicationContext.getString(R.string.message_authentication_error_detail),
                            onConfirm = { applicationContext.finishService() },
                        )
                    }
                    /**
                     */
                    else if (
                        secureAssessmentInfo.deviceInspection == DeviceInspection.INTEGRITY_FAILURES_EXCEEDED
                        || secureAssessmentInfo.verdictDeviceRecognition == VerdictDeviceRecognition.MEETS_BASIC_INTEGRITY
                        || secureAssessmentInfo.deviceActivityLevel == DeviceActivityLevel.LEVEL_3
                        || secureAssessmentInfo.deviceActivityLevel == DeviceActivityLevel.LEVEL_4
                        || secureAssessmentInfo.deviceActivityLevel == DeviceActivityLevel.UNEVALUATED
//                        || secureAssessmentInfo.verdictPlayProtect == VerdictPlayProtect.UNEVALUATED
                    ) {
                        Timber.tag(TAG).e("Untrusted environment. Play Integrity API will be requested again on app restart.")
                        sendSecureAnalytics(secureAssessmentInfo)
                        delay(warnDelay)
                        MenuBarView.INSTANCE.clear()
                        TargetHandleView.clearAll()
                        DialogView.INSTANCE.cast(
                            applicationContext = applicationContext,
                            isGlobalAlerts = true,
                            icon = Icons.Default.VpnKey,
                            dialogTitle = applicationContext.getString(R.string.message_untrusted_environment),
                            dialogText = applicationContext.getString(R.string.message_untrusted_environment_detail),
                            onConfirm = {
                                applicationContext.openGoogleApp()
                                applicationContext.finishService()
                            },
                        )
                    }
                    /**
                     */
//                    else if (secureAssessmentInfo.deviceInspection == DeviceInspection.PLAYSTORE_UPDATE_REQUIRED) {
//                        sendSecureAnalytics(secureAssessmentInfo)
//                        delay(warnDelay)
//                        MenuBarView.INSTANCE.clear()
//                        TargetHandleView.INSTANCE.clear()
//                        DialogView.INSTANCE.cast(
//                            applicationContext = applicationContext,
//                            isGlobalAlerts = true,
//                            icon = Icons.Default.Upgrade,
//                            dialogTitle = applicationContext.getString(R.string.message_playstore_update_required),
//                            dialogText = applicationContext.getString(R.string.message_playstore_update_required_detail),
//                            onConfirm = {
//                                applicationContext.playStoreUpdate()
//                                applicationContext.finish()
//                            },
//                        )
//                    }
                    /**
                     */
                    else if (secureAssessmentInfo.verdictAppLicensing == VerdictAppLicensing.UNLICENSED) {
                        Timber.tag(TAG).e("Unknown app source. Play Integrity API will be requested again on app restart.")
                        sendSecureAnalytics(secureAssessmentInfo)
                        delay(warnDelay)
                        MenuBarView.INSTANCE.clear()
                        TargetHandleView.clearAll()
                        DialogView.INSTANCE.cast(
                            applicationContext = applicationContext,
                            isGlobalAlerts = true,
                            painterResource = R.drawable.outline_translate_white_24,
                            dialogTitle = applicationContext.getString(R.string.message_unknown_source),
                            dialogText = applicationContext.getString(R.string.message_unknown_source_detail),
                            onConfirm = {
                                applicationContext.gotoStore(
                                    newTask = true,
                                    finishService = true
                                )
                            },
                        )
                    }
                }
        }
    }

    private fun sendSecureAnalytics(secureAssessmentInfo: SecureAssessmentInfo) {
        if (secureAssessmentInfo.integrityResponse == IntegrityResponse.UNKNOWN_PACKAGE) {
            analyticsRepository.secureReport("IntegrityResponse.UNKNOWN_PACKAGE")
        } else if (secureAssessmentInfo.deviceInspection == DeviceInspection.KEYSTORE_NOT_AVAILABLE) {
            analyticsRepository.secureReport("DeviceInspection.KEYSTORE_NOT_AVAILABLE")
        } else if (secureAssessmentInfo.deviceInspection == DeviceInspection.PLAYSTORE_UPDATE_REQUIRED) {
            analyticsRepository.secureReport("DeviceInspection.PLAYSTORE_UPDATE_REQUIRED")
        } else if (secureAssessmentInfo.deviceInspection == DeviceInspection.INTEGRITY_FAILURES_EXCEEDED) {
            analyticsRepository.secureReport("DeviceInspection.INTEGRITY_FAILURES_EXCEEDED")
        } else if (secureAssessmentInfo.verdictAppRecognition == VerdictAppRecognition.UNEVALUATED) {
            analyticsRepository.secureReport("VerdictAppRecognition.UNEVALUATED")
        } else if (secureAssessmentInfo.verdictAppRecognition == VerdictAppRecognition.UNRECOGNIZED_VERSION) {
            analyticsRepository.secureReport("VerdictAppRecognition.UNRECOGNIZED_VERSION")
        } else if (secureAssessmentInfo.verdictDeviceRecognition == VerdictDeviceRecognition.UNEVALUATED) {
            analyticsRepository.secureReport("VerdictDeviceRecognition.UNEVALUATED")
        } else if (secureAssessmentInfo.verdictAppLicensing == VerdictAppLicensing.UNEVALUATED) {
            analyticsRepository.secureReport("VerdictAppLicensing.UNEVALUATED")
        } else if (secureAssessmentInfo.integrityResponse == IntegrityResponse.HTTP_ERROR) {
            analyticsRepository.secureReport("IntegrityResponse.HTTP_ERROR")
        } else if (secureAssessmentInfo.integrityResponse == IntegrityResponse.FAILED) {
            analyticsRepository.secureReport("IntegrityResponse.FAILED")
        } else if (secureAssessmentInfo.verdictDeviceRecognition == VerdictDeviceRecognition.MEETS_BASIC_INTEGRITY) {
            analyticsRepository.secureReport("VerdictDeviceRecognition.MEETS_BASIC_INTEGRITY")
        } else if (secureAssessmentInfo.deviceActivityLevel == DeviceActivityLevel.LEVEL_3) {
            analyticsRepository.secureReport("DeviceActivityLevel.LEVEL_3")
        } else if (secureAssessmentInfo.deviceActivityLevel == DeviceActivityLevel.LEVEL_4) {
            analyticsRepository.secureReport("DeviceActivityLevel.LEVEL_4")
        } else if (secureAssessmentInfo.deviceActivityLevel == DeviceActivityLevel.UNEVALUATED) {
            analyticsRepository.secureReport("DeviceActivityLevel.UNEVALUATED")
        } else if (secureAssessmentInfo.verdictPlayProtect == VerdictPlayProtect.UNEVALUATED) {
            analyticsRepository.secureReport("VerdictPlayProtect.UNEVALUATED")
        } else if (secureAssessmentInfo.verdictAppLicensing == VerdictAppLicensing.UNLICENSED) {
            analyticsRepository.secureReport("VerdictAppLicensing.UNLICENSED")
        }
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    //                                                                                            //
    //                                       Service Operation                                    //
    //                                                                                            //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private data class RemoteConfig(
        val serviceAvailable: Boolean,
        val latestVersionCode: Long,
        val forceUpdate: Boolean,
        val forceUpdateApiKey: Boolean,
        val apiKeyVersionAzure: Int,
        val apiKeyVersionDeepl: Int,
        val apiKeyVersionPapago: Int,
        val apiKeyVersionYandex: Int,
        val apiKeyVersionChatgpt: Int,
    )

    private val serviceOperationInfoFlow: Flow<RemoteConfig> =
        remoteConfigRepository.remoteConfigFlow
            .filterNotNull()
            .map { remoteConfig ->
                Timber.tag(TAG).i("remoteConfig: $remoteConfig")

                val serviceAvailable: Boolean = remoteConfig[RemoteConfigRepository.SERVICE_AVAILABLE_KEY]?.asString()?.let {
                    val jsonObject = JSONObject(it)
                    Timber.tag(TAG).d("jsonObject: $jsonObject")
                    val defaultServiceAvailable = jsonObject.getBoolean("default")
                    Timber.tag(TAG).d("defaultServiceAvailable: $defaultServiceAvailable")
                    jsonObject.optBoolean(Locale.getDefault().country, defaultServiceAvailable)
                } ?: true
                Timber.tag(TAG).i("serviceAvailable: $serviceAvailable")

                val packageInfo = applicationContext.packageManager.getPackageInfo(applicationContext.packageName, 0)
                val versionCode: Long = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode
                } else {
                    packageInfo.versionCode.toLong()
                }
                Timber.tag(TAG).i("versionCode: $versionCode")
                val forceUpdateVersionCode = remoteConfig[RemoteConfigRepository.FORCE_UPDATE_VERSION_CODE_KEY]?.asLong() ?: 0

                val remoteConfigApiKeyVersionAzure = remoteConfig[RemoteConfigRepository.API_KEY_VERSION_AZURE]?.asLong() ?: 0
                val forceUpdateApiKeyAzure = (ApiKeyInfo.getApiKeyVersionAzure(applicationContext) ?: 1) < remoteConfigApiKeyVersionAzure && ApiKeyInfo.getApiKeyAzure(applicationContext) != null
                val remoteConfigApiKeyVersionDeepl = remoteConfig[RemoteConfigRepository.API_KEY_VERSION_DEEPL]?.asLong() ?: 0
                val forceUpdateApiKeyDeepl = (ApiKeyInfo.getApiKeyVersionDeepl(applicationContext) ?: 1) < remoteConfigApiKeyVersionDeepl && ApiKeyInfo.getApiKeyDeepl(applicationContext) != null
                val remoteConfigApiKeyVersionPapago = remoteConfig[RemoteConfigRepository.API_KEY_VERSION_PAPAGO]?.asLong() ?: 0
                val forceUpdateApiKeyPapago = (ApiKeyInfo.getApiKeyVersionPapago(applicationContext) ?: 1) < remoteConfigApiKeyVersionPapago && ApiKeyInfo.getApiKeyPapago(applicationContext) != null
                val remoteConfigApiKeyVersionYandex = remoteConfig[RemoteConfigRepository.API_KEY_VERSION_YANDEX]?.asLong() ?: 0
                val forceUpdateApiKeyYandex = (ApiKeyInfo.getApiKeyVersionYandex(applicationContext) ?: 1) < remoteConfigApiKeyVersionYandex && ApiKeyInfo.getApiKeyYandex(applicationContext) != null
                val remoteConfigApiKeyVersionChatgpt = remoteConfig[RemoteConfigRepository.API_KEY_VERSION_CHATGPT]?.asLong() ?: 0
                val forceUpdateApiKeyChatgpt =
                    (ApiKeyInfo.getApiKeyVersionChatgpt(applicationContext) ?: 1) < remoteConfigApiKeyVersionChatgpt && ApiKeyInfo.getApiKeyChatgpt(applicationContext) != null

                Timber.tag(TAG).d("remoteConfigApiKeyVersionAzure: $remoteConfigApiKeyVersionAzure")
                Timber.tag(TAG).d("forceUpdateApiKeyAzure: $forceUpdateApiKeyAzure")
                Timber.tag(TAG).d("remoteConfigApiKeyVersionDeepl: $remoteConfigApiKeyVersionDeepl")
                Timber.tag(TAG).d("forceUpdateApiKeyDeepl: $forceUpdateApiKeyDeepl")
                Timber.tag(TAG).d("remoteConfigApiKeyVersionPapago: $remoteConfigApiKeyVersionPapago")
                Timber.tag(TAG).d("forceUpdateApiKeyPapago: $forceUpdateApiKeyPapago")
                Timber.tag(TAG).d("remoteConfigApiKeyVersionYandex: $remoteConfigApiKeyVersionYandex")
                Timber.tag(TAG).d("forceUpdateApiKeyYandex: $forceUpdateApiKeyYandex")
                Timber.tag(TAG).d("remoteConfigApiKeyVersionChatgpt: $remoteConfigApiKeyVersionChatgpt")
                Timber.tag(TAG).d("forceUpdateApiKeyChatgpt: $forceUpdateApiKeyChatgpt")

                RemoteConfig(
                    serviceAvailable = serviceAvailable,
                    latestVersionCode = remoteConfig[RemoteConfigRepository.LATEST_VERSION_CODE_KEY]?.asLong() ?: 0,
                    forceUpdate = versionCode < forceUpdateVersionCode,
                    forceUpdateApiKey = forceUpdateApiKeyAzure || forceUpdateApiKeyDeepl || forceUpdateApiKeyPapago || forceUpdateApiKeyYandex || forceUpdateApiKeyChatgpt,
                    apiKeyVersionAzure = remoteConfigApiKeyVersionAzure.toInt(),
                    apiKeyVersionDeepl = remoteConfigApiKeyVersionDeepl.toInt(),
                    apiKeyVersionPapago = remoteConfigApiKeyVersionPapago.toInt(),
                    apiKeyVersionYandex = remoteConfigApiKeyVersionYandex.toInt(),
                    apiKeyVersionChatgpt = remoteConfigApiKeyVersionChatgpt.toInt(),
                )
            }
            .distinctUntilChanged()

    private fun collectServiceOperationInfoFlow() {
        viewModelScope.launch {
            serviceOperationInfoFlow
                .collect { remoteConfig: RemoteConfig ->
                    Timber.tag(TAG).i("remoteConfig: $remoteConfig")
                    if (!remoteConfig.serviceAvailable) {
                        delay(5000)
                        DialogView.INSTANCE.cast(
                            applicationContext = applicationContext,
                            icon = Icons.Default.Engineering,
                            dialogTitle = applicationContext.getString(R.string.message_service_unavailable),
                            dialogText = applicationContext.getString(R.string.message_service_unavailable_detail),
                            onConfirm = { applicationContext.finishService() }
                        )
                    }
                    else if (remoteConfig.forceUpdate) {
                        delay(5000)
                        DialogView.INSTANCE.cast(
                            applicationContext = applicationContext,
                            icon = Icons.Default.Update,
                            dialogTitle = applicationContext.getString(R.string.message_force_update),
                            dialogText = applicationContext.getString(R.string.message_force_update_detail),
                            onConfirm = { applicationContext.gotoStore(finishService = true) },
                        )
                    }
                    else if (remoteConfig.forceUpdateApiKey) {
                        ApiKeyInfo.setApiKeyAzure(applicationContext, "")
                        ApiKeyInfo.setApiKeyDeepl(applicationContext, "")
                        ApiKeyInfo.setApiKeyPapago(applicationContext, "")
                        ApiKeyInfo.setApiKeyYandex(applicationContext, "")
                        ApiKeyInfo.setApiKeyChatgpt(applicationContext, "")
                        secureRepository.playIntegrity(
                            apiKeyVersionAzure = remoteConfig.apiKeyVersionAzure,
                            apiKeyVersionDeepl = remoteConfig.apiKeyVersionDeepl,
                            apiKeyVersionPapago = remoteConfig.apiKeyVersionPapago,
                            apiKeyVersionYandex = remoteConfig.apiKeyVersionYandex,
                            apiKeyVersionChatgpt = remoteConfig.apiKeyVersionChatgpt,
                            retry = false
                        )
                    }
                }
        }
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    //                                                                                            //
    //                                        preference                                          //
    //                                                                                            //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private var _textDetectMode = TextDetectMode.SENTENCE

    val textDetectMode: TextDetectMode
        get() = _textDetectMode

    private var _dragHandleDocking = true

    val dragHandleDocking: Boolean
        get() = _dragHandleDocking

    private var _dockingDelay = 3000L

    val dockingDelay: Long
        get() = _dockingDelay

    private var ttsSpeechRate = 1.0f

    private fun collectPreference() {
        viewModelScope.launch {
            preferenceRepository.textDetectModeFlow.collect { newValue ->
                _textDetectMode = newValue
            }
        }

        viewModelScope.launch {
            preferenceRepository.dragHandleDockingFlow.collect { newValue ->
                _dragHandleDocking = newValue
            }
        }

        viewModelScope.launch {
            preferenceRepository.dockingDelayFlow.collect { newValue ->
                _dockingDelay = newValue
            }
        }

        viewModelScope.launch {
            preferenceRepository.ttsSpeechRateFlow
                .collect { ttsSpeechRate_ ->
                    ttsSpeechRate = ttsSpeechRate_
                }
        }

        viewModelScope.launch {
            combine(
                preferenceRepository.pointerLeftOffsetFlow,
                preferenceRepository.pointerRightOffsetFlow
            ) { left, right ->
                PointerOffsetPair(left, right)
            }.collect { offsets ->
                pointerOffsetPairFlow.value = offsets
            }
        }
    }

    fun updateTextDetectMode(textDetectMode: TextDetectMode) {
        preferenceRepository.update(PreferenceRepository.TEXT_DETECT_MODE, textDetectMode.name)
    }

    fun updateTranslationKitType(kitType: TranslationKitType) {
        preferenceRepository.update(PreferenceRepository.TRANSLATION_KIT_TYPE, kitType.name)
    }

    fun updatePointerPosition(side: PointerSide, visualPoint: Point) {
        activePointerSideFlow.value = side
        val offsets = pointerOffsetPairFlow.value
        val offset = when (side) {
            PointerSide.LEFT -> offsets.left
            PointerSide.RIGHT -> offsets.right
        }
        pointerPositionFlow.value = PointerCoordinateMapper.toOcrPoint(visualPoint, offset)
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    //                                                                                            //
    //                                      Capture request                                       //
    //                                                                                            //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private fun collectTargetHandleMotionEvent() {
        viewModelScope.launch {
            motionEventFlow
                .filterNotNull()
                .collect { motionEvent ->
                    if (motionEvent == MotionEvent.ACTION_DOWN) {
                        Timber.tag(TAG).i("#### TargetHandle motionEvent MotionEvent.ACTION_DOWN ####")
                        if (isPointedTranslationMode(textDetectMode)) {
                            visionResultFlow.value = null
                            visionCaptureScreenRect = null
                            captureStatusFlow.value = CaptureStatus.Idle
                        }
                    } else if (motionEvent == MotionEvent.ACTION_UP) {
                        Timber.tag(TAG).i("#### TargetHandle motionEvent MotionEvent.ACTION_UP ####")
                        cancelCapture()
                    }
                }
        }
    }

    private fun collectPointerStoppedCaptureRequests() {
        viewModelScope.launch {
            pointerStoppedPositionFlow
                .collectLatest { pointerPosition ->
                    if (pointerPosition == null) {
                        captureJob?.cancel()
                        return@collectLatest
                    }
                    if (!isPointedTranslationMode(textDetectMode)) {
                        return@collectLatest
                    }
                    val motionEventState = motionEventFlow.first()
                    if (motionEventState != MotionEvent.ACTION_DOWN && motionEventState != MotionEvent.ACTION_MOVE) {
                        return@collectLatest
                    }
                    if (visionCaptureScreenRect?.containsPoint(pointerPosition) == true && visionResultFlow.value != null) {
                        return@collectLatest
                    }
                    requestCapture(pointerPosition)
                }
        }
    }

    private fun isPointedTranslationMode(textDetectMode: TextDetectMode): Boolean {
        return textDetectMode == TextDetectMode.WORD ||
                textDetectMode == TextDetectMode.SENTENCE ||
                textDetectMode == TextDetectMode.SENSE_GROUP ||
                textDetectMode == TextDetectMode.PARAGRAPH
    }

    /**
     */
    private fun requestCapture(pointerPosition: Point) {
        startTime = System.nanoTime()
        Timber.tag(TAG).i("#### requestCapture() ####")

        visionResultFlow.value = null
        captureStatusFlow.value = CaptureStatus.Requested

        captureJob?.cancel()
        captureJob = viewModelScope.launch {
            Timber.tag(TAG).d("requestCapture viewModelScope.launch -------------- 0")
            val startDelayMs = RecognitionDelayPolicy.captureStartDelayMs()
            if (startDelayMs > 0) delay(startDelayMs)
            Timber.tag(TAG).d("requestCapture viewModelScope.launch -------------- 1")
            val screenInfo = ScreenInfoHolder.get()
            val cropRect = PointedCaptureCrop.boundsFor(
                screenWidth = screenInfo.width,
                screenHeight = screenInfo.height,
                pointer = pointerPosition,
            )
            val captureResponse: CaptureResponse = TargetCaptureTransparency.withTransparentTargets {
                captureRepository.request(cropRect)
            }
            Timber.tag(TAG).d("requestCapture viewModelScope.launch -------------- 2 $captureResponse")
            if (captureResponse is CaptureResponse.Success) {
                endTime = System.nanoTime()

                Timber.tag(TAG).d("captureResponse.bitmap ${captureResponse.bitmap.width} ${captureResponse.bitmap.height}")

//                 TestCapturedActivity.start(applicationContext, captureResponse.bitmap)

                val motionEventState = motionEventFlow.first()
                Timber.tag(TAG).d("requestCapture motionEventState $motionEventState")
                if (motionEventState == MotionEvent.ACTION_DOWN || motionEventState == MotionEvent.ACTION_MOVE) {
                    captureStatusFlow.value = CaptureStatus.Captured
                    requestVision(captureResponse.bitmap, captureResponse.screenRect, pointerPosition)
                }
            } else if (captureResponse is CaptureResponse.Error) {
                Timber.tag(TAG).d("CaptureResponse.Error ${captureResponse.t.toString()}")
                if (captureResponse.t is NoMediaProjectionTokenException) {
                    captureStatusFlow.value = CaptureStatus.PermissionRequested
                    val intent = Intent(
                        applicationContext,
                        ScreenCapturePermissionRequesterActivity::class.java
                    )
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    applicationContext.startActivity(intent)
                } else if (captureResponse.t is CapturePreventedException) {
                }
            }
        }
    }

    fun cancelCapture() {
        captureJob?.cancel()
        pointerPositionFlow.value = null
        pointerPositionedTranslationFlow.value = null
        if (captureStatusFlow.value != CaptureStatus.PermissionRequested) {
            captureStatusFlow.value = CaptureStatus.Idle
        }
        translateStatusFlow.value = TranslateStatus.Idle
        visionResultFlow.value = null
        visionCaptureScreenRect = null
    }

    fun restartCaptureRepository() {
        captureRepository.restart()
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    //                                                                                            //
    //                                        ml-kit vision                                       //
    //                                                                                            //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     */
    private suspend fun requestVision(capturedBitmap: Bitmap, captureScreenRect: Rect, pointerPosition: Point) {
        startTime = System.nanoTime()
        Timber.tag(TAG).i("#### requestVision() ####")

        val sourceLanguageCode: String = preferenceRepository.sourceLanguageCodeFlow.first()
        val visionResponse: VisionResponse = try {
            var response = visionRepository.request(
                bitmap = capturedBitmap,
                sourceLanguageCode = sourceLanguageCode,
                coordinateOffsetX = captureScreenRect.left,
                coordinateOffsetY = captureScreenRect.top,
                autoRecognitionPolicy = if (sourceLanguageCode == "auto") {
                    AutoRecognitionPolicy.LATIN_FIRST
                } else {
                    AutoRecognitionPolicy.FULL
                },
            )
            if (sourceLanguageCode == "auto" && response.needsFullAutoRetry(pointerPosition)) {
                response = visionRepository.request(
                    bitmap = capturedBitmap,
                    sourceLanguageCode = sourceLanguageCode,
                    coordinateOffsetX = captureScreenRect.left,
                    coordinateOffsetY = captureScreenRect.top,
                    autoRecognitionPolicy = AutoRecognitionPolicy.FULL,
                )
            }
            response
        } finally {
            capturedBitmap.recycle()
        }

        if (visionResponse is VisionResponse.Success) {
            endTime = System.nanoTime()
            val duration = (endTime - startTime) / 1_000_000

//            TestVisionTextActivity.start(
//                applicationContext = applicationContext,
//                capturedBitmap = capturedBitmap,
//                analyzedText = visionResponse.analyzed.first,
//                analyzedParagraphs = visionResponse.analyzed.second,
//                textDetectMode = textDetectMode,
//            )

            val motionEventState = motionEventFlow.first()
            if (motionEventState == MotionEvent.ACTION_DOWN || motionEventState == MotionEvent.ACTION_MOVE) {
                Timber.tag(TAG).i("set visionResult blocks=${visionResponse.result.text.textBlocks.size}")
                visionCaptureScreenRect = captureScreenRect
                visionResultFlow.value = visionResponse.result
            }
        } else if (visionResponse is VisionResponse.Error) {
            Timber.tag(TAG).e("visionResponse err ${visionResponse.t}")
        }
    }

    private fun VisionResponse.needsFullAutoRetry(pointerPosition: Point): Boolean {
        if (this !is VisionResponse.Success) return true
        return !hasPointedGeometry(result, pointerPosition, textDetectMode)
    }

    private fun hasPointedGeometry(
        visionResult: com.yiqun.translator.data.local.vision.model.Transaction,
        pointerPosition: Point,
        textDetectMode: TextDetectMode,
    ): Boolean {
        if (textDetectMode == TextDetectMode.SELECT) {
            return visionResult.text.textBlocks.isNotEmpty()
        }

        val positionedParagraph: Paragraph = visionResult.paragraphs.find { paragraph ->
            paragraph.boundingBox.contains(pointerPosition.x, pointerPosition.y)
        } ?: return false

        if (textDetectMode == TextDetectMode.PARAGRAPH) return true

        if (textDetectMode == TextDetectMode.SENTENCE || textDetectMode == TextDetectMode.SENSE_GROUP) {
            return positionedParagraph.sentences.any { sentence ->
                sentence.boundingPolygon.contains(pointerPosition)
            }
        }

        val positionedLine: Line = positionedParagraph.lines.find { line ->
            expandedRect(line.boundingBox).contains(pointerPosition.x, pointerPosition.y)
        } ?: return false
        return positionedLine.words.any { word ->
            expandedRect(word.boundingBox).contains(pointerPosition.x, pointerPosition.y)
        }
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    //                                                                                            //
    //                                                                                            //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private val POINTER_STOPPED_MARGIN_DISTANCE: Int = applicationContext.resources.getDimensionPixelSize(R.dimen.targethandle_view_pointer_stopped_distance)

    private val POINTER_STOPPED_MARGIN_DURATION: Long
        get() = RecognitionDelayPolicy.pointerStoppedDelayMs(textDetectMode)

    /**
     */
    val pointerStoppedPositionFlow: Flow<Point?> = channelFlow {
        var _pointerPosition: Point? = null
        var lastEmittedPoint: Point? = null
        var timerJob: Job? = null

        // Helper function to calculate distance
        fun calculateDistance(point1: Point, point2: Point): Double {
            val dx = point1.x - point2.x
            val dy = point1.y - point2.y
            return sqrt((dx * dx + dy * dy).toDouble())
        }

        // Cancel the timer job
        fun cancelTimer() {
            timerJob?.cancel()
            timerJob = null
        }

        // Start the timer to emit the position
        fun startTimer(pointerPosition: Point?) {
            cancelTimer() // Cancel any existing timer
            timerJob = launch {
                delay(POINTER_STOPPED_MARGIN_DURATION)
                pointerPosition?.let { currentPoint ->
                    // Emit only if the distance to the last emitted point is greater than the margin
                    val isNotDuplicate = lastEmittedPoint?.let {
                        calculateDistance(currentPoint, it) > POINTER_STOPPED_MARGIN_DISTANCE
                    } ?: true // If lastEmittedPoint is null, it's not a duplicate

                    if (isNotDuplicate) {
                        send(currentPoint) // Emit the position using `send`
                        lastEmittedPoint = currentPoint // Update the last emitted point
                    }
                }
                _pointerPosition = null // Reset for the next emit
                cancelTimer()
            }
        }

        // Combine pointerPositionFlow and motionEventFlow
        combine(pointerPositionFlow, motionEventFlow) { pointerPosition, motionEvent ->
            Pair(pointerPosition, motionEvent)
        }.collectLatest { (pointerPosition, motionEvent) ->
            if (pointerPosition != null &&
                (motionEvent == MotionEvent.ACTION_DOWN || motionEvent == MotionEvent.ACTION_MOVE)
            ) {
                if (_pointerPosition == null) {
                    _pointerPosition = pointerPosition
                    startTimer(pointerPosition) // Start the timer for the first time
                } else {
                    if (calculateDistance(pointerPosition, _pointerPosition!!) <= POINTER_STOPPED_MARGIN_DISTANCE) {
                        // Pointer is within the margin, continue waiting
                    } else {
                        cancelTimer() // Cancel the ongoing timer
                        _pointerPosition = null // Reset the pointer position
                        send(null) // Emit null using `send`
                    }
                }
            } else {
                cancelTimer() // Cancel the timer when pointer is invalid
                _pointerPosition = null
                lastEmittedPoint = null
                send(null) // Emit null using `send`
            }
        }
    }

    /**
     *
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val pointerPositionedVisionTextFlow: Flow<VisionText?> = combine(
        pointerStoppedPositionFlow.filterNotNull(),
        visionResultFlow
    ) { pointerStoppedPosition, visionResult ->
        Pair(pointerStoppedPosition, visionResult)
    }.mapLatest { (pointerStoppedPosition, visionResult) ->
        visionResult?.let {
            getPointerPositionedVisionText(
                visionResult = visionResult,
                pointerPosition = pointerStoppedPosition,
                textDetectMode = textDetectMode
            )
        }
    }.distinctUntilChanged()

    private suspend fun getPointerPositionedVisionText(
        visionResult: com.yiqun.translator.data.local.vision.model.Transaction,
        pointerPosition: Point,
        textDetectMode: TextDetectMode,
    ): VisionText? {
        if (textDetectMode == TextDetectMode.SELECT) {
            val boundingBox = visionResult.text.getBoundingBoxUnion()
            val averageTextBlockHeight = visionResult.text.getAverageTextBlockHeight()
            val writingDirection = visionResult.mostFrequentWritingDirection()
            if (boundingBox != null && averageTextBlockHeight > 0 && writingDirection != null) {
                return Word(
                    boundingBox = boundingBox,
                    representation = visionResult.text.text,
                    writingDirection = writingDirection,
                    chars = emptyList(),
                    presetFontHeight = averageTextBlockHeight
                )
            }
            return null
        }

        val positionedParagraph: Paragraph? = visionResult.paragraphs.find { paragraph ->
            paragraph.boundingBox.contains(pointerPosition.x, pointerPosition.y)
        }
//        Timber.tag(TAG).d("pointerPosition.x [${pointerPosition.x}] pointerPosition.x [${pointerPosition.x}] positionedParagraph [${positionedParagraph?.representation}]")
        if (textDetectMode == TextDetectMode.PARAGRAPH) {
            return positionedParagraph
        }

        if (textDetectMode == TextDetectMode.SENTENCE) {
            positionedParagraph?.sentences?.forEach {
//                Timber.tag(TAG).d("sentence : ${it.boundingBox}, ${it.representation}")
//                it.lines.forEach {
//                    Timber.tag(TAG).i("line : ${it.boundingBox}, ${it.representation}")
//                }
//                Timber.tag(TAG).i(
//                    "boundingPolygon : ${it.boundingPolygon.points}, $pointerPosition ${
//                        it.boundingPolygon.contains(pointerPosition)
//                    }"
//                )
            }
            return positionedParagraph?.sentences?.find { sentence ->
                sentence.boundingPolygon.contains(pointerPosition)
            }
        }

        if (textDetectMode == TextDetectMode.SENSE_GROUP) {
            val positionedSentence: Sentence? = positionedParagraph?.sentences?.find { sentence ->
                sentence.boundingPolygon.contains(pointerPosition)
            }
            return resolveSenseGroupVisionText(
                sentence = positionedSentence,
                pointerPosition = pointerPosition,
                sourceLanguageCode = visionResult.detectedLanguageCode,
            )
        }

        val positionedLine: Line? = positionedParagraph?.lines?.find { line ->
            val expandedRect = expandedRect(line.boundingBox)
            expandedRect.contains(pointerPosition.x, pointerPosition.y)
        }
        val positionedWord: Word? = positionedLine?.words?.find { word ->
            val expandedRect = expandedRect(word.boundingBox)
            expandedRect.contains(pointerPosition.x, pointerPosition.y)
        }
        return positionedWord
    }

    /**
     *
     *
     *
     */
    private suspend fun resolveSenseGroupVisionText(
        sentence: Sentence?,
        pointerPosition: Point,
        sourceLanguageCode: String,
    ): VisionText? {
        if (sentence == null) return null

        val positionedLine: Line? = sentence.lines.find { line ->
            val expandedRect = expandedRect(line.boundingBox)
            expandedRect.contains(pointerPosition.x, pointerPosition.y)
        }
        val positionedWord: Word = positionedLine?.words?.find { word ->
            val expandedRect = expandedRect(word.boundingBox)
            expandedRect.contains(pointerPosition.x, pointerPosition.y)
        } ?: return sentence

        val wordOffset = sentence.wordCharOffset(positionedWord) ?: return sentence
        val pointedToken = PointedTextToken.tokenAt(
            text = positionedWord.representation,
            charOffset = pointedCharOffset(positionedWord, pointerPosition),
        )
        if (pointedToken.text.isBlank()) return sentence
        val tokenOffset = wordOffset + pointedToken.start

        val cached = sentence.senseGroupCache[tokenOffset]
        if (cached != null) {
            Timber.tag(TAG).d("SENSE_GROUP cache hit word=[${pointedToken.text}] chunk=[${cached.text}]")
            return SenseGroupVisionText.from(sentence, cached, positionedWord.boundingBox)
        }

        val sentenceText = sentence.representation
        val wordText = pointedToken.text
        val targetLanguageCode: String = preferenceRepository.targetLanguageCodeFlow.first()

        val group: SenseGroup? = try {
            senseGroupRepository.senseGroupAt(
                word = wordText,
                sentence = sentenceText,
                pointedTokenOffset = tokenOffset,
                sourceLanguageCode = sourceLanguageCode,
                targetLanguageCode = targetLanguageCode,
            )
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "SENSE_GROUP senseGroupAt failed; falling back to sentence")
            null
        }

        if (group == null) {
            return sentence
        }

        sentence.senseGroupCache[tokenOffset] = group
        return SenseGroupVisionText.from(sentence, group, positionedWord.boundingBox)
    }

    private fun pointedCharOffset(word: Word, pointerPosition: Point): Int {
        if (word.chars.isEmpty()) return 0

        val directCharIndex = word.chars.indexOfFirst { char ->
            expandedRect(char.boundingBox).contains(pointerPosition.x, pointerPosition.y)
        }
        if (directCharIndex >= 0) return wordTextOffsetForChar(word, directCharIndex)

        val nearestCharIndex = word.chars
            .withIndex()
            .minByOrNull { (_, char) ->
                val centerXDistance = kotlin.math.abs(char.boundingBox.centerX() - pointerPosition.x)
                val centerYDistance = kotlin.math.abs(char.boundingBox.centerY() - pointerPosition.y)
                centerXDistance + centerYDistance
            }
            ?.index
            ?: 0

        return wordTextOffsetForChar(word, nearestCharIndex)
    }

    private fun wordTextOffsetForChar(word: Word, charIndex: Int): Int {
        val safeIndex = charIndex.coerceIn(0, word.chars.lastIndex)
        var searchFrom = 0
        for (index in 0..safeIndex) {
            val charText = word.chars[index].representation
            val foundAt = word.representation.indexOf(charText, startIndex = searchFrom)
            if (index == safeIndex) {
                return (if (foundAt >= 0) foundAt else searchFrom)
                    .coerceIn(0, word.representation.lastIndex.coerceAtLeast(0))
            }
            searchFrom = if (foundAt >= 0) {
                foundAt + charText.length
            } else {
                searchFrom + charText.length
            }
        }
        return 0
    }

    /**
     */
    private fun expandedRect(rect: Rect, delta: Int = 3.dp.toPx(applicationContext)): Rect {
        return Rect(rect.left, rect.top - delta, rect.right, rect.bottom + delta)
    }

    /**
     */
    private fun collectVisionTextForTranslationView() {
        viewModelScope.launch {
            pointerPositionedVisionTextFlow
                .filterNotNull()
                .collect { pointerPositionedVisionText ->
                    VisionTextView.INSTANCE.cast(applicationContext, pointerPositionedVisionText)

                    visionResultFlow.value?.let { visionResultTransaction ->
                        val translationKitType: TranslationKitType = preferenceRepository.translationKitTypeFlow.first()
                        val targetLanguageCode: String = preferenceRepository.targetLanguageCodeFlow.first()
                        // Timber.tag(TAG).d("kitType $kitType")
                        // Timber.tag(TAG).d("targetLanguageCode $targetLanguageCode")
                        val motionEventState = motionEventFlow.first()
                        if (motionEventState == MotionEvent.ACTION_DOWN || motionEventState == MotionEvent.ACTION_MOVE) {
                            translateStatusFlow.value = TranslateStatus.Requested

                            Timber.tag(TAG).d("sourceText ${pointerPositionedVisionText.representation}")

                            // SENSE_GROUP fast path: use the precomputed chunk translation when available.
                            if (
                                pointerPositionedVisionText is SenseGroupVisionText &&
                                pointerPositionedVisionText.precomputedTranslation.isNotBlank()
                            ) {
                                val chunkText = pointerPositionedVisionText.representation
                                val chunkTranslation = pointerPositionedVisionText.precomputedTranslation
                                Timber.tag(TAG).d("SENSE_GROUP precomputed: [$chunkText] -> [$chunkTranslation]")
                                translateStatusFlow.value = TranslateStatus.Translated
                                val transaction = Transaction(
                                    sourceLanguageCode = visionResultTransaction.detectedLanguageCode,
                                    targetLanguageCode = targetLanguageCode,
                                    sourceText = chunkText,
                                    translationKitType = translationKitType,
                                    detectedLanguageCode = visionResultTransaction.detectedLanguageCode,
                                    resultText = chunkTranslation,
                                )
                                TranslationView.INSTANCE.cast(
                                    applicationContext,
                                    transaction,
                                    pointerPositionedVisionText
                                )
                                pointerPositionedTranslationFlow.value = transaction
                                return@let
                            }

                            Timber.tag(TAG).d("translationKitType $translationKitType")

                            val motionEventState = motionEventFlow.first()
                            if (motionEventState == MotionEvent.ACTION_DOWN || motionEventState == MotionEvent.ACTION_MOVE) {
                                translationRepository.request(
                                    translationKitType,
                                    visionResultTransaction.detectedLanguageCode,
                                    targetLanguageCode,
                                    pointerPositionedVisionText.representation,
                                )
                                    .also {
                                        val motionEventState = motionEventFlow.first()
                                        if (motionEventState == MotionEvent.ACTION_DOWN || motionEventState == MotionEvent.ACTION_MOVE) {
                                            translateStatusFlow.value = TranslateStatus.Translated

                                            when (it) {
                                                is TranslationResponse.Success -> {
                                                    val transaction = Transaction(
                                                        sourceLanguageCode = it.result.sourceLanguageCode,
                                                        targetLanguageCode = it.result.targetLanguageCode,
                                                        sourceText = pointerPositionedVisionText.representation,
                                                        translationKitType = it.result.translationKitType,
                                                        detectedLanguageCode = it.result.detectedLanguageCode,
                                                        resultText = it.result.resultText,
                                                    )
                                                    Timber.tag(TAG).d("translationRepository Translated transaction $transaction")

                                                    TranslationView.INSTANCE.cast(
                                                        applicationContext,
                                                        transaction,
                                                        pointerPositionedVisionText
                                                    )
                                                    pointerPositionedTranslationFlow.value = transaction
                                                }

                                                is TranslationResponse.Error -> {
                                                    Timber.tag(TAG).d("Response Error ${it.t}")
                                                }
                                            }
                                        }
                                    }
                            }
                        }
                    }
                }
        }
    }

    /**
     */
    private val pointerPositionedTranslationFlow = MutableStateFlow<Transaction?>(null)


    ////////////////////////////////////////////////////////////////////////////////////////////////
    //                                                                                            //
    //                                        TranslationState                                    //
    //                                                                                            //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     */
    private val dismissRunningCommandFlow = MutableStateFlow(DismissRunningCommand.RESUME)

    fun resumeDismissRunning() {
        dismissRunningCommandFlow.value = DismissRunningCommand.RESUME
    }

    fun pauseDismissRunning() {
        dismissRunningCommandFlow.value = DismissRunningCommand.PAUSE
    }

    fun rerunDismissRunning() {
        dismissRunningCommandFlow.value = DismissRunningCommand.RERUN
    }

    private data class TranslationStateData(
        val visionText: VisionText?,
        val translation: Transaction?,
        val motionEvent: Int?,
        val ttsStatus: TTSStatus,
        val dismissRunningCommand: DismissRunningCommand
    )

    /**
     * dismiss delay time
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val translationFlow: Flow<Pair<VisionText, Transaction>?> = combine(
        pointerPositionedVisionTextFlow,
        pointerPositionedTranslationFlow,
        motionEventFlow,
        ttsRepository.ttsStatusFlow,
        dismissRunningCommandFlow
    ) { visionText, translation, motionEvent, ttsStatus, dismissRunningCommand ->
        TranslationStateData(visionText, translation, motionEvent, ttsStatus, dismissRunningCommand)
    }.flatMapLatest { (visionText, translation, motionEvent, ttsStatus, dismissRunningCommand) ->
//        Timber.tag(TAG).d("---------------------------------------------------")
//        Timber.tag(TAG).d("motionEvent != MotionEvent.ACTION_UP : $motionEvent ${motionEvent != MotionEvent.ACTION_UP}")
//        Timber.tag(TAG).d("visionText != null : ${visionText != null}")
//        Timber.tag(TAG).d("translation != null : ${translation != null}")
//        Timber.tag(TAG).d("ttsStatus : $ttsStatus")
//        Timber.tag(TAG).d("visionText.representation == translation.sourceText : ${visionText?.representation == translation?.sourceText}")
//        Timber.tag(TAG).d("dismissRunningCommand : $dismissRunningCommand")

        if (motionEvent == null) {
            emptyFlow()
        }

        else if (motionEvent == MotionEvent.ACTION_UP) {
            if (ttsStatus != TTSStatus.Playing) {
                if (dismissRunningCommand == DismissRunningCommand.RESUME) {
                    val translationCloseDelay = preferenceRepository.translationCloseDelayFlow.first()
                    delay(translationCloseDelay)
                    flowOf(null)
                }
                else if (dismissRunningCommand == DismissRunningCommand.PAUSE) {
                    emptyFlow()
                }
                else {
                    resumeDismissRunning()
                    emptyFlow()
                }
            }
            else {
                emptyFlow()
            }
        }

        else {
            if (
                visionText != null &&
                translation != null &&
                visionText.representation == translation.sourceText
            ) {
                if (ttsStatus == TTSStatus.Playing) {
//                    ttsRepository.stopTTS()
                }
                flowOf(Pair(visionText, translation))
            }
            else {
                if (ttsStatus == TTSStatus.Playing) {
                    ttsRepository.stopTTS()
                }
                // null emit.
                flowOf(null)
            }
        }
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    //                                                                                            //
    //                              AreaSelectionView, FixedAreaView                              //
    //                                                                                            //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    val areaSelectingStateFlow = MutableStateFlow(false)


    ////////////////////////////////////////////////////////////////////////////////////////////////
    //                                                                                            //
    //                                             TTS                                            //
    //                                                                                            //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private val pointerPositionDetectedLanguageCodeFlow = pointerPositionedTranslationFlow
        .map { it?.detectedLanguageCode }
        .distinctUntilChanged()

    private fun collectTranslationVoiceFlow() {
        viewModelScope.launch {
            combine(
                preferenceRepository.ttsOrderedVoiceNamesFlow.distinctUntilChanged(),
                pointerPositionDetectedLanguageCodeFlow.filterNotNull().distinctUntilChanged()
            ) { orderedVoiceNames, detectedLanguageCode ->
                Pair(orderedVoiceNames, detectedLanguageCode)
            }
                .collect { (orderedVoiceNames, detectedLanguageCode) ->
                    ensureTtsAcquiredForTranslation()
                    Timber.tag(TAG).i("Ordered Voice Names: $orderedVoiceNames")
                    Timber.tag(TAG).i("Detected Language Code: $detectedLanguageCode")
                    val matchingVoiceName = if (orderedVoiceNames.isEmpty()) {
                        val availableVoices = ttsRepository.availableVoicesFlow
                            .filter { it.isNotEmpty() }
                            .first()
                        availableVoices.map { voice -> voice.name }.firstOrNull { voiceName ->
                            voiceName.startsWith(detectedLanguageCode)
                        }
                    } else {
                        orderedVoiceNames.firstOrNull { voiceName ->
                            voiceName.startsWith(detectedLanguageCode)
                        }
                    }
                    Timber.tag(TAG).i("matchingVoiceName: $matchingVoiceName")
                    matchingVoiceName?.let {
                        ttsRepository.setVoice(matchingVoiceName)
                    }
                }
        }
    }

    private fun ensureTtsAcquiredForTranslation() {
        if (!ttsAcquiredForTranslation) {
            ttsRepository.acquire()
            ttsAcquiredForTranslation = true
        }
    }

    fun playTTS(text: String) {
        ensureTtsAcquiredForTranslation()
        ttsRepository.playTTS(text, ttsSpeechRate)
    }


    fun increaseTranslationUsageCount(): Int {
        return secureRepository.increaseTranslationUsageCount()
    }

    init {
        Timber.tag(TAG).i("#### init ####")
        secureRepository.acquire()
        captureRepository.acquire()
        translationRepository.acquire()
        collectSecureStateFlow()
        collectServiceOperationInfoFlow()
        collectPreference()
        collectTargetHandleMotionEvent()
        collectPointerStoppedCaptureRequests()
        collectVisionTextForTranslationView()
        collectTranslationVoiceFlow()
    }

    override fun onCleared() {
        secureRepository.release()
        captureRepository.release()
        translationRepository.release()
        if (ttsAcquiredForTranslation) {
            ttsRepository.release()
            ttsAcquiredForTranslation = false
        }
        super.onCleared()
    }
}

private fun Rect.containsPoint(point: Point): Boolean {
    return point.x >= left && point.x < right && point.y >= top && point.y < bottom
}
