package com.yiqun.translator.ui.screen.overlay.detectmode

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.runtime.Composable
import com.yiqun.translator.R
import com.yiqun.translator.core.OverlayService
import com.yiqun.translator.data.local.screen.ScreenInfoHolder
import com.yiqun.translator.data.local.vision.TextDetectMode
import com.yiqun.translator.ui.screen.overlay.OverlayView
import com.yiqun.translator.ui.screen.overlay.menubar.MenuBarDropdownOptions
import com.yiqun.translator.ui.screen.overlay.menubar.MenuBarViewModel
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class DetectModeMenuView private constructor() : OverlayView() {

    companion object {
        val INSTANCE: DetectModeMenuView by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { DetectModeMenuView() }
    }

    private lateinit var viewModel: MenuBarViewModel

    override var layoutParams: WindowManager.LayoutParams = menuLayoutParams()

    override val composable: @Composable () -> Unit = @Composable {}

    override fun createView(overlayService: OverlayService): View {
        return DetectModeMenuNativeView(
            context = overlayService,
            dismiss = { clear() },
            updateTextDetectMode = { mode ->
                viewModel.updateTextDetectMode(mode)
                clear()
            },
        )
    }

    override fun onServiceConnected(overlayService: OverlayService) {
        viewModel = overlayService.getMenuBarViewModel()
        super.onServiceConnected(overlayService)
    }

    override suspend fun cast(applicationContext: Context) {
        cast(applicationContext, null, null)
    }

    suspend fun cast(applicationContext: Context, anchorCenterX: Int?, anchorCenterY: Int?) {
        layoutParams = menuLayoutParams().apply {
            val position = menuPosition(applicationContext, anchorCenterX, anchorCenterY)
            x = position.first
            y = position.second
        }
        super.cast(applicationContext, reattach = isRunning.get())
    }

    private fun menuLayoutParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
    }

    private fun menuPosition(
        context: Context,
        anchorCenterX: Int?,
        anchorCenterY: Int?,
    ): Pair<Int, Int> {
        val screenInfo = ScreenInfoHolder.get()
        val density = context.resources.displayMetrics.density
        val menuWidth = (240f * density).roundToInt()
        val rowHeight = (44f * density).roundToInt()
        val menuHeight = rowHeight * MenuBarDropdownOptions.textDetectModes.size + (12f * density).roundToInt()
        val margin = (12f * density).roundToInt()

        val fallbackX = ((screenInfo.width - menuWidth) / 2).coerceAtLeast(margin)
        val fallbackY = ((screenInfo.height - menuHeight) / 2).coerceAtLeast(margin)
        val rawX = if (anchorCenterX != null) anchorCenterX - menuWidth / 2 else fallbackX
        val handleRadius = context.resources.getDimensionPixelSize(R.dimen.target_handle_width) / 2
        val rawY = if (anchorCenterY != null) anchorCenterY - handleRadius - menuHeight - margin else fallbackY
        val maxX = (screenInfo.width - menuWidth - margin).coerceAtLeast(margin)
        val maxY = (screenInfo.height - menuHeight - margin).coerceAtLeast(margin)
        return rawX.coerceIn(margin, maxX) to rawY.coerceIn(margin, maxY)
    }
}

private class DetectModeMenuNativeView(
    context: Context,
    private val dismiss: () -> Unit,
    private val updateTextDetectMode: (TextDetectMode) -> Unit,
) : LinearLayout(context) {

    init {
        orientation = VERTICAL
        minimumWidth = dp(240)
        setPadding(0, dp(6), 0, dp(6))
        background = GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            setColor(if (isDarkMode()) Color.rgb(31, 31, 31) else Color.rgb(254, 254, 254))
            setStroke(dpFloat(0.4f).roundToInt(), if (isDarkMode()) Color.rgb(106, 106, 106) else Color.rgb(214, 214, 214))
        }
        elevation = dpFloat(3f)
        MenuBarDropdownOptions.textDetectModes.forEach { mode ->
            addView(modeRow(mode))
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
            dismiss()
            return false
        }
        return super.dispatchTouchEvent(event)
    }

    private fun modeRow(mode: TextDetectMode): View {
        return LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(44)
            setPadding(dp(14), 0, dp(14), 0)
            background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { updateTextDetectMode(mode) }
            addView(
                ImageView(context).apply {
                    setImageResource(mode.iconResourceId)
                    setColorFilter(contentColor())
                },
                LayoutParams(dp(20), dp(20)),
            )
            addView(
                TextView(context).apply {
                    text = mode.text
                    setTextColor(contentColor())
                    textSize = 15f
                    includeFontPadding = false
                    gravity = Gravity.CENTER_VERTICAL
                },
                LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                    marginStart = dp(12)
                },
            )
        }
    }

    private fun isDarkMode(): Boolean {
        return (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
    }

    private fun contentColor(): Int {
        return if (isDarkMode()) {
            Color.rgb(253, 253, 253)
        } else {
            Color.rgb(69, 69, 69)
        }
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).roundToInt()

    private fun dpFloat(value: Float): Float = value * context.resources.displayMetrics.density
}
