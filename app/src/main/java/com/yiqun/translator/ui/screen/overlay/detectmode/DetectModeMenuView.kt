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
import com.yiqun.translator.extensions.finishService
import com.yiqun.translator.ui.screen.overlay.OverlayView
import com.yiqun.translator.ui.screen.overlay.menubar.MenuBarDropdownOptions
import com.yiqun.translator.ui.screen.overlay.menubar.MenuBarViewModel
import javax.inject.Singleton
import kotlin.math.roundToInt

// Layout constants shared between the window sizing math and the native view so the
// fixed WindowManager size always matches the laid-out content height exactly.
private const val MENU_WIDTH_DP = 240
private const val ROW_HEIGHT_DP = 44
private const val MENU_VERTICAL_PADDING_DP = 6
private const val SEPARATOR_HEIGHT_DP = 1
private const val SEPARATOR_MARGIN_DP = 4
private const val MENU_MARGIN_DP = 12

private fun dp(value: Int, density: Float): Int = (value * density).roundToInt()

@Singleton
class DetectModeMenuView private constructor() : OverlayView() {

    companion object {
        val INSTANCE: DetectModeMenuView by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { DetectModeMenuView() }
    }

    private lateinit var viewModel: MenuBarViewModel
    private var selectedMode: TextDetectMode? = null

    // Placeholder size; replaced with the real fixed size in cast() once a Context is available.
    override var layoutParams: WindowManager.LayoutParams = menuLayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
    )

    override val composable: @Composable () -> Unit = @Composable {}

    override fun createView(overlayService: OverlayService): View {
        return DetectModeMenuNativeView(
            context = overlayService,
            currentMode = selectedMode,
            dismiss = { clear() },
            updateTextDetectMode = { mode ->
                viewModel.updateTextDetectMode(mode)
                clear()
            },
            closeApp = {
                // Same full-app close path as the in-app power button: stop OverlayService.
                overlayService.applicationContext.finishService()
            },
        ).also { menuView ->
            launchInOverlayViewCoroutineScope {
                viewModel.preferenceRepository.textDetectModeFlow.collect { mode ->
                    selectedMode = mode
                    menuView.setSelectedMode(mode)
                }
            }
        }
    }

    override fun onServiceConnected(overlayService: OverlayService) {
        viewModel = overlayService.getMenuBarViewModel()
        super.onServiceConnected(overlayService)
    }

    override suspend fun cast(applicationContext: Context) {
        cast(applicationContext, null, null)
    }

    suspend fun cast(applicationContext: Context, anchorCenterX: Int?, anchorCenterY: Int?) {
        val density = applicationContext.resources.displayMetrics.density
        val menuWidth = dp(MENU_WIDTH_DP, density)
        val menuHeight = menuHeightPx(density)
        val position = menuPosition(applicationContext, menuWidth, menuHeight, anchorCenterX, anchorCenterY)
        layoutParams = menuLayoutParams(menuWidth, menuHeight).apply {
            x = position.x
            y = position.y
        }
        super.cast(applicationContext, reattach = isRunning.get())
    }

    /**
     * Exact pixel height of the native menu: vertical padding + one row per detection
     * mode + the separator block + the fixed bottom Close row. Summed with the same
     * per-element rounding the native view uses so the fixed window height never clips.
     */
    private fun menuHeightPx(density: Float): Int {
        val rowCount = MenuBarDropdownOptions.textDetectModes.size + 1 // + fixed Close row
        return 2 * dp(MENU_VERTICAL_PADDING_DP, density) +
                rowCount * dp(ROW_HEIGHT_DP, density) +
                dp(SEPARATOR_HEIGHT_DP, density) +
                2 * dp(SEPARATOR_MARGIN_DP, density)
    }

    private fun menuLayoutParams(width: Int, height: Int): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            width,
            height,
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
        menuWidth: Int,
        menuHeight: Int,
        anchorCenterX: Int?,
        anchorCenterY: Int?,
    ): DetectModeMenuPositionPolicy.Position {
        val screenInfo = ScreenInfoHolder.get()
        val density = context.resources.displayMetrics.density
        val handleRadius = context.resources.getDimensionPixelSize(R.dimen.target_handle_width) / 2
        return DetectModeMenuPositionPolicy.resolve(
            screenWidth = screenInfo.width,
            screenHeight = screenInfo.height,
            menuWidth = menuWidth,
            menuHeight = menuHeight,
            margin = dp(MENU_MARGIN_DP, density),
            handleRadius = handleRadius,
            anchorCenterX = anchorCenterX,
            anchorCenterY = anchorCenterY,
        )
    }
}

private class DetectModeMenuNativeView(
    context: Context,
    currentMode: TextDetectMode?,
    private val dismiss: () -> Unit,
    private val updateTextDetectMode: (TextDetectMode) -> Unit,
    private val closeApp: () -> Unit,
) : LinearLayout(context) {

    private val selectionIndicators = mutableMapOf<TextDetectMode, TextView>()
    private var selectedMode: TextDetectMode? = currentMode

    init {
        orientation = VERTICAL
        minimumWidth = dp(MENU_WIDTH_DP)
        setPadding(0, dp(MENU_VERTICAL_PADDING_DP), 0, dp(MENU_VERTICAL_PADDING_DP))
        background = GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            setColor(if (isDarkMode()) Color.rgb(31, 31, 31) else Color.rgb(254, 254, 254))
            setStroke(dpFloat(0.4f).roundToInt(), if (isDarkMode()) Color.rgb(106, 106, 106) else Color.rgb(214, 214, 214))
        }
        elevation = dpFloat(3f)
        MenuBarDropdownOptions.textDetectModes.forEach { mode ->
            addView(modeRow(mode))
        }
        // Separator + Close row are added after every detection mode, so the Close
        // option stays pinned to the bottom even if new modes are introduced later.
        addView(separator())
        addView(closeRow())
        setSelectedMode(currentMode)
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
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(ROW_HEIGHT_DP))
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(ROW_HEIGHT_DP)
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
                LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(12)
                },
            )
            addView(
                TextView(context).apply {
                    text = if (DetectModeMenuSelectionPolicy.isSelected(mode, selectedMode)) CHECK_MARK else ""
                    setTextColor(accentColor())
                    textSize = 18f
                    includeFontPadding = false
                    gravity = Gravity.CENTER
                    selectionIndicators[mode] = this
                },
                LayoutParams(dp(24), LayoutParams.WRAP_CONTENT),
            )
        }
    }

    private fun separator(): View {
        return View(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(SEPARATOR_HEIGHT_DP)).apply {
                topMargin = dp(SEPARATOR_MARGIN_DP)
                bottomMargin = dp(SEPARATOR_MARGIN_DP)
                marginStart = dp(14)
                marginEnd = dp(14)
            }
            setBackgroundColor(separatorColor())
        }
    }

    private fun closeRow(): View {
        return LinearLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(ROW_HEIGHT_DP))
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(ROW_HEIGHT_DP)
            setPadding(dp(14), 0, dp(14), 0)
            background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { closeApp() }
            addView(
                ImageView(context).apply {
                    setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                    setColorFilter(contentColor())
                },
                LayoutParams(dp(20), dp(20)),
            )
            addView(
                TextView(context).apply {
                    text = context.getString(R.string.detect_mode_menu_close)
                    setTextColor(contentColor())
                    textSize = 15f
                    includeFontPadding = false
                    gravity = Gravity.CENTER_VERTICAL
                },
                LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(12)
                },
            )
        }
    }

    fun setSelectedMode(mode: TextDetectMode?) {
        selectedMode = mode
        selectionIndicators.forEach { (rowMode, indicator) ->
            indicator.text = if (DetectModeMenuSelectionPolicy.isSelected(rowMode, mode)) CHECK_MARK else ""
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

    private fun accentColor(): Int {
        return if (isDarkMode()) {
            Color.rgb(121, 196, 255)
        } else {
            Color.rgb(0, 107, 184)
        }
    }

    private fun separatorColor(): Int {
        return if (isDarkMode()) {
            Color.rgb(58, 58, 58)
        } else {
            Color.rgb(229, 229, 229)
        }
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).roundToInt()

    private fun dpFloat(value: Float): Float = value * context.resources.displayMetrics.density

    private companion object {
        const val CHECK_MARK = "✓"
    }
}
