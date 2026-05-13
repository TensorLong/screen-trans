package com.yiqun.translator.data.local.screen

import android.app.Activity
import android.content.Context
import android.os.Build
import android.util.Size
import android.view.WindowInsets
import android.view.WindowManager
import android.view.WindowMetrics
import timber.log.Timber

object ScreenInfoHolder {
    @Volatile
    private var _screenInfo: ScreenInfo = ScreenInfo(
        width = 0,
        height = 0,
        statusBarHeight = 0,
        navBarHeight = 0,
        orientation = 0,
        safePaddingLeft = 0,
        safePaddingTop = 0,
        safePaddingRight = 0,
        safePaddingBottom = 0
    )

    fun set(info: ScreenInfo) { _screenInfo = info }
    fun get(): ScreenInfo = _screenInfo

    fun collectAndStoreScreenInfo(activity: Activity) {
        val metrics: Size = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics: WindowMetrics = activity.windowManager.currentWindowMetrics
            val bounds = windowMetrics.bounds
            Size(bounds.width(), bounds.height())
        } else {
            val displayMetrics = activity.resources.displayMetrics
            Size(displayMetrics.widthPixels, displayMetrics.heightPixels)
        }

        val orientation = activity.resources.configuration.orientation

        val prevInfo = get()
        set(
            ScreenInfo(
                width = metrics.width,
                height = metrics.height,
                statusBarHeight = prevInfo.statusBarHeight,
                navBarHeight = prevInfo.navBarHeight,
                orientation = orientation,
                safePaddingLeft = prevInfo.safePaddingLeft,
                safePaddingTop = prevInfo.safePaddingTop,
                safePaddingRight = prevInfo.safePaddingRight,
                safePaddingBottom = prevInfo.safePaddingBottom,
            )
        )

        activity.window.decorView.post {
            val insets = activity.window.decorView.rootWindowInsets

            val statusBarHeight = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                insets?.getInsets(WindowInsets.Type.statusBars())?.top ?: 0
            } else {
                @Suppress("DEPRECATION")
                insets?.systemWindowInsetTop ?: 0
            }

            val navBarHeight = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                insets?.getInsets(WindowInsets.Type.navigationBars())?.bottom ?: 0
            } else {
                @Suppress("DEPRECATION")
                insets?.systemWindowInsetBottom ?: 0
            }

            val left = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                insets?.getInsets(WindowInsets.Type.systemBars())?.left ?: 0
            } else {
                0
            }

            val right = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                insets?.getInsets(WindowInsets.Type.systemBars())?.right ?: 0
            } else {
                0
            }

            val current = get()
            val screenInfo = ScreenInfo(
                width = current.width,
                height = current.height,
                statusBarHeight = statusBarHeight,
                navBarHeight = navBarHeight,
                orientation = current.orientation,
                safePaddingLeft = left,
                safePaddingTop = statusBarHeight,
                safePaddingRight = right,
                safePaddingBottom = navBarHeight
            )
            Timber.tag("ScreenInfoHolder").d("ScreenInfo $screenInfo")

            set(screenInfo)
        }
    }

    /**
     */
    fun updateScreenInfoInService(context: Context) {
        val orientation = context.resources.configuration.orientation
        val (width, height) = readCurrentSize(context)

        val prevInfo = get()
        val newInfo = ScreenInfo(
            width = width,
            height = height,
            statusBarHeight = prevInfo.statusBarHeight,
            navBarHeight = prevInfo.navBarHeight,
            orientation = orientation,
            safePaddingLeft = prevInfo.safePaddingLeft,
            safePaddingTop = prevInfo.safePaddingTop,
            safePaddingRight = prevInfo.safePaddingRight,
            safePaddingBottom = prevInfo.safePaddingBottom
        )
        Timber.tag("ScreenInfoHolder").d("ScreenInfo newInfo $newInfo")
        set(newInfo)
    }

    private fun readCurrentSize(context: Context): Pair<Int, Int> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val bounds = wm.currentWindowMetrics.bounds
                return bounds.width() to bounds.height()
            } catch (t: Throwable) {
                Timber.tag("ScreenInfoHolder").w(t, "currentWindowMetrics failed; falling back to displayMetrics")
            }
        }
        @Suppress("DEPRECATION")
        val dm = context.resources.displayMetrics
        return dm.widthPixels to dm.heightPixels
    }
}
