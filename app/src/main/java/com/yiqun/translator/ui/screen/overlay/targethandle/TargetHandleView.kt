package com.yiqun.translator.ui.screen.overlay.targethandle

import android.animation.Animator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SignalWifiStatusbarConnectedNoInternet4
import androidx.compose.runtime.Composable
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.yiqun.translator.R
import com.yiqun.translator.core.OverlayService
import com.yiqun.translator.data.local.screen.ScreenInfo
import com.yiqun.translator.data.local.screen.ScreenInfoHolder
import com.yiqun.translator.data.local.vision.TextDetectMode
import com.yiqun.translator.data.local.vision.WritingDirection
import com.yiqun.translator.data.local.vision.model.VisionText
import com.yiqun.translator.data.remote.translation.Language
import com.yiqun.translator.extensions.isNetworkAvailable
import com.yiqun.translator.extensions.vibrate
import com.yiqun.translator.ui.screen.main.SettingsActivity
import com.yiqun.translator.ui.screen.overlay.Event
import com.yiqun.translator.ui.screen.overlay.OverlayView
import com.yiqun.translator.ui.screen.overlay.dialog.DialogView
import com.yiqun.translator.ui.screen.overlay.detectmode.DetectModeMenuView
import com.yiqun.translator.ui.screen.overlay.fixedarea.FixedAreaView
import com.yiqun.translator.ui.screen.overlay.menubar.MenuBarView
import com.yiqun.translator.ui.screen.overlay.selection.AreaSelectionView
import com.yiqun.translator.ui.screen.overlay.visiontext.VisionTextView
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Singleton
import java.util.LinkedHashSet
import kotlin.coroutines.resume
import kotlin.math.roundToInt
import kotlin.math.sqrt


/**
 */
@Singleton
class TargetHandleView private constructor(
    private val pointerSide: PointerSide,
) : OverlayView() {

    companion object {
        private val instances = LinkedHashSet<TargetHandleView>()

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

        suspend fun recastForCurrentConfiguration(applicationContext: Context) {
            val dualPointerMode = PRIMARY.viewModel.preferenceRepository.dualPointerEnabledFlow.first()
            castConfigured(
                applicationContext = applicationContext,
                dualPointerMode = dualPointerMode,
                repositionPrimary = true,
            )
            if (dualPointerMode && SECONDARY.isRunning.get()) {
                SECONDARY.setAtStartPosition(applicationContext)
            }
        }

        fun clearAll() {
            if (PRIMARY.isRunning.get()) PRIMARY.clear()
            if (SECONDARY.isRunning.get()) SECONDARY.clear()
        }

        private fun registeredInstances(): List<TargetHandleView> {
            return synchronized(instances) { instances.toList() }
        }

        internal fun targetIconViews(): List<TargetIconNativeView> {
            return registeredInstances().mapNotNull { handleView ->
                handleView.targetView as? TargetIconNativeView
            }
        }

        private fun showOnlyInteractingPointer(activeSide: PointerSide) {
            registeredInstances().forEach { handleView ->
                handleView.updatePointerInteractionWindowVisibility(handleView.pointerSide == activeSide)
            }
            MenuBarView.INSTANCE.updatePointerInteractionWindowVisibility(false)
        }

        private fun restorePointerInteractionWindows() {
            registeredInstances().forEach { handleView ->
                handleView.updatePointerInteractionWindowVisibility(true)
            }
            MenuBarView.INSTANCE.updatePointerInteractionWindowVisibility(true)
        }
    }

    init {
        synchronized(instances) { instances.add(this) }
    }

    private lateinit var viewModel: TargetHandleViewModel

    private var viewWidth = 0

    private var viewHeight = 0

    private var handleWidth = 0

    private var handleCenterX = 0

    private var handleCenterY = 0

    private var pointerDimen = 0

    private var pointerThumbSpace = 0

    private var passThroughWindowLayout: PointerPassThroughWindowLayout? = null

    private lateinit var targetLayoutParams: WindowManager.LayoutParams

    private var targetView: View? = null

    private val handleWindowVisibilityState = StableWindowVisibilityState()

    private val targetWindowVisibilityState = StableWindowVisibilityState()

    private var dualPointerMode = false

    private val pointerOffsetXState = MutableStateFlow(0)

    private val pointerOffsetYState = MutableStateFlow(0)

    private var nativeStateCollectorJobs = emptyList<Job>()

    private var lastTargetIconRenderState = TargetIconRenderState()

    override lateinit var layoutParams: WindowManager.LayoutParams

    override fun createView(overlayService: OverlayService): View {
        val thumbDimen = overlayService.resources.getDimensionPixelSize(R.dimen.target_handle_thumb_dimen)
        val padding = ((handleWidth - thumbDimen) / 2).coerceAtLeast(0)
        val isDarkMode = (overlayService.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        return ImageView(overlayService).apply {
            setImageResource(if (isDarkMode) R.drawable.drag_handle_dark else R.drawable.drag_handle)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(padding, padding, padding, padding)
        }
    }

    override val composable: @Composable () -> Unit = @Composable {}

    private data class PointerInteractionState(
        val motionEventAction: Int,
        val activeSide: PointerSide,
    )

    private data class TargetIconStatusState(
        val textDetectMode: TextDetectMode,
        val captureStatus: CaptureStatus,
        val translateStatus: TranslateStatus,
        val areaSelecting: Boolean,
        val writingRtl: Boolean,
    )

    private data class PointerStoppedState(
        val stoppedPosition: Point?,
        val positionedVisionText: VisionText?,
        val textDetectMode: TextDetectMode,
        val dragHandleHaptic: Boolean,
        val activeSide: PointerSide,
    )

    private data class DockingState(
        val motionEventAction: Int,
        val translationState: Pair<VisionText, com.yiqun.translator.data.remote.translation.Transaction>?,
        val menuOperating: Boolean,
        val activeSide: PointerSide,
    )

    private fun restartNativeStateCollectors(applicationContext: Context) {
        nativeStateCollectorJobs.forEach { it.cancel() }

        val interactionFlow = combine(
            viewModel.motionEventFlow,
            viewModel.activePointerSideFlow,
        ) { motionEventAction, activeSide ->
            PointerInteractionState(motionEventAction, activeSide)
        }.distinctUntilChanged()

        val targetIconStatusFlow = combine(
            viewModel.preferenceRepository.textDetectModeFlow,
            viewModel.captureStatusFlow,
            viewModel.translateStatusFlow,
            viewModel.areaSelectingStateFlow,
            viewModel.preferenceRepository.sourceLanguageCodeFlow,
        ) { textDetectMode, captureStatus, translateStatus, areaSelecting, sourceLanguageCode ->
            val writingDirection = Language.writingDirection(sourceLanguageCode, false)
            TargetIconStatusState(
                textDetectMode = textDetectMode,
                captureStatus = captureStatus,
                translateStatus = translateStatus,
                areaSelecting = areaSelecting,
                writingRtl = writingDirection == WritingDirection.RTL,
            )
        }.distinctUntilChanged()

        nativeStateCollectorJobs = listOf(
            launchInOverlayViewCoroutineScope {
                combine(
                    viewModel.preferenceRepository.pointerLeftOffsetFlow,
                    viewModel.preferenceRepository.pointerRightOffsetFlow,
                ) { leftOffset, rightOffset ->
                    when (pointerSide) {
                        PointerSide.LEFT -> leftOffset
                        PointerSide.RIGHT -> rightOffset
                    }
                }.distinctUntilChanged().collect { targetFromHandleOffset ->
                    val passThroughLayout = PointerPassThroughWindowLayout.fromTargetFromHandleOffset(
                        pointerDimen = pointerDimen,
                        handleWidth = handleWidth,
                        targetFromHandleOffset = targetFromHandleOffset,
                    )
                    updatePassThroughWindowLayout(applicationContext, passThroughLayout)
                    updateTargetLayout(applicationContext)
                }
            },
            launchInOverlayViewCoroutineScope {
                interactionFlow.collect { interaction ->
                    val interactionVisible = PointerInteractionVisibilityPolicy.visibleForSide(
                        side = pointerSide,
                        activeSide = interaction.activeSide,
                        motionEventAction = interaction.motionEventAction,
                    )
                    updateHandleWindowVisibility(interactionVisible)
                    updateTargetWindowVisibility(interactionVisible)
                }
            },
            launchInOverlayViewCoroutineScope {
                combine(
                    targetIconStatusFlow,
                    interactionFlow,
                    FixedAreaView.fixedAreaViewStateFlow,
                ) { status, interaction, fixedAreaState ->
                    TargetIconRenderPolicy.stateFor(
                        side = pointerSide,
                        activeSide = interaction.activeSide,
                        motionEventAction = interaction.motionEventAction,
                        textDetectMode = status.textDetectMode,
                        captureStatus = status.captureStatus,
                        fixedAreaTranslating = status.textDetectMode == TextDetectMode.FIXED_AREA &&
                                fixedAreaState == FixedAreaView.State.Translating,
                        translateStatus = status.translateStatus,
                        areaSelecting = status.areaSelecting,
                        writingRtl = status.writingRtl,
                    )
                }.distinctUntilChanged().collect { state ->
                    applyTargetIconRenderState(state)
                }
            },
            launchInOverlayViewCoroutineScope {
                var previousVisionText: VisionText? = null
                var lastAreaAction: Pair<TextDetectMode, Point>? = null
                combine(
                    viewModel.pointerStoppedPositionFlow,
                    viewModel.pointerPositionedVisionTextFlow,
                    viewModel.preferenceRepository.textDetectModeFlow,
                    viewModel.preferenceRepository.dragHandleHapticFlow,
                    viewModel.activePointerSideFlow,
                ) { stoppedPosition, positionedVisionText, textDetectMode, dragHandleHaptic, activeSide ->
                    PointerStoppedState(
                        stoppedPosition = stoppedPosition,
                        positionedVisionText = positionedVisionText,
                        textDetectMode = textDetectMode,
                        dragHandleHaptic = dragHandleHaptic,
                        activeSide = activeSide,
                    )
                }.collect { state ->
                    val isActivePointer = state.activeSide == pointerSide
                    if (!isActivePointer || state.stoppedPosition == null) {
                        previousVisionText = null
                        lastAreaAction = null
                        return@collect
                    }

                    if (
                        state.dragHandleHaptic &&
                        state.positionedVisionText != null &&
                        state.positionedVisionText != previousVisionText &&
                        isPointedTranslationMode(state.textDetectMode)
                    ) {
                        applicationContext.vibrate()
                        previousVisionText = state.positionedVisionText
                    }

                    val areaAction = state.textDetectMode to state.stoppedPosition
                    when (state.textDetectMode) {
                        TextDetectMode.SELECT -> {
                            if (areaAction != lastAreaAction && !AreaSelectionView.INSTANCE.isRunning.get()) {
                                if (state.dragHandleHaptic) applicationContext.vibrate()
                                AreaSelectionView.INSTANCE.cast(applicationContext, state.stoppedPosition)
                                lastAreaAction = areaAction
                            }
                        }

                        TextDetectMode.FIXED_AREA -> {
                            if (areaAction != lastAreaAction && !FixedAreaView.INSTANCE.isRunning.get()) {
                                if (state.dragHandleHaptic) applicationContext.vibrate()
                                FixedAreaView.INSTANCE.cast(applicationContext, state.stoppedPosition)
                                lastAreaAction = areaAction
                            }
                        }

                        else -> {
                            lastAreaAction = null
                        }
                    }
                }
            },
            launchInOverlayViewCoroutineScope {
                val dockingReleaseTracker = PointerDockingReleaseTracker()
                combine(
                    viewModel.motionEventFlow,
                    viewModel.translationFlow,
                    viewModel.activePointerSideFlow,
                ) { motionEventAction, translationState, activeSide ->
                    DockingState(motionEventAction, translationState, false, activeSide)
                }.collect { state ->
                    val shouldScheduleDock = dockingReleaseTracker.shouldScheduleDockAfterRelease(
                        side = pointerSide,
                        activeSide = state.activeSide,
                        motionEventAction = state.motionEventAction,
                        translationActive = state.translationState != null,
                        menuOperating = state.menuOperating,
                    )
                    if (state.activeSide != pointerSide || state.menuOperating) {
                        cancelDockDragHandle(applicationContext)
                        return@collect
                    }
                    if (shouldScheduleDock) {
                        scheduleDockAfterRelease(applicationContext)
                    }
                }
            },
        )
    }

    private fun applyTargetIconRenderState(state: TargetIconRenderState) {
        lastTargetIconRenderState = state
        (targetView as? TargetIconNativeView)?.render(state)
    }

    private fun isPointedTranslationMode(textDetectMode: TextDetectMode): Boolean {
        return textDetectMode == TextDetectMode.WORD ||
                textDetectMode == TextDetectMode.SENTENCE ||
                textDetectMode == TextDetectMode.SENSE_GROUP ||
                textDetectMode == TextDetectMode.PARAGRAPH
    }

    private fun scheduleDockAfterRelease(context: Context) {
        val screenInfo = ScreenInfoHolder.get()
        val loc = IntArray(2)
        view?.getLocationOnScreen(loc) ?: return
        val posX = loc[0]
        val edgeMargin = (10f * context.resources.displayMetrics.density).roundToInt()
        if (posX < edgeMargin) {
            scheduleDockDragHandle(context, true, 500)
        } else if ((screenInfo.width - viewWidth - edgeMargin) < posX) {
            scheduleDockDragHandle(context, false, 500)
        } else if (viewModel.dragHandleDocking) {
            scheduleDockDragHandle(context, posX < screenInfo.width / 2, viewModel.dockingDelay)
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
                                DetectModeMenuView.INSTANCE.cast(
                                    applicationContext = applicationContext,
                                    anchorCenterX = layoutParams.x + handleCenterX,
                                    anchorCenterY = layoutParams.y + handleCenterY,
                                )
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
            var isDraggingHandle = false

            @SuppressLint("ClickableViewAccessibility")
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                val action = event.actionMasked
                if (action == MotionEvent.ACTION_DOWN) {
                    if (!PointerOverlayHitTest.isHandleHit(handleCenterX, handleCenterY, handleWidth, event.x, event.y)) {
                        isDraggingHandle = false
                        return false
                    }
                    isDraggingHandle = true
                } else if (!isDraggingHandle) {
                    return false
                }

                tapDetector.onTouchEvent(event)

                when (action) {
                    MotionEvent.ACTION_DOWN -> {
                        if (applicationContext.isNetworkAvailable()) {
                            viewModel.activePointerSideFlow.value = pointerSide
                            viewModel.motionEventFlow.value = event.action
                            (targetView as? TargetIconNativeView)?.setPointerVisible(true)
                            showOnlyInteractingPointer(pointerSide)
                            cancelRepositionAnimation()
                            cancelDockDragHandle(applicationContext)
                            touchStartX = event.rawX
                            touchStartY = event.rawY
                            dragStartX = layoutParams.x
                            dragStartY = layoutParams.y
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
                            isDraggingHandle = false
                        }
                    }

                    MotionEvent.ACTION_MOVE -> {
                        viewModel.activePointerSideFlow.value = pointerSide
                        viewModel.motionEventFlow.value = event.action
                        (targetView as? TargetIconNativeView)?.setPointerVisible(true)
                        val screenInfo: ScreenInfo = ScreenInfoHolder.get()
                        layoutParams.x = (dragStartX + (event.rawX - touchStartX)).toInt()
                        layoutParams.y = (dragStartY + (event.rawY - touchStartY)).toInt()
                        clampLayoutWithinScreen(screenInfo)

                        val windowLayout = passThroughWindowLayout
                        val edgeCorrection = if (windowLayout != null) {
                            PointerDragEdgeCorrection.calculate(
                                x = layoutParams.x,
                                y = layoutParams.y,
                                screenWidth = screenInfo.width,
                                screenHeight = screenInfo.height,
                                layout = windowLayout,
                                handleWidth = handleWidth,
                                isRtl = isRTL,
                            )
                        } else {
                            PointerEdgeCorrection(0, 0)
                        }

                        pointerOffsetXState.value = edgeCorrection.x
                        pointerOffsetYState.value = edgeCorrection.y
                        clampLayoutWithinScreen(screenInfo)
                        updateLayout(applicationContext)

                        val x = layoutParams.x + handleCenterX + edgeCorrection.x
                        val y = layoutParams.y + handleCenterY + edgeCorrection.y
                        viewModel.updatePointerPosition(pointerSide, Point(x, y))
                    }

                    MotionEvent.ACTION_UP -> {
                        viewModel.motionEventFlow.value = event.action
                        (targetView as? TargetIconNativeView)?.setPointerVisible(false)
                        repositionWithinScreen(applicationContext)
                        restorePointerInteractionWindows()
                        isDraggingHandle = false
                    }

                    MotionEvent.ACTION_CANCEL -> {
                        viewModel.motionEventFlow.value = MotionEvent.ACTION_UP
                        (targetView as? TargetIconNativeView)?.setPointerVisible(false)
                        restorePointerInteractionWindows()
                        isDraggingHandle = false
                    }
                }
                return true
            }
        }
    }

    override suspend fun cast(applicationContext: Context) {
        castWithMode(applicationContext, false)
    }

    private fun updatePassThroughWindowLayout(
        context: Context,
        windowLayout: PointerPassThroughWindowLayout,
    ) {
        passThroughWindowLayout = windowLayout

        if (
            viewWidth == windowLayout.touchableWidth
            && viewHeight == windowLayout.touchableHeight
            && handleCenterX == windowLayout.handleCenterX
            && handleCenterY == windowLayout.handleCenterY
        ) {
            updateTargetLayout(context)
            return
        }

        val preserveHandlePosition = ::layoutParams.isInitialized && isRunning.get()
        val oldHandleX = if (preserveHandlePosition) layoutParams.x + handleCenterX else 0
        val oldHandleY = if (preserveHandlePosition) layoutParams.y + handleCenterY else 0

        viewWidth = windowLayout.touchableWidth
        viewHeight = windowLayout.touchableHeight
        handleCenterX = windowLayout.handleCenterX
        handleCenterY = windowLayout.handleCenterY

        if (preserveHandlePosition) {
            layoutParams.width = viewWidth
            layoutParams.height = viewHeight
            layoutParams.x = oldHandleX - handleCenterX
            layoutParams.y = oldHandleY - handleCenterY
            updateLayout(context)
        }
    }

    private fun updateTargetLayout(
        context: Context,
        edgeCorrectionX: Int = pointerOffsetXState.value,
        edgeCorrectionY: Int = pointerOffsetYState.value,
    ) {
        val windowLayout = passThroughWindowLayout ?: return
        if (!::layoutParams.isInitialized || !::targetLayoutParams.isInitialized) return

        targetLayoutParams.width = windowLayout.targetIconWindowWidth
        targetLayoutParams.height = windowLayout.targetIconWindowHeight
        targetLayoutParams.x = layoutParams.x + windowLayout.targetIconTopLeftX(edgeCorrectionX)
        targetLayoutParams.y = layoutParams.y + windowLayout.targetIconTopLeftY(edgeCorrectionY)

        val localTargetView = targetView
        if (localTargetView?.isAttachedToWindow == true) {
            try {
                (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
                    .updateViewLayout(localTargetView, targetLayoutParams)
            } catch (e: IllegalArgumentException) {
                Timber.tag(TAG).e(e, "targetView updateViewLayout failed")
            }
        }
    }

    private fun updatePointerInteractionWindowVisibility(visible: Boolean) {
        updateHandleWindowVisibility(visible)
        updateTargetWindowVisibility(visible)
    }

    private fun updateHandleWindowVisibility(visible: Boolean) {
        val localView = view ?: return
        if (!handleWindowVisibilityState.markIfChanged(visible)) return
        val alpha = OverlayWindowAlpha.forTouchableVisibility(visible)
        localView.alpha = alpha
        localView.visibility = if (visible) View.VISIBLE else View.GONE
        if (::layoutParams.isInitialized) {
            layoutParams.alpha = alpha
            layoutParams.flags = if (visible) {
                layoutParams.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            } else {
                layoutParams.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            }
            if (isServiceInitialized() && localView.isAttachedToWindow) {
                updateLayout(overlayService.applicationContext)
            }
        }
    }

    private fun updateTargetWindowVisibility(visible: Boolean) {
        val localTargetView = targetView ?: return
        if (!targetWindowVisibilityState.markIfChanged(visible)) return
        localTargetView.alpha = OverlayWindowAlpha.forTouchableVisibility(visible)
        localTargetView.visibility = if (visible) View.VISIBLE else View.GONE
        if (::targetLayoutParams.isInitialized) {
            targetLayoutParams.alpha = OverlayWindowAlpha.forPassThroughVisibility(visible)
            if (isServiceInitialized() && localTargetView.isAttachedToWindow) {
                try {
                    (overlayService.applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
                        .updateViewLayout(localTargetView, targetLayoutParams)
                } catch (e: IllegalArgumentException) {
                    Timber.tag(TAG).e(e, "targetView visibility updateViewLayout failed")
                }
            }
        }
    }

    override fun updateLayout(applicationContext: Context) {
        super.updateLayout(applicationContext)
        updateTargetLayout(applicationContext)
    }

    suspend fun castWithMode(applicationContext: Context, dualPointerMode: Boolean) {
        this.dualPointerMode = dualPointerMode
        var screenInfo: ScreenInfo = ScreenInfoHolder.get()
        if (PointerScreenInfoRefreshPolicy.requiresRefresh(screenInfo.width, screenInfo.height)) {
            ScreenInfoHolder.updateScreenInfoInService(applicationContext)
            screenInfo = ScreenInfoHolder.get()
        }
        handleWidth = applicationContext.resources.getDimensionPixelSize(R.dimen.target_handle_width)
        pointerDimen = applicationContext.resources.getDimensionPixelSize(R.dimen.target_pointer_dimen)
        pointerThumbSpace = applicationContext.resources.getDimensionPixelSize(R.dimen.target_handle_pointer_thumb_space)
        val defaultTargetFromHandleOffset = PointerOffset.defaultTargetFromHandleOffset(
            pointerDimen = pointerDimen,
            handleWidth = handleWidth,
            pointerThumbSpace = pointerThumbSpace,
        )
        val windowLayout = PointerPassThroughWindowLayout.fromTargetFromHandleOffset(
            pointerDimen = pointerDimen,
            handleWidth = handleWidth,
            targetFromHandleOffset = defaultTargetFromHandleOffset,
        )
        passThroughWindowLayout = windowLayout
        viewWidth = windowLayout.touchableWidth
        viewHeight = windowLayout.touchableHeight
        handleCenterX = windowLayout.handleCenterX
        handleCenterY = windowLayout.handleCenterY
//        Timber.tag(TAG).d("viewWidth $viewWidth")
//        Timber.tag(TAG).d("viewHeight $viewHeight")
//        Timber.tag(TAG).d("pointerDimen $pointerDimen")

        layoutParams = WindowManager.LayoutParams(
            viewWidth,
            viewHeight,
            startX(screenInfo),
            startY(screenInfo),
            overlayWindowType(),
            touchableHandleWindowFlags(),
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        targetLayoutParams = WindowManager.LayoutParams(
            windowLayout.targetIconWindowWidth,
            windowLayout.targetIconWindowHeight,
            0,
            0,
            overlayWindowType(),
            passThroughTargetWindowFlags(),
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            alpha = OverlayWindowAlpha.forPassThroughVisibility(true)
        }
        updateTargetLayout(applicationContext)

        if (isRunning.get()) {
            setAtStartPosition(applicationContext)
            ensureTargetIconView(applicationContext)
        } else {
            super.cast(applicationContext)
            ensureTargetIconView(applicationContext)
        }
        restartNativeStateCollectors(applicationContext)
    }

    private fun setAtStartPosition(context: Context) {
        val screenInfo: ScreenInfo = ScreenInfoHolder.get()
        SayHereView.INSTANCE.clear()
        cancelDockDragHandle()
        PointerDefaultPlacement.edgeCorrection().let { edgeCorrection ->
            pointerOffsetXState.value = edgeCorrection.x
            pointerOffsetYState.value = edgeCorrection.y
        }
        layoutParams.x = startX(screenInfo)
        layoutParams.y = startY(screenInfo)
        updateLayout(context)
    }

    private fun startX(screenInfo: ScreenInfo): Int {
        passThroughWindowLayout?.let { windowLayout ->
            return PointerDefaultPlacement.layoutTopLeft(
                screenWidth = screenInfo.width,
                screenHeight = screenInfo.height,
                layout = windowLayout,
                side = pointerSide,
                dualPointerMode = dualPointerMode,
            ).first
        }
        val x = screenInfo.width / 2 - handleCenterX
        return x.coerceIn(0, (screenInfo.width - viewWidth).coerceAtLeast(0))
    }

    private fun startY(screenInfo: ScreenInfo): Int {
        passThroughWindowLayout?.let { windowLayout ->
            return PointerDefaultPlacement.layoutTopLeft(
                screenWidth = screenInfo.width,
                screenHeight = screenInfo.height,
                layout = windowLayout,
                side = pointerSide,
                dualPointerMode = dualPointerMode,
            ).second
        }
        return screenInfo.height * 2 / 3 - handleCenterY
    }

    private fun overlayWindowType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    private fun touchableHandleWindowFlags(): Int {
        return WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
    }

    private fun passThroughTargetWindowFlags(): Int {
        return touchableHandleWindowFlags() or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
    }

    private fun ensureTargetIconView(applicationContext: Context) {
        if (!::targetLayoutParams.isInitialized || !isServiceInitialized()) return

        launchInOverlayViewCoroutineScope {
            if (targetView == null) {
                targetView = TargetIconNativeView(overlayService).apply {
                    render(lastTargetIconRenderState)
                }
            }

            updateTargetLayout(applicationContext)
            val localTargetView = targetView ?: return@launchInOverlayViewCoroutineScope
            if (!localTargetView.isAttachedToWindow) {
                try {
                    (applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
                        .addView(localTargetView, targetLayoutParams)
                } catch (e: IllegalStateException) {
                    Timber.tag(TAG).e(e, "targetView addView failed")
                }
            }
        }
    }

    private fun removeTargetIconView() {
        val localTargetView = targetView ?: return
        if (isServiceInitialized()) {
            try {
                (overlayService.applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
                    .removeView(localTargetView)
            } catch (e: Exception) {
                try {
                    (overlayService.applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
                        .removeViewImmediate(localTargetView)
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "targetView removeView failed")
                }
            }
        }
        targetView = null
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
            viewModel.activePointerSideFlow.value = pointerSide
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

    private var exposeAnimator: ValueAnimator? = null

    private var repositionAnimation: SpringAnimation? = null

    /**
     */
    private fun onConfigurationChanged(context: Context) {
        Timber.tag(TAG).d("#### onConfigurationChanged() ####")
        if (pointerSide == PointerSide.LEFT) {
            viewModel.restartCaptureRepository()
            launchInOverlayViewCoroutineScope {
                recastForCurrentConfiguration(context)
            }
        }
    }

    /**
     */
    private fun repositionWithinScreen(applicationContext: Context) {
        val screenInfo: ScreenInfo = ScreenInfoHolder.get()
        val loc = IntArray(2)
        view?.getLocationOnScreen(loc)

        val windowLayout = passThroughWindowLayout
        val visualLeft = windowLayout?.visualLeft(pointerOffsetXState.value) ?: 0
        val visualTop = windowLayout?.visualTop(pointerOffsetYState.value) ?: 0
        val visualRight = windowLayout?.visualRight(pointerOffsetXState.value) ?: viewWidth
        val visualBottom = windowLayout?.visualBottom(pointerOffsetYState.value) ?: viewHeight

        val topLeft = Point(loc[0] + visualLeft, loc[1] + visualTop)
        val topRight = Point(loc[0] + visualRight, loc[1] + visualTop)
        val bottomLeft = Point(loc[0] + visualLeft, loc[1] + visualBottom)

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
            cancelRepositionAnimation()
            repositionAnimation = SpringAnimation(FloatValueHolder()).apply {
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
                    if (repositionAnimation === animation) {
                        repositionAnimation = null
                    }
                }
            }
            repositionAnimation?.start()
        }
    }

    private fun clampLayoutWithinScreen(screenInfo: ScreenInfo) {
        val windowLayout = passThroughWindowLayout ?: return
        val (clampedX, clampedY) = PointerWindowBounds.clampLayoutPosition(
            x = layoutParams.x,
            y = layoutParams.y,
            screenWidth = screenInfo.width,
            screenHeight = screenInfo.height,
            layout = windowLayout,
            edgeCorrectionX = pointerOffsetXState.value,
            edgeCorrectionY = pointerOffsetYState.value,
        )
        layoutParams.x = clampedX
        layoutParams.y = clampedY
    }

    private fun cancelRepositionAnimation() {
        repositionAnimation?.cancel()
        repositionAnimation = null
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
        val visualTop = passThroughWindowLayout?.visualTop() ?: 0
        val targetVisualTop = (screenInfo.height - context.resources.getDimensionPixelSize(R.dimen.target_handle_height) - context.resources.getDimensionPixelSize(R.dimen.target_handle_width)) / 2
        val targetY = targetVisualTop - visualTop

        val startX = layoutParams.x
        val startY = layoutParams.y

        val deltaX: Double = targetX - startX
        val deltaY = targetY - startY

        dockAnimator?.cancel()

        var dockCanceled = false
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
                override fun onAnimationStart(animation: Animator) {
                    dockCanceled = false
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (!dockCanceled) {
                        exposeTargetHandleKnob(context, start)
                        viewModel.dockStateFlow.value = true
                    }
                    if (dockAnimator === animation) {
                        dockAnimator = null
                    }
                }

                override fun onAnimationCancel(animation: Animator) {
                    dockCanceled = true
                    if (dockAnimator === animation) {
                        dockAnimator = null
                    }
                }

                override fun onAnimationRepeat(animation: Animator) {}
            })

            start()
        }
    }

    private fun cancelDockDragHandle(context: Context? = null) {
        dragHandleDockingJob?.cancel()
        dockAnimator?.cancel()
        exposeAnimator?.cancel()
        dockAnimator = null
        exposeAnimator = null
        viewModel.dockStateFlow.value = false
        if (context != null && ::layoutParams.isInitialized) {
            clampLayoutWithinScreen(ScreenInfoHolder.get())
            if (isServiceInitialized()) {
                updateLayout(context)
            }
        }
    }

    /**
     */
    private fun exposeTargetHandleKnob(context: Context, start: Boolean) {
        Timber.tag(TAG).i("------------- exposeTargetHandleKnob [$start]]")
        val startX = layoutParams.x
        val hideDepth = (context.resources.getDimensionPixelSize(R.dimen.target_handle_width) * if (start) .30 else .32).toInt()
        val deltaX: Int = if (start) hideDepth else -hideDepth
        exposeAnimator?.cancel()
        exposeAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
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
                    if (exposeAnimator === animation) {
                        exposeAnimator = null
                    }
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

                override fun onAnimationCancel(animation: Animator) {
                    if (exposeAnimator === animation) {
                        exposeAnimator = null
                    }
                }

                override fun onAnimationRepeat(animation: Animator) {}
            })

            start()
        }
    }

    override fun clear() {
        nativeStateCollectorJobs.forEach { it.cancel() }
        nativeStateCollectorJobs = emptyList()
        lastTargetIconRenderState = TargetIconRenderState()
        cancelDockDragHandle()
        cancelRepositionAnimation()
        handleWindowVisibilityState.reset()
        targetWindowVisibilityState.reset()
        removeTargetIconView()
        super.clear()
    }
}

object TargetCaptureTransparency {
    private const val FRAME_COMMIT_TIMEOUT_MS = 80L

    suspend fun <T> withTransparentTargets(block: suspend () -> T): T {
        return withTransparentTargetsAfterCommit { block() }
    }

    suspend fun <T> withTransparentTargetsAfterCommit(block: suspend (minimumImageTimestampNs: Long?) -> T): T {
        return withTransparentTargetsAfterCommit(TargetHandleView.targetIconViews(), block)
    }

    internal suspend fun <T> withTransparentTargets(
        targetViews: List<TargetIconNativeView>,
        block: suspend () -> T,
    ): T {
        return withTransparentTargetsAfterCommit(targetViews) { block() }
    }

    internal suspend fun <T> withTransparentTargetsAfterCommit(
        targetViews: List<TargetIconNativeView>,
        block: suspend (minimumImageTimestampNs: Long?) -> T,
    ): T {
        val visibleTargets = withContext(Dispatchers.Main.immediate) {
            targetViews
                .filter { it.isAttachedToWindow && it.isShown && it.width > 0 && it.height > 0 }
                .toList()
        }
        if (visibleTargets.isEmpty()) return block(null)

        return try {
            val minimumImageTimestampNs = System.nanoTime()
            withContext(Dispatchers.Main.immediate) {
                visibleTargets.forEach { it.setCaptureTransparent(true) }
            }
            coroutineScope {
                visibleTargets
                    .map { targetView ->
                        async(Dispatchers.Main.immediate) {
                            withTimeoutOrNull(FRAME_COMMIT_TIMEOUT_MS) {
                                targetView.awaitCaptureTransparentFrameCommit()
                            }
                        }
                    }
                    .awaitAll()
            }
            block(minimumImageTimestampNs)
        } finally {
            withContext(NonCancellable + Dispatchers.Main.immediate) {
                visibleTargets.forEach { it.setCaptureTransparent(false) }
            }
        }
    }
}

internal class TargetIconNativeView(context: Context) : View(context) {
    private val pointerDrawable: Drawable? = context.getDrawable(R.drawable.drag_pointer)?.mutate()
    private val selectionPointerDrawable: Drawable? = context.getDrawable(R.drawable.drag_selection_pointer)?.mutate()
    private val progressDimen = context.resources.getDimensionPixelSize(R.dimen.target_pointer_progress_dimen)
    private val progressRect = RectF()
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 1.8f * context.resources.displayMetrics.density
        color = Color.rgb(0x48, 0xba, 0xef)
    }

    private var renderState = TargetIconRenderState()
    private var captureTransparent = false
    private var progressRotation = 0f
    private var progressAnimator: ValueAnimator? = null

    fun render(state: TargetIconRenderState) {
        if (renderState == state) return
        renderState = state
        updateProgressAnimator()
        invalidate()
    }

    fun setCaptureTransparent(transparent: Boolean) {
        if (captureTransparent == transparent) {
            postInvalidateOnAnimation()
            return
        }
        captureTransparent = transparent
        postInvalidateOnAnimation()
    }

    suspend fun awaitCaptureTransparentFrameCommit() {
        if (!isAttachedToWindow || !isShown || width <= 0 || height <= 0) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            awaitFrameCommit()
        } else {
            awaitDrawThenAnimationFrame()
        }
    }

    private suspend fun awaitFrameCommit() {
        suspendCancellableCoroutine { continuation ->
            var resumed = false
            fun resumeOnce() {
                if (resumed) return
                resumed = true
                continuation.resume(Unit)
            }

            continuation.invokeOnCancellation { resumed = true }
            viewTreeObserver.registerFrameCommitCallback {
                resumeOnce()
            }
            postInvalidateOnAnimation()
        }
    }

    private suspend fun awaitDrawThenAnimationFrame() {
        suspendCancellableCoroutine { continuation ->
            var resumed = false
            var listener: ViewTreeObserver.OnDrawListener? = null

            fun resumeOnce() {
                if (resumed) return
                resumed = true
                continuation.resume(Unit)
            }

            listener = ViewTreeObserver.OnDrawListener {
                post {
                    listener?.let { drawListener ->
                        if (viewTreeObserver.isAlive) {
                            viewTreeObserver.removeOnDrawListener(drawListener)
                        }
                    }
                    postOnAnimation { resumeOnce() }
                }
            }

            continuation.invokeOnCancellation {
                resumed = true
                post {
                    listener?.let { drawListener ->
                        if (viewTreeObserver.isAlive) {
                            viewTreeObserver.removeOnDrawListener(drawListener)
                        }
                    }
                }
            }

            viewTreeObserver.addOnDrawListener(listener)
            postInvalidateOnAnimation()
        }
    }

    fun setPointerVisible(visible: Boolean) {
        render(renderState.copy(pointerVisible = visible))
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateProgressAnimator()
    }

    override fun onDetachedFromWindow() {
        stopProgressAnimator()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val contentAlpha = if (captureTransparent) {
            TargetIconRenderPolicy.CAPTURE_ALPHA
        } else {
            TargetIconRenderPolicy.contentAlpha(renderState)
        }
        if (renderState.progressVisible) {
            drawProgress(canvas, contentAlpha)
        }
        if (renderState.pointerVisible) {
            drawPointer(canvas, contentAlpha)
        }
    }

    private fun drawProgress(canvas: Canvas, contentAlpha: Float) {
        val left = (width - progressDimen) / 2f
        val top = (height - progressDimen) / 2f
        progressRect.set(left, top, left + progressDimen, top + progressDimen)
        progressPaint.alpha = (255 * contentAlpha).roundToInt().coerceIn(0, 255)
        canvas.drawArc(progressRect, progressRotation, 270f, false, progressPaint)
    }

    private fun drawPointer(canvas: Canvas, contentAlpha: Float) {
        val drawable = when (renderState.tint) {
            TargetIconTint.NONE -> pointerDrawable
            TargetIconTint.SELECT,
            TargetIconTint.FIXED_AREA -> selectionPointerDrawable ?: pointerDrawable
        } ?: return

        drawable.bounds = android.graphics.Rect(0, 0, width, height)
        drawable.alpha = (255 * contentAlpha).roundToInt().coerceIn(0, 255)
        drawable.colorFilter = when (renderState.tint) {
            TargetIconTint.SELECT -> PorterDuffColorFilter(
                Color.argb(0x88, 0x3b, 0x6f, 0xdb),
                PorterDuff.Mode.SRC_IN
            )

            TargetIconTint.FIXED_AREA -> PorterDuffColorFilter(
                Color.argb(0x88, 0x00, 0x66, 0x00),
                PorterDuff.Mode.SRC_IN
            )

            TargetIconTint.NONE -> null
        }

        val saveCount = canvas.save()
        if (renderState.writingRtl) {
            canvas.scale(-1f, 1f, width / 2f, height / 2f)
        }
        drawable.draw(canvas)
        canvas.restoreToCount(saveCount)
        drawable.colorFilter = null
    }

    private fun updateProgressAnimator() {
        if (renderState.progressVisible && isAttachedToWindow) {
            startProgressAnimator()
        } else {
            stopProgressAnimator()
        }
    }

    private fun startProgressAnimator() {
        if (progressAnimator != null) return
        progressAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 900L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { animator ->
                progressRotation = animator.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun stopProgressAnimator() {
        progressAnimator?.cancel()
        progressAnimator = null
        progressRotation = 0f
    }
}
