package com.yiqun.translator.ui.screen.overlay.targethandle

import android.animation.Animator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SignalWifiStatusbarConnectedNoInternet4
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yiqun.translator.R
import com.yiqun.translator.core.OverlayService
import com.yiqun.translator.data.local.screen.ScreenInfo
import com.yiqun.translator.data.local.screen.ScreenInfoHolder
import com.yiqun.translator.data.local.vision.TextDetectMode
import com.yiqun.translator.data.local.vision.WritingDirection
import com.yiqun.translator.data.local.vision.model.VisionText
import com.yiqun.translator.data.remote.translation.Language
import com.yiqun.translator.extensions.isNetworkAvailable
import com.yiqun.translator.extensions.toPx
import com.yiqun.translator.extensions.vibrate
import com.yiqun.translator.ui.screen.main.SettingsActivity
import com.yiqun.translator.ui.screen.overlay.Event
import com.yiqun.translator.ui.screen.overlay.OverlayView
import com.yiqun.translator.ui.screen.overlay.dialog.DialogView
import com.yiqun.translator.ui.screen.overlay.fixedarea.FixedAreaView
import com.yiqun.translator.ui.screen.overlay.menubar.MenuBarView
import com.yiqun.translator.ui.screen.overlay.selection.AreaSelectionView
import com.yiqun.translator.ui.screen.overlay.visiontext.VisionTextView
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Singleton
import kotlin.math.sqrt


/**
 */
@Singleton
class TargetHandleView private constructor(
    private val pointerSide: PointerSide,
) : OverlayView() {

    companion object {
        val PRIMARY: TargetHandleView by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { TargetHandleView(PointerSide.LEFT) }
        val SECONDARY: TargetHandleView by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { TargetHandleView(PointerSide.RIGHT) }
        val INSTANCE: TargetHandleView
            get() = PRIMARY

        suspend fun castConfigured(
            applicationContext: Context,
            dualPointerMode: Boolean,
            repositionPrimary: Boolean = false,
        ) {
            if (PRIMARY.isRunning.get()) {
                PRIMARY.dualPointerMode = dualPointerMode
                if (repositionPrimary) {
                    PRIMARY.setAtStartPosition(applicationContext)
                }
            } else {
                PRIMARY.castWithMode(applicationContext, dualPointerMode)
            }
            if (dualPointerMode) {
                if (!SECONDARY.isRunning.get()) {
                    SECONDARY.castWithMode(applicationContext, true)
                }
            } else if (SECONDARY.isRunning.get()) {
                SECONDARY.clear()
            }
        }

        fun clearAll() {
            if (PRIMARY.isRunning.get()) PRIMARY.clear()
            if (SECONDARY.isRunning.get()) SECONDARY.clear()
        }
    }

    private lateinit var viewModel: TargetHandleViewModel

    private var viewWidth = 0

    private var viewHeight = 0

    private var handleWidth = 0

    private var handleCenterX = 0

    private var handleCenterY = 0

    private var pointerDimen = 0

    private var pointerThumbSpace = 0

    private var dualPointerMode = false

    private val pointerOffsetXState = MutableStateFlow(0)

    private val pointerOffsetYState = MutableStateFlow(0)

    override lateinit var layoutParams: WindowManager.LayoutParams

    override val composable: @Composable () -> Unit = @Composable {
        val context = LocalContext.current
        val density = LocalDensity.current
        val lifecycleOwner = LocalLifecycleOwner.current
        val screenInfo: ScreenInfo = ScreenInfoHolder.get()
        val isDarkMode = isSystemInDarkTheme()

        val textDetectMode by viewModel.preferenceRepository.textDetectModeFlow.collectAsStateWithLifecycle(
            lifecycle = lifecycleOwner.lifecycle,
            initialValue = TextDetectMode.SENTENCE
        )
        val pointerPosition: Point? by viewModel.pointerPositionFlow.collectAsStateWithLifecycle(
            lifecycle = lifecycleOwner.lifecycle,
            initialValue = null
        )
        val pointerStoppedPosition: Point? by viewModel.pointerStoppedPositionFlow.collectAsStateWithLifecycle(
            lifecycle = lifecycleOwner.lifecycle,
            initialValue = null
        )
        LaunchedEffect(pointerStoppedPosition) {
            Timber.tag(TAG).d("LaunchedEffect pointerStoppedPosition $pointerPosition")
        }
        val captureStatus by viewModel.captureStatusFlow.collectAsStateWithLifecycle()
        LaunchedEffect(pointerStoppedPosition) {
            Timber.tag(TAG).d("LaunchedEffect captureStatus $captureStatus")
        }
        val fixedAreaViewState by FixedAreaView.fixedAreaViewStateFlow.collectAsStateWithLifecycle()
        LaunchedEffect(pointerStoppedPosition) {
            Timber.tag(TAG).d("LaunchedEffect fixedAreaViewState $fixedAreaViewState")
        }
        val translateStatus by viewModel.translateStatusFlow.collectAsStateWithLifecycle()
        val motionEventState by viewModel.motionEventFlow.collectAsStateWithLifecycle()
        val activePointerSide by viewModel.activePointerSideFlow.collectAsStateWithLifecycle()
        val isActivePointer = activePointerSide == pointerSide
        val defaultTargetFromHandleOffset = remember { viewModel.preferenceRepository.defaultPointerOffset }
        val leftTargetFromHandleOffset by viewModel.preferenceRepository.pointerLeftOffsetFlow.collectAsStateWithLifecycle(
            lifecycle = lifecycleOwner.lifecycle,
            initialValue = defaultTargetFromHandleOffset
        )
        val rightTargetFromHandleOffset by viewModel.preferenceRepository.pointerRightOffsetFlow.collectAsStateWithLifecycle(
            lifecycle = lifecycleOwner.lifecycle,
            initialValue = defaultTargetFromHandleOffset
        )
        val targetFromHandleOffset = when (pointerSide) {
            PointerSide.LEFT -> leftTargetFromHandleOffset
            PointerSide.RIGHT -> rightTargetFromHandleOffset
        }
        LaunchedEffect(pointerStoppedPosition) {
            Timber.tag(TAG).d("LaunchedEffect motionEventState $motionEventState")
        }
        val menuOperatingState by MenuBarView.operatingStateFlow.collectAsStateWithLifecycle()
        val pointerOffsetX by pointerOffsetXState.collectAsStateWithLifecycle()
        val pointerOffsetY by pointerOffsetYState.collectAsStateWithLifecycle()
        val overlayLayout = remember(pointerDimen, handleWidth, targetFromHandleOffset) {
            PointerOverlayLayout.fromTargetFromHandleOffset(
                pointerDimen = pointerDimen,
                handleWidth = handleWidth,
                targetFromHandleOffset = targetFromHandleOffset,
            )
        }
        SideEffect {
            updateOverlayLayout(context, overlayLayout)
        }
        val translationState by viewModel.translationFlow.collectAsStateWithLifecycle(
            lifecycle = lifecycleOwner.lifecycle,
            initialValue = null
        )
        val pointerPositionedVisionText by viewModel.pointerPositionedVisionTextFlow.collectAsStateWithLifecycle(
            lifecycle = lifecycleOwner.lifecycle,
            initialValue = null
        )
        val dragHandleHaptic by viewModel.preferenceRepository.dragHandleHapticFlow.collectAsStateWithLifecycle(
            lifecycle = lifecycleOwner.lifecycle,
            initialValue = false
        )
        val areaSelecting by viewModel.areaSelectingStateFlow.collectAsStateWithLifecycle(
            lifecycle = lifecycleOwner.lifecycle,
            initialValue = false
        )
        LaunchedEffect(pointerStoppedPosition) {
            Timber.tag(TAG).d("LaunchedEffect areaSelecting $areaSelecting")
        }
        val isWritingRtl = remember { mutableStateOf(false) }
        val sourceLanguageCode by viewModel.preferenceRepository.sourceLanguageCodeFlow.collectAsStateWithLifecycle(
            lifecycle = lifecycleOwner.lifecycle,
            initialValue = "auto"
        )
        LaunchedEffect(sourceLanguageCode) {
            val writingDirection = Language.writingDirection(sourceLanguageCode, false)
            isWritingRtl.value = writingDirection == WritingDirection.RTL
        }

        val previousVisionText = remember { mutableStateOf<VisionText?>(null) }
        LaunchedEffect(dragHandleHaptic, pointerStoppedPosition, pointerPositionedVisionText, textDetectMode, activePointerSide) {
            if (!isActivePointer || pointerPositionedVisionText == null) {
                previousVisionText.value = null
            } else if (dragHandleHaptic
                && pointerStoppedPosition != null
                && pointerPositionedVisionText != previousVisionText.value
                && (textDetectMode == TextDetectMode.WORD
                        || textDetectMode == TextDetectMode.SENTENCE
                        || textDetectMode == TextDetectMode.SENSE_GROUP
                        || textDetectMode == TextDetectMode.PARAGRAPH)
            ) {
                context.vibrate()
                previousVisionText.value = pointerPositionedVisionText
            }
        }

        LaunchedEffect(textDetectMode, pointerStoppedPosition, activePointerSide) {
            if (isActivePointer && pointerStoppedPosition != null && textDetectMode == TextDetectMode.SELECT && !AreaSelectionView.INSTANCE.isRunning.get()) {
                // Timber.tag(TAG).i("AreaSelectionView.INSTANCE.cast $pointerStoppedPosition")
                if (dragHandleHaptic) {
                    context.vibrate()
                }
                AreaSelectionView.INSTANCE.cast(context, pointerStoppedPosition!!)
            }
        }

        LaunchedEffect(textDetectMode, pointerStoppedPosition, activePointerSide) {
            if (isActivePointer && pointerStoppedPosition != null && textDetectMode == TextDetectMode.FIXED_AREA && !FixedAreaView.INSTANCE.isRunning.get()) {
                // Timber.tag(TAG).i("FixedAreaView.INSTANCE.cast $pointerStoppedPosition")
                if (dragHandleHaptic) {
                    context.vibrate()
                }
                FixedAreaView.INSTANCE.cast(context, pointerStoppedPosition!!)
            }
        }

        Box(
            modifier = Modifier
//                .background(Color.Cyan)
                .alpha(if (captureStatus == CaptureStatus.Requested || (textDetectMode == TextDetectMode.FIXED_AREA && fixedAreaViewState == FixedAreaView.State.Translating)) 0.01f else 1.0f)
                .width(with(density) { overlayLayout.width.toDp() })
                .height(with(density) { overlayLayout.height.toDp() }),
        ) {
            Box(
                modifier = Modifier
                    .size(dimensionResource(id = R.dimen.target_pointer_dimen))
                    .offset {
                        IntOffset(
                            overlayLayout.targetIconTopLeftX + pointerOffsetX,
                            overlayLayout.targetIconTopLeftY + pointerOffsetY,
                        )
                    }
                    .zIndex(1f),
                contentAlignment = Alignment.Center
            ) {
                if (translateStatus == TranslateStatus.Requested && textDetectMode != TextDetectMode.SELECT) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(dimensionResource(id = R.dimen.target_pointer_progress_dimen)),
                        color = Color(0xFF48baef),
                        strokeWidth = 1.8.dp
                    )
                }
                Image(
                    painter = painterResource(id = if (areaSelecting) R.drawable.drag_selection_pointer else R.drawable.drag_pointer),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            if (isWritingRtl.value) rotationY = 180f
                        },
                    alpha = if (
                        !isActivePointer
                        || motionEventState == MotionEvent.INVALID_POINTER_ID
                        || motionEventState == MotionEvent.ACTION_UP
                    ) 0.0f else 1.0f,
                    colorFilter = if (areaSelecting) {
                        if (textDetectMode == TextDetectMode.SELECT) ColorFilter.tint(Color(0x883B6FDB)) else ColorFilter.tint(Color(0x88006600))
                    } else null
                )
            }
            Box(
                modifier = Modifier
//                .background(Color.Blue)
                    .size(dimensionResource(id = R.dimen.target_handle_width))
                    .offset {
                        val (x, y) = overlayLayout.handleTopLeft
                        IntOffset(x, y)
                    },
                contentAlignment = Alignment.Center
            ) {
                val alpha by rememberInfiniteTransition(label = "service live anim").animateFloat(
                    initialValue = 1.0f,
                    targetValue = 0.9f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 800, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ), label = "service live anim spec"
                )

                /**
                 * !!! Important
                 */
                Image(
                    painter = painterResource(id = if (isDarkMode) R.drawable.drag_handle_dark else R.drawable.drag_handle),
                    contentDescription = null,
                    modifier = Modifier
                        .size(dimensionResource(id = R.dimen.target_handle_thumb_dimen))
                        .alpha(alpha)
                )
            }
        }

        LaunchedEffect(motionEventState, translationState, menuOperatingState, activePointerSide) {
//            Timber.tag(TAG).d("LaunchedEffect motionEventState == MotionEvent.ACTION_UP : ${motionEventState == MotionEvent.ACTION_UP}")
            Timber.tag(TAG).d("LaunchedEffect translationState [$translationState]")
            if (!isActivePointer) {
                cancelDockDragHandle()
            } else if (menuOperatingState) {
                cancelDockDragHandle()
            } else {
                if (motionEventState == MotionEvent.ACTION_UP && translationState == null) {
                    val loc = IntArray(2)
                    view?.getLocationOnScreen(loc)
                    val posX = loc[0]
//                Timber.tag(TAG).d("posX [$posX] [$viewWidth] [${(screenInfo.height - viewWidth)}]")
                    if (posX < 10.dp.toPx(context)) {
                        scheduleDockDragHandle(context, true, 500)
                    } else if ((screenInfo.width - viewWidth - 10.dp.toPx(context)) < posX) {
                        scheduleDockDragHandle(context, false, 500)
                    } else if (viewModel.dragHandleDocking) {
                        scheduleDockDragHandle(context, posX < screenInfo.width / 2, viewModel.dockingDelay)
                    }
                }
            }
        }
    }

    override val touchListener: (Context) -> View.OnTouchListener? = { applicationContext ->

        val isRTL = (applicationContext.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL)

        object : View.OnTouchListener {

            val tapDetector = GestureDetector(applicationContext, object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    return true
                }

                override fun onDoubleTap(e: MotionEvent): Boolean {
                    if (!SettingsActivity.liveStateFlow.value) {
                        launchInAVDCoroutineScope {
                            delay(200)
                            if (!VisionTextView.INSTANCE.isRunning.get()) {
                                applicationContext.vibrate()
                                SettingsActivity.start(applicationContext)
                            }
                        }
                    }
                    return true
                }

                override fun onLongPress(e: MotionEvent) {
                }
            })

            var touchStartX = 0f
            var touchStartY = 0f
            var dragStartX = 0
            var dragStartY = 0

            @SuppressLint("ClickableViewAccessibility")
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                tapDetector.onTouchEvent(event)

                viewModel.motionEventFlow.value = event.action
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        if (applicationContext.isNetworkAvailable()) {
                            viewModel.activePointerSideFlow.value = pointerSide
                            touchStartX = event.rawX
                            touchStartY = event.rawY
                            dragStartX = layoutParams.x
                            dragStartY = layoutParams.y
                            dragHandleDockingJob?.cancel()
                            viewModel.dockStateFlow.value = false
                        } else {
                            launchInOverlayViewCoroutineScope {
                                DialogView.INSTANCE.cast(
                                    applicationContext = applicationContext,
                                    icon = Icons.Default.SignalWifiStatusbarConnectedNoInternet4,
                                    dialogTitle = applicationContext.getString(R.string.message_network_unavailable),
                                    dialogText = applicationContext.getString(R.string.message_network_unavailable_detail),
                                    onConfirm = {}
                                )
                            }
                        }
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val screenInfo: ScreenInfo = ScreenInfoHolder.get()
                        layoutParams.x = (dragStartX + (event.rawX - touchStartX)).toInt()
                        layoutParams.y = (dragStartY + (event.rawY - touchStartY)).toInt()
                        updateLayout(applicationContext)

                        val loc = IntArray(2)
                        view?.getLocationOnScreen(loc)

                        val centerX = loc[0] + handleCenterX
                        val adjustionPositionWidth = handleWidth * 6 / 10

                        val _screenStartAdjustionPosition = adjustionPositionWidth
                        val _screenEndAdjustionPosition = screenInfo.width - adjustionPositionWidth

//                        Timber.tag(TAG).d("isRTL $isRTL centerX $centerX fullWidth ${screenInfo.height} StartAdjustion $_screenStartAdjustionPosition EndAdjustion $_screenEndAdjustionPosition")
                        val _pointerOffsetX = when {
                            centerX < _screenStartAdjustionPosition -> _screenStartAdjustionPosition - centerX
                            centerX > _screenEndAdjustionPosition -> _screenEndAdjustionPosition - centerX
                            else -> 0
                        } * if (isRTL) 1 else -1

                        pointerOffsetXState.value = _pointerOffsetX

                        val bottomLeft = loc[1] + viewHeight
                        val _screenBottomStart = screenInfo.height - viewHeight
                        val _pointerOffsetY = if (bottomLeft > _screenBottomStart) (bottomLeft - _screenBottomStart) / 2 else 0
                        pointerOffsetYState.value = _pointerOffsetY

                        val x = layoutParams.x + handleCenterX + _pointerOffsetX
                        val y = layoutParams.y + handleCenterY + _pointerOffsetY
                        viewModel.updatePointerPosition(pointerSide, Point(x, y))
                    }

                    MotionEvent.ACTION_UP -> {
                        repositionWithinScreen(applicationContext)
                    }
                }
                return true
            }
        }
    }

    override suspend fun cast(applicationContext: Context) {
        castWithMode(applicationContext, false)
    }

    private fun updateOverlayLayout(
        context: Context,
        overlayLayout: PointerOverlayLayout,
    ) {
        if (
            viewWidth == overlayLayout.width
            && viewHeight == overlayLayout.height
            && handleCenterX == overlayLayout.handleCenterX
            && handleCenterY == overlayLayout.handleCenterY
        ) {
            return
        }

        val preserveHandlePosition = ::layoutParams.isInitialized && isRunning.get()
        val oldHandleX = if (preserveHandlePosition) layoutParams.x + handleCenterX else 0
        val oldHandleY = if (preserveHandlePosition) layoutParams.y + handleCenterY else 0

        viewWidth = overlayLayout.width
        viewHeight = overlayLayout.height
        handleCenterX = overlayLayout.handleCenterX
        handleCenterY = overlayLayout.handleCenterY

        if (preserveHandlePosition) {
            layoutParams.x = oldHandleX - handleCenterX
            layoutParams.y = oldHandleY - handleCenterY
            updateLayout(context)
        }
    }

    suspend fun castWithMode(applicationContext: Context, dualPointerMode: Boolean) {
        this.dualPointerMode = dualPointerMode
        val screenInfo: ScreenInfo = ScreenInfoHolder.get()
        handleWidth = applicationContext.resources.getDimensionPixelSize(R.dimen.target_handle_width)
        pointerDimen = applicationContext.resources.getDimensionPixelSize(R.dimen.target_pointer_dimen)
        pointerThumbSpace = applicationContext.resources.getDimensionPixelSize(R.dimen.target_handle_pointer_thumb_space)
        val defaultTargetFromHandleOffset = PointerOffset.defaultTargetFromHandleOffset(
            pointerDimen = pointerDimen,
            handleWidth = handleWidth,
            pointerThumbSpace = pointerThumbSpace,
        )
        val overlayLayout = PointerOverlayLayout.fromTargetFromHandleOffset(
            pointerDimen = pointerDimen,
            handleWidth = handleWidth,
            targetFromHandleOffset = defaultTargetFromHandleOffset,
        )
        viewWidth = overlayLayout.width
        viewHeight = overlayLayout.height
        handleCenterX = overlayLayout.handleCenterX
        handleCenterY = overlayLayout.handleCenterY
//        Timber.tag(TAG).d("viewWidth $viewWidth")
//        Timber.tag(TAG).d("viewHeight $viewHeight")
//        Timber.tag(TAG).d("pointerDimen $pointerDimen")

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            startX(screenInfo),
            screenInfo.height / 2 - viewHeight / 2,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        if (isRunning.get()) {
            setAtStartPosition(applicationContext)
        } else {
            super.cast(applicationContext)
        }
    }

    private fun setAtStartPosition(context: Context) {
        val screenInfo: ScreenInfo = ScreenInfoHolder.get()
        SayHereView.INSTANCE.clear()
        cancelDockDragHandle()
        layoutParams.x = startX(screenInfo)
        layoutParams.y = screenInfo.height / 2 - viewHeight / 2
        updateLayout(context)
    }

    private fun startX(screenInfo: ScreenInfo): Int {
        val handleX = if (!dualPointerMode) {
            screenInfo.width / 2
        } else {
            when (pointerSide) {
                PointerSide.LEFT -> screenInfo.width / 4
                PointerSide.RIGHT -> screenInfo.width * 3 / 4
            }
        }
        val x = handleX - handleCenterX
        return x.coerceIn(0, (screenInfo.width - viewWidth).coerceAtLeast(0))
    }

    override fun onServiceConnected(overlayService: OverlayService) {
        viewModel = overlayService.getTargetHandleViewModel()
        super.onServiceConnected(overlayService)
    }

    /**
     */
    fun runTranslation(point: Point, textDetectMode: TextDetectMode) {
        launchInAVDCoroutineScope {
            if (textDetectMode == TextDetectMode.SELECT || textDetectMode == TextDetectMode.FIXED_AREA) {
                /**
                 */
                viewModel.updateTextDetectMode(TextDetectMode.SENTENCE)
                delay(100L)
            }
            viewModel.motionEventFlow.value = MotionEvent.ACTION_DOWN
            delay(1200L)
            viewModel.pointerPositionFlow.value = point
            delay(200L)
            viewModel.pointerPositionFlow.value = Point(point.x + 1, point.y)
        }
    }

    fun closeTranslation(textDetectMode: TextDetectMode?) {
        if (::viewModel.isInitialized) {
            viewModel.motionEventFlow.value = MotionEvent.ACTION_UP
            textDetectMode?.let { viewModel.updateTextDetectMode(it) }
        }
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    //                                                                                            //
    //                                  OverlayServiceEventListener                               //
    //                                                                                            //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    override fun onOverlayServiceEvent(overlayService: OverlayService, event: Event) {
        when (event) {
            Event.ConfigurationChanged -> {
                onConfigurationChanged(overlayService.applicationContext)
            }

            Event.Unbind -> {
                onServiceUnbind()
            }

            Event.DockTargetHandleView -> {
                dockDragHandle(overlayService.applicationContext, true)
            }
        }
        super.onOverlayServiceEvent(overlayService, event)
    }

    private fun onServiceUnbind() {
        Timber.tag(TAG).i("#### onServiceUnbind() ####")
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    //                                                                                            //
    //                                                                                            //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private var dragHandleDockingJob: Job? = null

    private var dockAnimator: ValueAnimator? = null

    /**
     */
    private fun onConfigurationChanged(context: Context) {
        Timber.tag(TAG).d("#### onConfigurationChanged() ####")
        viewModel.restartCaptureRepository()

        val screenInfo: ScreenInfo = ScreenInfoHolder.get()
        Timber.tag(TAG).d("layoutParams.x ${layoutParams.x} layoutParams.y ${layoutParams.y} screenInfo $screenInfo")

        layoutParams.x = when (layoutParams.x + viewWidth) {
            viewWidth -> 0
            screenInfo.height -> screenInfo.width - viewWidth
            screenInfo.width -> screenInfo.height - viewWidth
            else -> (layoutParams.x + viewWidth / 2) * screenInfo.width / screenInfo.height - viewWidth / 2
        }

        if (layoutParams.x < 0) {
            layoutParams.x = 0
        } else if ((layoutParams.x + viewWidth) > screenInfo.width) {
            layoutParams.x = screenInfo.width - viewWidth
        }

        layoutParams.y = when (layoutParams.y + viewHeight) {
            viewHeight -> 0
            screenInfo.height -> screenInfo.width - viewHeight
            screenInfo.width -> screenInfo.height - viewHeight
            else -> (layoutParams.y + viewHeight / 2) * screenInfo.height / screenInfo.width - viewHeight / 2
        }

        if (layoutParams.y < 0) {
            layoutParams.y = 0
        } else if ((layoutParams.y + viewHeight) > screenInfo.height) {
            layoutParams.y = screenInfo.height - viewHeight
        }

//         layoutParams.x = screenInfo.width / 2 - viewWidth / 2
//         layoutParams.y = screenInfo.height / 2 - viewHeight / 2

        updateLayout(context)
        Timber.tag(TAG).d("updateLayout layoutParams.x ${layoutParams.x} layoutParams.y ${layoutParams.y} screenInfo $screenInfo")

//        scheduleDockDragHandle(context)
    }

    /**
     */
    private fun repositionWithinScreen(applicationContext: Context) {
        val screenInfo: ScreenInfo = ScreenInfoHolder.get()
        val loc = IntArray(2)
        view?.getLocationOnScreen(loc)

        val topLeft = Point(loc[0], loc[1])
        val topRight = Point(loc[0] + viewWidth, loc[1])
        val bottomLeft = Point(loc[0], loc[1] + viewHeight)
        val bottomRight = Point(loc[0] + viewWidth, loc[1] + viewHeight)

        val moveX =
            if (topLeft.x < 0) 0 - topLeft.x
            else if (topRight.x > screenInfo.width) (topRight.x - screenInfo.width) * -1
            else 0

        val moveY =
            if (topLeft.y < 0) 0 - topLeft.y
            else if (bottomLeft.y > screenInfo.height) (bottomLeft.y - screenInfo.height) * -1
            else 0

        val move = sqrt(moveX.toDouble() * moveX + moveY * moveY)
        if (move > 0) {
            val fromX = layoutParams.x
            val fromY = layoutParams.y
            SpringAnimation(FloatValueHolder()).apply {
                spring = SpringForce().apply {
                    setStartValue(0f)
                    setFinalPosition(move.toFloat())
                    stiffness = SpringForce.STIFFNESS_LOW
                    dampingRatio = SpringForce.DAMPING_RATIO_LOW_BOUNCY
                }
                addUpdateListener { _, value, _ ->
                    if (moveX != 0) layoutParams.x = fromX + ((moveX * value) / move).toInt()
                    if (moveY != 0) layoutParams.y = fromY + ((moveY * value) / move).toInt()
                    updateLayout(applicationContext)
                }
                addEndListener { animation, canceled, value, velocity ->
                    // Timber.tag(TAG).d("springAnim End $animation $canceled $value $velocity")
                }
            }.start()
        }
    }

    /**
     */
    private fun scheduleDockDragHandle(context: Context, start: Boolean, delay: Long) {
        dragHandleDockingJob?.cancel()
        if (!AreaSelectionView.INSTANCE.isRunning.get() && !FixedAreaView.INSTANCE.isRunning.get()) {
            dragHandleDockingJob = launchInOverlayViewCoroutineScope {
                delay(delay)
                dockDragHandle(context, start)
            }
        }
    }

    private fun dockDragHandle(context: Context, start: Boolean) {
        Timber.tag(TAG).d("#### dockDragHandle() ####")
        val screenInfo: ScreenInfo = ScreenInfoHolder.get()
        val hideDepth = context.resources.getDimensionPixelSize(R.dimen.target_handle_width)
        val targetX = (if (start) -hideDepth else screenInfo.width).toDouble()
//        val targetX:Double =  -context.resources.getDimensionPixelSize(R.dimen.target_handle_width) * 0.94
        val targetY = (screenInfo.height - context.resources.getDimensionPixelSize(R.dimen.target_handle_height) - context.resources.getDimensionPixelSize(R.dimen.target_handle_width)) / 2

        val startX = layoutParams.x
        val startY = layoutParams.y

        val deltaX: Double = targetX - startX
        val deltaY = targetY - startY

        dockAnimator?.cancel()

        dockAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 500

            interpolator = android.view.animation.DecelerateInterpolator()

            addUpdateListener { valueAnimator ->
                val fraction = valueAnimator.animatedValue as Float

                val easedFraction = fraction * fraction * (3 - 2 * fraction) // Smootherstep curve
                val acceleratedFraction = easedFraction * easedFraction
                val deceleratedFraction = (1 - easedFraction) * (1 - easedFraction)

                val currentX = startX + (deltaX * acceleratedFraction).toInt()

                val currentY = startY - (deltaY * deceleratedFraction).toInt() + deltaY

                layoutParams.x = currentX
                layoutParams.y = currentY
                updateLayout(context)
            }

            addListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(animation: Animator) {}

                override fun onAnimationEnd(animation: Animator) {
                    exposeTargetHandleKnob(context, start)
                    viewModel.dockStateFlow.value = true
                    dockAnimator = null
                }

                override fun onAnimationCancel(animation: Animator) {}

                override fun onAnimationRepeat(animation: Animator) {}
            })

            start()
        }
    }

    private fun cancelDockDragHandle() {
        dragHandleDockingJob?.cancel()
        dockAnimator?.cancel()
        dockAnimator = null
        viewModel.dockStateFlow.value = false
    }

    /**
     */
    private fun exposeTargetHandleKnob(context: Context, start: Boolean) {
        Timber.tag(TAG).i("------------- exposeTargetHandleKnob [$start]]")
        val startX = layoutParams.x
        val hideDepth = (context.resources.getDimensionPixelSize(R.dimen.target_handle_width) * if (start) .30 else .32).toInt()
        val deltaX: Int = if (start) hideDepth else -hideDepth
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 300
            interpolator = android.view.animation.DecelerateInterpolator()

            addUpdateListener { valueAnimator ->
                val fraction = valueAnimator.animatedValue as Float
                val currentX = startX + (deltaX * fraction).toInt()
//                Timber.tag(TAG).d("[${screenInfo.height}] [${viewWidth}] [$currentX] [$startX] [$deltaX] [${(deltaX * fraction).toInt()}]")
                layoutParams.x = currentX
                updateLayout(context)
            }

            addListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(animation: Animator) {}

                override fun onAnimationEnd(animation: Animator) {
                    launchInAVDCoroutineScope {
                        if (start && !viewModel.preferenceRepository.isSayHereLShownFlow.first()) {
                            SayHereView.INSTANCE.cast(
                                applicationContext = context,
                                start = true,
                                position = Point(layoutParams.x, layoutParams.y)
                            )
                        } else if (!start && !viewModel.preferenceRepository.isSayHereRShownFlow.first()) {
                            SayHereView.INSTANCE.cast(
                                applicationContext = context,
                                start = false,
                                position = Point(layoutParams.x, layoutParams.y)
                            )
                        }
                    }
                }

                override fun onAnimationCancel(animation: Animator) {}

                override fun onAnimationRepeat(animation: Animator) {}
            })

            start()
        }
    }

    override fun clear() {
        dragHandleDockingJob?.cancel()
        super.clear()
    }
}

