package com.yiqun.translator.ui.screen.overlay.fixedarea


import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.Rect
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.SignalWifiStatusbarConnectedNoInternet4
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yiqun.translator.R
import com.yiqun.translator.core.OverlayService
import com.yiqun.translator.data.local.capture.CapturePreventedException
import com.yiqun.translator.data.local.capture.CaptureResponse
import com.yiqun.translator.data.local.capture.NoMediaProjectionTokenException
import com.yiqun.translator.data.local.screen.ScreenInfo
import com.yiqun.translator.data.local.screen.ScreenInfoHolder
import com.yiqun.translator.data.local.vision.TextDetectMode
import com.yiqun.translator.data.local.vision.model.Transaction
import com.yiqun.translator.data.local.vision.model.VisionResponse
import com.yiqun.translator.data.remote.translation.TranslationKitType
import com.yiqun.translator.data.remote.translation.TranslationResponse
import com.yiqun.translator.data.remote.translation.TranslationSourcePolicy
import com.yiqun.translator.extensions.setFromPoints
import com.yiqun.translator.extensions.vibrate
import com.yiqun.translator.ui.screen.main.SettingsActivity
import com.yiqun.translator.ui.screen.overlay.Event
import com.yiqun.translator.ui.screen.overlay.OverlayView
import com.yiqun.translator.ui.screen.overlay.dialog.DialogView
import com.yiqun.translator.ui.screen.overlay.selection.createOverlaidBitmap
import com.yiqun.translator.ui.screen.overlay.targethandle.TargetCaptureTransparency
import com.yiqun.translator.ui.screen.overlay.targethandle.TargetHandleView
import com.yiqun.translator.ui.screen.overlay.targethandle.TargetHandleViewModel
import com.yiqun.translator.ui.screen.permissions.ScreenCapturePermissionRequesterActivity
import com.yiqun.translator.ui.screen.overlay.selection.AreaCapturePolicy
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Singleton


/**
 */
@Singleton
open class FixedAreaView : OverlayView() {

    enum class State {
        Idle,
        Handling,
        Translating,
        TranslatingHandling,
    }

    companion object {
        val INSTANCE: FixedAreaView by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { FixedAreaView() }

        val fixedAreaViewStateFlow = MutableStateFlow(State.Idle)

        val translationFlow = MutableStateFlow("")

        private const val SELECTION_AREA_RATIO_LIMIT: Int = 50

        private const val BACKGROUND_ALPHA: Float = 0.28f

        private const val BACKGROUND_FADE_OUT_ALPHA: Float = 0.0f

        private const val BACKGROUND_FADE_OUT_DURATION: Int = 8000
    }

    private lateinit var targetHandleViewModel: TargetHandleViewModel

    override lateinit var layoutParams: WindowManager.LayoutParams

    private lateinit var layoutTopCenter: Point

    private var fixedAreaViewStateFlowJob: Job? = null

    private var translateJob: Job? = null

    private var fixedAreaViewBackgroundAlphaJob: Job? = null

    override val composable: @Composable () -> Unit = @Composable {
        val localView = LocalView.current
        val context = LocalContext.current
        val resources = context.resources
        val lifecycleOwner = LocalLifecycleOwner.current
        val composeScope = rememberCoroutineScope()

        val isDarkMode = isSystemInDarkTheme()
        val fixedAreaViewColor = colorResource(if (isDarkMode) R.color.fixed_area_color else R.color.fixed_area_color_dark)
        val fixedAreaViewBackgroundAlpha = remember { Animatable(BACKGROUND_ALPHA) }

        val stoppedDistancePx = with(resources.displayMetrics) {
            resources.getDimensionPixelSize(R.dimen.targethandle_view_pointer_stopped_distance)
        }
        val selectionMinWidthPx = with(resources.displayMetrics) {
            resources.getDimensionPixelSize(R.dimen.area_selection_min_width)
        }
        val selectionMinHeightPx = with(resources.displayMetrics) {
            resources.getDimensionPixelSize(R.dimen.area_selection_min_height)
        }

        val textDetectMode by targetHandleViewModel.preferenceRepository.textDetectModeFlow.collectAsStateWithLifecycle(
            lifecycle = lifecycleOwner.lifecycle,
            initialValue = TextDetectMode.FIXED_AREA
        )

        if (textDetectMode != TextDetectMode.FIXED_AREA) {
            clear()
        }

        val dragHandleHaptic by targetHandleViewModel.preferenceRepository.dragHandleHapticFlow.collectAsStateWithLifecycle(
            lifecycle = lifecycleOwner.lifecycle,
            initialValue = false
        )

        fun haptic() {
            if (dragHandleHaptic) {
                context.vibrate()
            }
        }

        val selectionStarted = remember { mutableStateOf(true) }
        val selectedCompleted = remember { mutableStateOf(false) }
        val selectionAreaRatio = remember { mutableIntStateOf(0) }
        val selectedArea = remember { mutableStateOf(Rect()) }

        val pointerStoppedPosition: Point? by targetHandleViewModel.pointerStoppedPositionFlow.collectAsStateWithLifecycle(
            lifecycle = lifecycleOwner.lifecycle,
            initialValue = null
        )

        val fixedAreaViewState: State by fixedAreaViewStateFlow.collectAsStateWithLifecycle()

        val settingsActivityLiveState by SettingsActivity.liveStateFlow.collectAsStateWithLifecycle()

        fun startSelection() {
            FixedAreaTranslationView.INSTANCE.clear()
            fixedAreaViewStateFlowJob?.cancel()
            fixedAreaViewStateFlow.value = State.Idle
            translateJob?.cancel()
            fixedAreaViewBackgroundAlphaJob?.cancel()
            composeScope.launch {
                fixedAreaViewBackgroundAlpha.snapTo(BACKGROUND_ALPHA)
            }
            selectionStarted.value = true
            targetHandleViewModel.areaSelectingStateFlow.value = true
            translationFlow.value = ""
            haptic()
        }

        fun resetSelection(currentPosition: Point) {
            targetHandleViewModel.cancelCapture()

            selectedCompleted.value = false
            targetHandleViewModel.areaSelectingStateFlow.value = false
            translationFlow.value = ""
            selectionStarted.value = false

            layoutParams.x = currentPosition.x
            layoutParams.y = currentPosition.y
            // Collapse the window to a point; width/height previously reused the x/y
            // coordinates as sizes, leaving a screen-spanning invisible window behind.
            layoutParams.width = 1
            layoutParams.height = 1
            updateLayout(context)

            view?.post {
                layoutTopCenter = currentPosition
            }
        }

        fun completeSelection(_selectedArea: Rect) {
            selectedCompleted.value = true
            selectedArea.value = _selectedArea
            targetHandleViewModel.areaSelectingStateFlow.value = false
            if (selectionAreaRatio.intValue in 5..SELECTION_AREA_RATIO_LIMIT) {
                haptic()
            } else if (selectionAreaRatio.intValue > SELECTION_AREA_RATIO_LIMIT) {
                clear()
            }
        }

        LaunchedEffect(pointerStoppedPosition) {
            // Timber.tag(TAG).d("LaunchedEffect pointerStoppedPosition $pointerStoppedPosition")

            if (pointerStoppedPosition == null) {
                return@LaunchedEffect
            }

            if (!selectionStarted.value) {
                startSelection()
                return@LaunchedEffect
            }

            // Selected Area
            val r: Rect = Rect().apply {
                setFromPoints(layoutTopCenter, pointerStoppedPosition!!)
            }
            if ((r.width() > selectionMinWidthPx && r.height() > selectionMinHeightPx)
                || (r.width() > selectionMinHeightPx && r.height() > selectionMinWidthPx)
            ) {
                completeSelection(r)
            }
        }

        val pointerPosition by targetHandleViewModel.pointerPositionFlow.collectAsStateWithLifecycle()
        val _pointerPosition = remember { mutableStateOf<Point?>(null) }

        pointerPosition?.let { currentPosition ->
            if (currentPosition.y + stoppedDistancePx / 2 < layoutTopCenter.y
                || currentPosition.x + stoppedDistancePx / 2 < layoutTopCenter.x
            ) {
                resetSelection(currentPosition)
            }
            else {
                if (selectionStarted.value) {
                    if (_pointerPosition.value != null) {
                        if (currentPosition.y + stoppedDistancePx / 2 < _pointerPosition.value!!.y
                            || currentPosition.x + stoppedDistancePx / 2 < _pointerPosition.value!!.x
                        ) {
                            resetSelection(currentPosition)
                        }
                    }
                }
                else {
                    layoutTopCenter = currentPosition
                }
            }

            _pointerPosition.value = currentPosition

            val r: Rect = Rect().apply {
                setFromPoints(layoutTopCenter, currentPosition)
            }

            layoutParams.x = r.left
            layoutParams.y = r.top
            layoutParams.width = r.width()
            layoutParams.height = r.height()

            val area = r.width() * r.height()
            val screenInfo: ScreenInfo = ScreenInfoHolder.get()
            val percentage = (area.toDouble() / screenInfo.safeArea.toDouble()) * 100
            Timber.tag(TAG).d("screenInfo $screenInfo    area $area  percentage $percentage")
            selectionAreaRatio.intValue = percentage.toInt()
            updateLayout(context)
        }

        fun fixedAreaVisible() {
            if (fixedAreaViewStateFlow.value == State.Translating || fixedAreaViewStateFlow.value == State.TranslatingHandling) {
                fixedAreaViewStateFlowJob?.cancel()
                fixedAreaViewStateFlow.value = State.TranslatingHandling
                fixedAreaViewStateFlowJob = launchInOverlayViewCoroutineScope {
                    delay(BACKGROUND_FADE_OUT_DURATION.toLong())
                    fixedAreaViewStateFlow.value = State.Translating
                }

                fixedAreaViewBackgroundAlphaJob?.cancel()
                fixedAreaViewBackgroundAlphaJob = composeScope.launch {
                    fixedAreaViewBackgroundAlpha.snapTo(BACKGROUND_ALPHA)
                    fixedAreaViewBackgroundAlpha.animateTo(
                        targetValue = BACKGROUND_FADE_OUT_ALPHA,
                        animationSpec = tween(durationMillis = BACKGROUND_FADE_OUT_DURATION)
                    )
                }
            }
        }

        LaunchedEffect(settingsActivityLiveState) {
            if (settingsActivityLiveState) {
                fixedAreaVisible()
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(fixedAreaViewColor.copy(alpha = fixedAreaViewBackgroundAlpha.value))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    fixedAreaVisible()
                },
            contentAlignment = Alignment.Center,
        ) {
            if (fixedAreaViewState == State.Idle || fixedAreaViewState == State.Handling) {
                if (selectionAreaRatio.intValue <= SELECTION_AREA_RATIO_LIMIT) {
                    IconButton(
                        onClick = {
                            fun start() {
                                startFixedAreaTranslate(context, selectedArea.value)
                                launchInOverlayViewCoroutineScope {
                                    TargetHandleView.INSTANCE.cast(context, true)
                                    FixedAreaTranslationView.INSTANCE.cast(context, selectedArea.value)
                                }

                                fixedAreaViewBackgroundAlphaJob?.cancel()
                                fixedAreaViewBackgroundAlphaJob = composeScope.launch {
                                    fixedAreaViewBackgroundAlpha.animateTo(
                                        targetValue = BACKGROUND_FADE_OUT_ALPHA,
                                        animationSpec = tween(durationMillis = BACKGROUND_FADE_OUT_DURATION)
                                    )
                                }
                            }

                            launchInOverlayViewCoroutineScope {
                                DialogView.INSTANCE.cast(
                                    applicationContext = context,
                                    icon = Icons.Default.BatteryAlert,
                                    dialogTitle = context.getString(R.string.message_translate_fixedarea_warn),
                                    dialogText = context.getString(R.string.message_translate_fixedarea_warn_detail),
                                    onConfirm = {
                                        launchInOverlayViewCoroutineScope {
                                            start()
                                        }
                                    }
                                )
                            }
                        },
                        modifier = Modifier
                            .size(96.dp)
                            .padding(1.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PlayCircleOutline,
                            contentDescription = "start translation",
                            tint = fixedAreaViewColor,
                            modifier = Modifier
                                .size(52.dp)
                                .padding(2.dp)
                        )
                    }
                }
            }
        }
    }

    override fun onServiceConnected(overlayService: OverlayService) {
        targetHandleViewModel = overlayService.getTargetHandleViewModel()
        super.onServiceConnected(overlayService)
    }

    open suspend fun cast(
        applicationContext: Context,
        startPosition: Point,
    ) {
        this.layoutTopCenter = startPosition
        layoutParams = WindowManager.LayoutParams(
            1,
            1,
            startPosition.x,
            startPosition.y,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
        super.cast(applicationContext)
        targetHandleViewModel.areaSelectingStateFlow.value = true
    }

    override fun onOverlayServiceEvent(overlayService: OverlayService, event: Event) {
        when (event) {
            Event.ConfigurationChanged -> {
                clear()
            }

            else -> {}
        }
        super.onOverlayServiceEvent(overlayService, event)
    }

    override fun clear() {
        FixedAreaTranslationView.INSTANCE.clear()
        fixedAreaViewBackgroundAlphaJob?.cancel()
        fixedAreaViewBackgroundAlphaJob = null
        fixedAreaViewStateFlowJob?.cancel()
        fixedAreaViewStateFlowJob = null
        fixedAreaViewStateFlow.value = State.Idle
        translateJob?.cancel()
        translateJob = null
        targetHandleViewModel.areaSelectingStateFlow.value = false
        super.clear()
    }

    private fun startFixedAreaTranslate(context: Context, selectedArea: Rect) {
        translateJob?.cancel()
        fixedAreaViewStateFlowJob?.cancel()
        fixedAreaViewStateFlow.value = State.Translating
        translateJob = launchInOverlayViewCoroutineScope {
            while (fixedAreaViewStateFlow.value == State.Translating || fixedAreaViewStateFlow.value == State.TranslatingHandling) {
                requestVision(context, selectedArea)
                delay(FixedAreaRecognitionPolicy.pollingDelayMs())
            }
        }
    }

    private var detectedString = ""

    private suspend fun requestVision(context: Context, selectedArea: Rect) {
        // Hide the shared target pointer (the real OCR contaminant — same as pointed mode)
        // and gate the capture on the overlay-hidden frame being committed.
        // The fixed-area dimming overlay is deliberately NOT hidden here: it is a uniform
        // translucent wash (OCR-benign, unlike the pointer) that the app already fades out
        // on its own, and requestVision runs in a tight polling loop — hiding it every poll
        // would turn that fade-out into a strobe.
        val captureResponse: CaptureResponse = TargetCaptureTransparency.withTransparentTargetsAfterCommit { minimumImageTimestampNs ->
            targetHandleViewModel.captureRepository.request(
                AreaCapturePolicy.captureRect(selectedArea),
                minimumImageTimestampNs,
            )
        }
        Timber.tag(TAG).d("captureResponse $captureResponse")
        if (captureResponse !is CaptureResponse.Success) {
            Timber.tag(TAG).d("CaptureResponse.Error ${(captureResponse as CaptureResponse.Error).t}")
            if (captureResponse.t is NoMediaProjectionTokenException) {
                val intent = Intent(context, ScreenCapturePermissionRequesterActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                clear()
            } else if (captureResponse.t is CapturePreventedException) {
            }
            return
        }

        val selectedAreaBitmap = captureResponse.bitmap

        // TestCapturedActivity.start(context, selectedAreaBitmap)

        val sourceLanguageCode: String = targetHandleViewModel.preferenceRepository.sourceLanguageCodeFlow.first()
        val visionResponse: VisionResponse = try {
            targetHandleViewModel.visionRepository.request(
                bitmap = selectedAreaBitmap,
                sourceLanguageCode = sourceLanguageCode,
                coordinateOffsetX = AreaCapturePolicy.coordinateOffsetX(captureResponse.screenRect),
                coordinateOffsetY = AreaCapturePolicy.coordinateOffsetY(captureResponse.screenRect),
            )
        } finally {
            selectedAreaBitmap.recycle()
        }

        if (visionResponse !is VisionResponse.Success) {
            return
        }

        val visionResponseString = visionResponse.result.text.text.replace("\n", " ")
//        Timber.tag(TAG).d("[visionResponseString] [$visionResponseString]")
        if (detectedString == visionResponseString) {
            return
        }

        detectedString = visionResponseString
        Timber.tag(TAG).d("[detectedString] $detectedString")
        requestTranslate(visionResponse.result, detectedString)
    }

    private suspend fun requestTranslate(visionResult: Transaction, sourceText: String) {
        val translationKitType: TranslationKitType = targetHandleViewModel.preferenceRepository.translationKitTypeFlow.first()
        val sourceLanguageCode: String = TranslationSourcePolicy.requestSourceLanguageCode(
            userSourceLanguageCode = targetHandleViewModel.preferenceRepository.sourceLanguageCodeFlow.first(),
            detectedLanguageCode = visionResult.detectedLanguageCode,
            translationKitType = translationKitType,
        )
        val targetLanguageCode: String = targetHandleViewModel.preferenceRepository.targetLanguageCodeFlow.first()

        if (sourceText.trim().isEmpty()) {
            translationFlow.value = ""
        } else {
            targetHandleViewModel.translationRepository.request(
                translationKitType = translationKitType,
                sourceLanguageCode = sourceLanguageCode,
                targetLanguageCode = targetLanguageCode,
                sourceText = sourceText,
            ).also {
                when (it) {
                    is TranslationResponse.Success -> {
                        val transaction = com.yiqun.translator.data.remote.translation.Transaction(
                            sourceLanguageCode = it.result.sourceLanguageCode,
                            targetLanguageCode = it.result.targetLanguageCode,
                            sourceText = sourceText,
                            translationKitType = it.result.translationKitType,
                            detectedLanguageCode = it.result.detectedLanguageCode,
                            resultText = it.result.resultText,
                        )
                        Timber.tag(TAG).d("===== $translationKitType ${it.result.resultText}")
                        translationFlow.value = it.result.resultText ?: ""
                        targetHandleViewModel.increaseTranslationUsageCount()
                    }

                    is TranslationResponse.Error -> {
                        Timber.tag(TAG).d("Response Error ${it.t}")
                    }
                }
            }
        }
    }
}

object FixedAreaRecognitionPolicy {
    fun firstRecognitionDelayMs(): Long = 0L

    fun pollingDelayMs(): Long = 100L
}














