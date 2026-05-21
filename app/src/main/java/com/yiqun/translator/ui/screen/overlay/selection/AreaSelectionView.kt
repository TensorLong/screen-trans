package com.yiqun.translator.ui.screen.overlay.selection


import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.Rect
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yiqun.translator.R
import com.yiqun.translator.core.OverlayService
import com.yiqun.translator.data.local.capture.CapturePreventedException
import com.yiqun.translator.data.local.capture.NoMediaProjectionTokenException
import com.yiqun.translator.data.local.vision.TextDetectMode
import com.yiqun.translator.data.local.vision.WritingDirection
import com.yiqun.translator.extensions.setFromPoints
import com.yiqun.translator.extensions.vibrate
import com.yiqun.translator.data.remote.translation.Language
import com.yiqun.translator.data.local.capture.CaptureResponse
import com.yiqun.translator.data.local.vision.model.VisionResponse
import com.yiqun.translator.ui.screen.overlay.OverlayView
import com.yiqun.translator.ui.screen.overlay.targethandle.TargetHandleViewModel
import com.yiqun.translator.ui.screen.overlay.targethandle.TranslateStatus
import com.yiqun.translator.ui.screen.overlay.translation.TranslationView
import com.yiqun.translator.ui.screen.permissions.ScreenCapturePermissionRequesterActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Singleton
import kotlin.math.max


/**
 */
@Singleton
open class AreaSelectionView : OverlayView() {

    companion object {
        val INSTANCE: AreaSelectionView by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { AreaSelectionView() }
    }

    private lateinit var targetHandleViewModel: TargetHandleViewModel

    override lateinit var layoutParams: WindowManager.LayoutParams

    private lateinit var layoutTopCenter: Point

    private var translateJob: Job? = null

    override val composable: @Composable () -> Unit = @Composable {
        val localView = LocalView.current
        val context = LocalContext.current
        val resources = context.resources
        val lifecycleOwner = LocalLifecycleOwner.current

        val isDarkMode = isSystemInDarkTheme()
        val visionTextColor = colorResource(if (isDarkMode) R.color.vision_text_color_dark else R.color.vision_text_color)

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
            initialValue = TextDetectMode.SELECT
        )

        if (textDetectMode != TextDetectMode.SELECT) {
            clear()
        }

        val isWritingRtl = remember { mutableStateOf(false) }
        val writingDirection = remember { mutableStateOf(WritingDirection.LTR) }
        val sourceLanguageCode by targetHandleViewModel.preferenceRepository.sourceLanguageCodeFlow.collectAsStateWithLifecycle(
            lifecycle = lifecycleOwner.lifecycle,
            initialValue = "auto"
        )
        LaunchedEffect(sourceLanguageCode) {
            writingDirection.value = Language.writingDirection(sourceLanguageCode, false)
            isWritingRtl.value = writingDirection.value == WritingDirection.RTL
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

        val pointerStoppedPosition: Point? by targetHandleViewModel.pointerStoppedPositionFlow.collectAsStateWithLifecycle(
            lifecycle = lifecycleOwner.lifecycle,
            initialValue = null
        )

        val translationViewLiveState: Boolean by TranslationView.liveStateFlow.collectAsStateWithLifecycle()

        fun startSelection() {
            translateJob?.cancel()
            selectionStarted.value = true
            targetHandleViewModel.areaSelectingStateFlow.value = true
            haptic()
        }

        fun resetSelection(currentPosition: Point) {
            translateJob?.cancel()
            targetHandleViewModel.cancelCapture()

            selectedCompleted.value = false
            targetHandleViewModel.areaSelectingStateFlow.value = false
            selectionStarted.value = false

            layoutParams.x = currentPosition.x
            layoutParams.y = currentPosition.y
            layoutParams.width = layoutParams.x + 1
            layoutParams.height = currentPosition.y + 1
            updateLayout(context)

            view?.post {
                layoutTopCenter = currentPosition
            }
        }

        fun completeSelection(selectedArea: Rect) {
            selectedCompleted.value = true
            targetHandleViewModel.areaSelectingStateFlow.value = false
            haptic()
            requestTranslate(context, selectedArea)
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
            if (isWritingRtl.value) {
                if (currentPosition.y + stoppedDistancePx / 2 < layoutTopCenter.y
                    || currentPosition.x - stoppedDistancePx / 2 > layoutTopCenter.x
                ) {
                    resetSelection(currentPosition)
                }
                else {
                    if (selectionStarted.value) {
                        if (_pointerPosition.value != null) {
                            if (currentPosition.y + stoppedDistancePx / 2 < _pointerPosition.value!!.y
                                || currentPosition.x - stoppedDistancePx / 2 > _pointerPosition.value!!.x
                            ) {
                                resetSelection(currentPosition)
                            }
                        }
                    }
                    else {
                        layoutTopCenter = currentPosition
                    }
                }
            } else {
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
            }

            _pointerPosition.value = currentPosition

            val r: Rect = Rect().apply {
                setFromPoints(layoutTopCenter, currentPosition)
            }

            layoutParams.x = r.left
            layoutParams.y = r.top
            layoutParams.width = r.width()
            layoutParams.height = r.height()
            updateLayout(context)
        } ?: clear()

        if (selectionStarted.value) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(visionTextColor.copy(alpha = if (isDarkMode) 0.18f else 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                if (selectedCompleted.value && !translationViewLiveState) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(dimensionResource(id = R.dimen.target_pointer_progress_dimen)),
                        color = Color(0xFF48baef),
                        strokeWidth = 2.2.dp
                    )
                }
            }
        }

//        Box(
//            modifier = Modifier
//                .fillMaxSize()
//                .background( Color(0x44ffff11)),
//        )
    }

    override fun onServiceConnected(overlayService: OverlayService) {
        targetHandleViewModel = overlayService.getTargetHandleViewModel()
        super.onServiceConnected(overlayService)
    }

    open suspend fun cast(
        applicationContext: Context,
        startPosition: Point
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

    override fun clear() {
        translateJob?.cancel()
        targetHandleViewModel.areaSelectingStateFlow.value = false
        targetHandleViewModel.translateStatusFlow.value = TranslateStatus.Idle
        super.clear()
    }

    private fun requestTranslate(context: Context, selectedArea: Rect) {
        translateJob?.cancel()
        translateJob = launchInOverlayViewCoroutineScope {
            val captureResponse: CaptureResponse = targetHandleViewModel.captureRepository.request(
                AreaCapturePolicy.captureRect(selectedArea)
            )
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
                return@launchInOverlayViewCoroutineScope
            }

            val selectedAreaBitmap = captureResponse.bitmap

//                    TestCapturedActivity.start(context, selectedAreaBitmap)

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
            Timber.tag(TAG).d("$selectedArea sourceLanguageCode $sourceLanguageCode")
            Timber.tag(TAG).d("$selectedArea visionResponse $visionResponse")

            if (visionResponse !is VisionResponse.Success) {
                return@launchInOverlayViewCoroutineScope
            }

            targetHandleViewModel.visionResultFlow.value = visionResponse.result
        }
    }
}

object AreaCapturePolicy {
    data class Bounds(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    )

    fun captureBounds(left: Int, top: Int, right: Int, bottom: Int): Bounds {
        return Bounds(left, top, right, bottom)
    }

    fun captureRect(selectedArea: Rect): Rect {
        val bounds = captureBounds(
            left = selectedArea.left,
            top = selectedArea.top,
            right = selectedArea.right,
            bottom = selectedArea.bottom,
        )
        return Rect(bounds.left, bounds.top, bounds.right, bounds.bottom)
    }

    fun coordinateOffsetX(bounds: Bounds): Int = bounds.left

    fun coordinateOffsetY(bounds: Bounds): Int = bounds.top

    fun coordinateOffsetX(captureRect: Rect): Int = coordinateOffsetX(
        captureBounds(captureRect.left, captureRect.top, captureRect.right, captureRect.bottom)
    )

    fun coordinateOffsetY(captureRect: Rect): Int = coordinateOffsetY(
        captureBounds(captureRect.left, captureRect.top, captureRect.right, captureRect.bottom)
    )
}

fun createOverlaidBitmap(originalBitmap: Bitmap, rect: Rect): Bitmap {
    val maxWidth = max(1, originalBitmap.width)
    val maxHeight = max(1, originalBitmap.height)

    val safeLeft = rect.left.coerceIn(0, maxWidth - 1)
    val safeTop = rect.top.coerceIn(0, maxHeight - 1)
    val safeRight = rect.right.coerceIn(safeLeft + 1, maxWidth)
    val safeBottom = rect.bottom.coerceIn(safeTop + 1, maxHeight)

    val safeWidth = safeRight - safeLeft
    val safeHeight = safeBottom - safeTop

    if (safeWidth <= 0 || safeHeight <= 0) {
        Timber.e(
            "createOverlaidBitmap: Invalid dimensions. " +
                    "rect=($rect), " +
                    "computed=(left=$safeLeft, top=$safeTop, right=$safeRight, bottom=$safeBottom), " +
                    "width=$safeWidth, height=$safeHeight"
        )
        return Bitmap.createBitmap(maxWidth, maxHeight, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.BLACK)
        }
    }

    return try {
        val croppedBitmap = Bitmap.createBitmap(originalBitmap, safeLeft, safeTop, safeWidth, safeHeight)

        val returnBitmap = Bitmap.createBitmap(maxWidth, maxHeight, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.BLACK)
        }

        val canvas = Canvas(returnBitmap)
        canvas.drawBitmap(croppedBitmap, safeLeft.toFloat(), safeTop.toFloat(), null)
        croppedBitmap.recycle()

        returnBitmap
    } catch (e: IllegalArgumentException) {
        Timber.e(e, "Bitmap.createBitmap failed in createOverlaidBitmap")
        Bitmap.createBitmap(maxWidth, maxHeight, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.BLACK)
        }
    }
}













