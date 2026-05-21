package com.yiqun.translator.ui.screen.test

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import com.yiqun.translator.data.local.screen.ScreenInfoHolder

class OcrProbeActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
        )
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        setContentView(createContent())
        ScreenInfoHolder.collectAndStoreScreenInfo(this)
    }

    private fun createContent(): View {
        return FrameLayout(this).apply {
            setBackgroundColor(Color.WHITE)
            addView(
                TextView(context).apply {
                    text = TITLE_TEXT
                    setTextColor(Color.BLACK)
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, 54f)
                    includeFontPadding = false
                },
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    leftMargin = TEXT_X.toInt()
                    topMargin = TITLE_TOP
                },
            )
            addView(
                TextView(context).apply {
                    text = EXPECTED_TEXT
                    setTextColor(Color.rgb(18, 18, 18))
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, 54f)
                    includeFontPadding = false
                },
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    leftMargin = TEXT_X.toInt()
                    topMargin = TEXT_TOP
                },
            )
            addView(
                FrameTickerView(context),
                FrameLayout.LayoutParams(1, 1).apply {
                    leftMargin = 0
                    topMargin = 0
                },
            )
        }
    }

    class FrameTickerView(context: android.content.Context) : View(context) {
        private val paint = Paint()
        private var tick = false

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            paint.color = if (tick) Color.WHITE else Color.rgb(254, 254, 254)
            tick = !tick
            canvas.drawPoint(0f, 0f, paint)
            postInvalidateOnAnimation()
        }
    }

    companion object {
        const val TITLE_TEXT = "Sense group OCR probe"
        const val EXPECTED_TEXT = "The target icon points at clear words."
        const val TEXT_X = 48f
        const val TITLE_TOP = 74
        const val TEXT_TOP = 164
        const val TARGET_CENTER_X = 430
        const val TARGET_CENTER_Y = 195
        const val CROP_TOP = 72
        const val CROP_BOTTOM = 284
    }
}
