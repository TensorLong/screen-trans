package com.galaxy.airviewdictionary.ui.screen.overlay.targethandle

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.galaxy.airviewdictionary.R
import com.galaxy.airviewdictionary.core.OverlayService
import com.galaxy.airviewdictionary.data.local.preference.PreferenceRepository
import com.galaxy.airviewdictionary.data.local.screen.ScreenInfo
import com.galaxy.airviewdictionary.data.local.screen.ScreenInfoHolder
import com.galaxy.airviewdictionary.extensions.toPx
import com.galaxy.airviewdictionary.ui.screen.overlay.Event
import com.galaxy.airviewdictionary.ui.screen.overlay.OverlayView
import javax.inject.Singleton


/**
 * 번역 뷰
 */
@Singleton
open class SayHereView : OverlayView() {

    companion object {
        val INSTANCE: SayHereView by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { SayHereView() }
    }

    private lateinit var targetHandleViewModel: TargetHandleViewModel

    override lateinit var layoutParams: WindowManager.LayoutParams

    private val start = mutableStateOf(true)

    override val composable: @Composable () -> Unit = @Composable {
        if (start.value) SayHereL() else SayHereR()
    }

    override val touchListener: (Context) -> View.OnTouchListener? = {
        object : View.OnTouchListener {
            @SuppressLint("ClickableViewAccessibility")
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                targetHandleViewModel.preferenceRepository.update(
                    if (start.value) PreferenceRepository.IS_SAY_HERE_L_SHOWN else PreferenceRepository.IS_SAY_HERE_R_SHOWN,
                    true
                )
                clear()
                return true
            }
        }
    }

    suspend fun cast(applicationContext: Context, start: Boolean, position: Point) {
        val screenInfo: ScreenInfo = ScreenInfoHolder.get()
        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (start) position.x else screenInfo.width,
            position.y + 38.dp.toPx(applicationContext),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
        this.start.value = start
        super.cast(applicationContext)
    }

    override fun onServiceConnected(overlayService: OverlayService) {
        targetHandleViewModel = overlayService.getTargetHandleViewModel()
        super.onServiceConnected(overlayService)
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
}


@Composable
fun SayHereR() {
    Box(
        modifier = Modifier
            .padding(16.dp)
            .wrapContentSize(),
    ) {
        Canvas(
            modifier = Modifier.size(140.dp, 50.dp)
        ) {
            val width = size.width
            val height = size.height
            val cornerRadius = 16.dp.toPx() // Radius for rounded corners
            val tailWidth = 36f
            val tailHeight = 24f
            val tailYOffset = 14.dp.toPx() // Shift the tail downward by 10.dp

            // Create the path for the speech bubble
            val bubblePath = Path().apply {
                // Top-left corner
                moveTo(cornerRadius, 0f)
                arcTo(
                    rect = Rect(0f, 0f, cornerRadius * 2, cornerRadius * 2),
                    startAngleDegrees = 180f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false
                )
                lineTo(width - tailWidth - cornerRadius, 0f) // Top edge before top-right corner

                // Top-right corner
                arcTo(
                    rect = Rect(
                        width - tailWidth - cornerRadius * 2,
                        0f,
                        width - tailWidth,
                        cornerRadius * 2
                    ),
                    startAngleDegrees = 270f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false
                )
                lineTo(width - tailWidth, tailHeight + tailYOffset) // Top of the tail
                lineTo(width, tailHeight * 1.0f + tailYOffset) // Tip of the tail
                lineTo(width - tailWidth, tailHeight * 2 + tailYOffset) // Bottom of the tail
                lineTo(width - tailWidth, height - cornerRadius) // Right edge before bottom-right corner

                // Bottom-right corner
                arcTo(
                    rect = Rect(
                        width - tailWidth - cornerRadius * 2,
                        height - cornerRadius * 2,
                        width - tailWidth,
                        height
                    ),
                    startAngleDegrees = 0f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false
                )
                lineTo(cornerRadius, height) // Bottom edge before bottom-left corner

                // Bottom-left corner
                arcTo(
                    rect = Rect(0f, height - cornerRadius * 2, cornerRadius * 2, height),
                    startAngleDegrees = 90f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false
                )
                lineTo(0f, cornerRadius) // Left edge before top-left corner

                close() // Complete the path
            }

            // Draw shadow
            drawIntoCanvas { canvas ->
                val shadowPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.GRAY // Gray shadow
                    setShadowLayer(8f, 4f, 4f, android.graphics.Color.GRAY) // Reduced thickness and offset
                }
                canvas.nativeCanvas.drawPath(bubblePath.asAndroidPath(), shadowPaint)
            }

            // Draw the bubble
            drawPath(
                path = bubblePath,
                color = Color.White
            )
        }

        Row(
            modifier = Modifier
                .wrapContentSize()
                .align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_drag_handle),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                colorFilter = ColorFilter.tint(Color(0xFF6a91b2))
            )

            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = "I'm here !",
                color = Color.Black,
            )
            Spacer(modifier = Modifier.width(11.dp))
        }
    }
}

@Composable
fun SayHereL() {
    Box(
        modifier = Modifier
            .padding(16.dp)
            .wrapContentSize(),
    ) {
        Canvas(
            modifier = Modifier.size(140.dp, 50.dp)
        ) {
            val width = size.width
            val height = size.height
            val cornerRadius = 16.dp.toPx() // Radius for rounded corners
            val tailWidth = 36f
            val tailHeight = 24f
            val tailYOffset = 14.dp.toPx() // Shift the tail downward by 10.dp

            // Create the path for the speech bubble with the tail on the left
            val bubblePath = Path().apply {
                // Top-left corner
                moveTo(tailWidth, 0f)
                arcTo(
                    rect = Rect(tailWidth, 0f, tailWidth + cornerRadius * 2, cornerRadius * 2),
                    startAngleDegrees = 180f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false
                )
                lineTo(width - cornerRadius, 0f) // Top edge before top-right corner

                // Top-right corner
                arcTo(
                    rect = Rect(
                        width - cornerRadius * 2,
                        0f,
                        width,
                        cornerRadius * 2
                    ),
                    startAngleDegrees = 270f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false
                )
                lineTo(width, height - cornerRadius) // Right edge before bottom-right corner

                // Bottom-right corner
                arcTo(
                    rect = Rect(
                        width - cornerRadius * 2,
                        height - cornerRadius * 2,
                        width,
                        height
                    ),
                    startAngleDegrees = 0f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false
                )
                lineTo(tailWidth + cornerRadius, height) // Bottom edge before bottom-left corner

                // Bottom-left corner
                arcTo(
                    rect = Rect(
                        tailWidth,
                        height - cornerRadius * 2,
                        tailWidth + cornerRadius * 2,
                        height
                    ),
                    startAngleDegrees = 90f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false
                )
                lineTo(tailWidth, tailHeight * 2 + tailYOffset) // Bottom of the tail
                lineTo(0f, tailHeight * 1.0f + tailYOffset) // Tip of the tail
                lineTo(tailWidth, tailHeight + tailYOffset) // Top of the tail

                close() // Complete the path
            }

            // Draw shadow
            drawIntoCanvas { canvas ->
                val shadowPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.GRAY // Gray shadow
                    setShadowLayer(8f, 4f, 4f, android.graphics.Color.GRAY) // Reduced thickness and offset
                }
                canvas.nativeCanvas.drawPath(bubblePath.asAndroidPath(), shadowPaint)
            }

            // Draw the bubble
            drawPath(
                path = bubblePath,
                color = Color.White
            )
        }

        Row(
            modifier = Modifier
                .wrapContentSize()
                .align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.width(12.dp))

            Image(
                painter = painterResource(id = R.drawable.ic_drag_handle),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                colorFilter = ColorFilter.tint(Color(0xFF6a91b2))
            )

            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = "I'm here !",
                color = Color.Black,
            )
        }
    }
}


















