package com.yiqun.translator.data.local.capture

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import androidx.core.graphics.createBitmap
import com.yiqun.translator.data.AVDRepository
import com.yiqun.translator.data.local.screen.ScreenInfo
import com.yiqun.translator.data.local.screen.ScreenInfoHolder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 */
@Singleton
class CaptureRepository @Inject constructor(@ApplicationContext val context: Context) : AVDRepository() {

    private enum class State {
        Uninitialized,
        Ready,
    }

    companion object {
        private const val ARGB_8888_BYTES_PER_PIXEL = 4

        var mediaProjectionToken: Intent? = null
            set(value) {
                field = value
                Timber.tag("CaptureRepository").i("#### set mediaProjectionToken $value ####")
            }
    }

    private var state: State = State.Uninitialized

    private var mediaProjection: MediaProjection? = null

    private var mediaProjectionStopCallback: MediaProjection.Callback? = null

    private var imageReader: ImageReader? = null

    private var virtualDisplay: VirtualDisplay? = null

    private val captureResponseFlow = MutableStateFlow<CaptureResponse?>(null)

    private var requestedScreenRect: Rect? = null

    private val handler = Handler(Looper.getMainLooper())

    private fun start() {
        Timber.tag(TAG).d("#### request() #### $mediaProjectionToken")

        clearResources()

        /**
         * Returns a new Rect describing the bounds of the area the window occupies.
         * Note that the size of the reported bounds can have different size than Display#getSize.
         * This method reports the window size including all system decorations,
         * while Display#getSize reports the area excluding navigation bars and display cutout areas.
         * Returns:
         * window bounds in pixels.
         */
        val screenInfo: ScreenInfo = ScreenInfoHolder.get()
        val dpi = context.resources.displayMetrics.density.toInt()
        Timber.tag("CaptureRepository").i("#### start width ${screenInfo.width}  height ${screenInfo.height} dpi $dpi ####")

        try {
            val mediaProjectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = mediaProjectionManager.getMediaProjection(Activity.RESULT_OK, mediaProjectionToken!!)

            mediaProjectionStopCallback = object : MediaProjection.Callback() {
                override fun onStop() {
                    // System or user stopped the projection (e.g. via system "Stop sharing" notification).
                    // Reset state so the next request() goes through start() and surfaces a proper
                    // NoMediaProjectionTokenException, which triggers the re-auth flow in the UI.
                    Timber.tag(TAG).w("#### MediaProjectionStopCallback onStop() ####")
                    clearResources()
                    state = State.Uninitialized
                }
            }
            mediaProjection!!.registerCallback(mediaProjectionStopCallback!!, null)

            imageReader = ImageReader.newInstance(screenInfo.width, screenInfo.height, PixelFormat.RGBA_8888, 1)

            virtualDisplay = mediaProjection!!.createVirtualDisplay(
                "Sense Group Translator",
                screenInfo.width,
                screenInfo.height,
                dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader!!.surface,
                null,
                null,
            )

            imageReader!!.setOnImageAvailableListener({ imageReader ->
//                Timber.tag(TAG).d("---- onImageAvailable imageReader $imageReader ----")
                val capturedImage = imageReader.acquireLatestImage()
                try {
                    if (captureResponseFlow.value == null) {
                        if (capturedImage != null) {
                            val (capturedBitmap, screenRect) = bitmapFromImage(
                                image = capturedImage,
                                screenInfo = screenInfo,
                                requestedRect = requestedScreenRect,
                            )
//                            Timber.tag(TAG).d("capturedBitmap.allocationByteCount ${capturedBitmap.allocationByteCount}")
                            captureResponseFlow.value = CaptureResponse.Success(capturedBitmap, screenRect)
                        } else {
                            captureResponseFlow.value = CaptureResponse.Error(CapturedImageInvalidException())
                        }
                    }
                } catch (t: Throwable) {
                    t.printStackTrace()
                    Timber.tag(TAG).e("err t ${t.toString()} $mediaProjectionToken")
                    captureResponseFlow.value = CaptureResponse.Error(NoMediaProjectionTokenException(t.toString()))
                } finally {
                    capturedImage?.close()
                }
            }, handler)

            state = State.Ready
        }
        /*
            NullPointerException:

            SecurityException:
            Don't re-use the resultData to retrieve the same projection instance, and don't use a token that has timed out.
            Don't take multiple captures by invoking MediaProjection#createVirtualDisplay multiple times on the same instance.

            IllegalStateException:
            Cannot start already started MediaProjection
         */
        catch (e: Exception) {
            e.printStackTrace()
            Timber.tag(TAG).e("err e ${e.toString()} $mediaProjectionToken")
            captureResponseFlow.value = CaptureResponse.Error(NoMediaProjectionTokenException(e.toString()))
        }
    }

    private fun bitmapFromImage(
        image: Image,
        screenInfo: ScreenInfo,
        requestedRect: Rect?,
    ): Pair<Bitmap, Rect> {
        val screenRect = rectOf(0, 0, screenInfo.width, screenInfo.height)
        val safeRect = requestedRect
            ?.let { sanitizeRect(it, screenInfo.width, screenInfo.height) }
            ?: screenRect

        return if (sameRect(safeRect, screenRect)) {
            copyFullBitmap(image, screenInfo) to screenRect
        } else {
            copyCroppedBitmap(image, safeRect) to safeRect
        }
    }

    private fun copyFullBitmap(image: Image, screenInfo: ScreenInfo): Bitmap {
        val planes = image.planes
        val buffer = planes[0].buffer

        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding: Int = rowStride - pixelStride * screenInfo.width

        val paddedBitmap = createBitmap(screenInfo.width + rowPadding / pixelStride, screenInfo.height)
        paddedBitmap.copyPixelsFromBuffer(buffer)
        if (rowPadding == 0) {
            return paddedBitmap
        }

        val croppedBitmap = Bitmap.createBitmap(paddedBitmap, 0, 0, screenInfo.width, screenInfo.height)
        paddedBitmap.recycle()
        return croppedBitmap
    }

    private fun copyCroppedBitmap(image: Image, rect: Rect): Bitmap {
        val plane = image.planes[0]
        if (rect.left == 0 && rect.right == image.width && plane.pixelStride == ARGB_8888_BYTES_PER_PIXEL) {
            return copyFullWidthCroppedBitmap(image, rect, plane)
        }
        return copyCroppedBitmapPixelByPixel(image, rect, plane)
    }

    private fun copyFullWidthCroppedBitmap(
        image: Image,
        rect: Rect,
        plane: Image.Plane,
    ): Bitmap {
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        if (rowStride % pixelStride != 0) {
            return copyCroppedBitmapPixelByPixel(image, rect, plane)
        }

        val paddedWidth = rowStride / pixelStride
        val height = rect.bottom - rect.top
        val buffer = plane.buffer.duplicate()
        buffer.position(rect.top * rowStride)

        val paddedBitmap = createBitmap(paddedWidth, height)
        paddedBitmap.copyPixelsFromBuffer(buffer)
        if (paddedWidth == rect.width()) {
            return paddedBitmap
        }

        val croppedBitmap = Bitmap.createBitmap(paddedBitmap, 0, 0, rect.width(), height)
        paddedBitmap.recycle()
        return croppedBitmap
    }

    private fun copyCroppedBitmapPixelByPixel(
        image: Image,
        rect: Rect,
        plane: Image.Plane,
    ): Bitmap {
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val width = rect.right - rect.left
        val height = rect.bottom - rect.top
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        val rowPixels = IntArray(width)

        for (row in 0 until height) {
            val bufferRowStart = (rect.top + row) * rowStride + rect.left * pixelStride
            for (column in 0 until width) {
                val pixelStart = bufferRowStart + column * pixelStride
                val red = buffer.get(pixelStart).toInt() and 0xFF
                val green = buffer.get(pixelStart + 1).toInt() and 0xFF
                val blue = buffer.get(pixelStart + 2).toInt() and 0xFF
                rowPixels[column] = (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
            }
            bitmap.setPixels(rowPixels, 0, width, 0, row, width, 1)
        }

        return bitmap
    }

    private fun sanitizeRect(rect: Rect, screenWidth: Int, screenHeight: Int): Rect {
        val maxRight = screenWidth.coerceAtLeast(1)
        val maxBottom = screenHeight.coerceAtLeast(1)
        val left = rect.left.coerceIn(0, maxRight - 1)
        val top = rect.top.coerceIn(0, maxBottom - 1)
        val right = rect.right.coerceIn(left + 1, maxRight)
        val bottom = rect.bottom.coerceIn(top + 1, maxBottom)
        return rectOf(left, top, right, bottom)
    }

    private fun rectOf(left: Int, top: Int, right: Int, bottom: Int): Rect {
        return Rect().apply {
            this.left = left
            this.top = top
            this.right = right
            this.bottom = bottom
        }
    }

    private fun sameRect(first: Rect, second: Rect): Boolean {
        return first.left == second.left &&
                first.top == second.top &&
                first.right == second.right &&
                first.bottom == second.bottom
    }

    fun restart() {
        clearResources()
        state = State.Uninitialized
    }

    /**
     * https://stackoverflow.com/questions/42158782/mediaprojection-api-on-protected-drm-content
     * https://support.google.com/googleplay/android-developer/answer/14638385?hl=ko&ref_topic=13878452&sjid=17736376115784780377-AP#zippy=%2Cflag-secure%EA%B0%80-%EC%9D%98%EB%8F%84%ED%95%9C-%EB%8C%80%EB%A1%9C-%EC%9E%91%EB%8F%99%ED%95%98%EB%8A%94-%EB%B0%A9%EC%8B%9D%EC%9D%98-%EC%98%88%EB%8A%94-%EB%AC%B4%EC%97%87%EC%9D%B8%EA%B0%80%EC%9A%94%2Cflag-secure-%EB%B0%8F-require-secure-env-%ED%94%8C%EB%9E%98%EA%B7%B8%EB%A5%BC-%EC%82%AC%EC%9A%A9%ED%95%A0-%EC%88%98-%EC%9E%88%EB%8A%94-%EC%95%B1-%EC%9C%A0%ED%98%95%EC%9D%80-%EB%AC%B4%EC%97%87%EC%9D%B8%EA%B0%80%EC%9A%94%2C%EC%9D%B4%EB%9F%AC%ED%95%9C-%ED%94%8C%EB%9E%98%EA%B7%B8%EB%A5%BC-%EC%82%AC%EC%9A%A9%ED%95%98%EB%A9%B4-%EC%95%B1%EC%97%90-%EB%B6%80%EC%A0%95%EC%A0%81%EC%9D%B8-%EC%98%81%ED%96%A5%EC%9D%84-%EB%AF%B8%EC%B9%98%EB%82%98%EC%9A%94-%EA%B5%AC%ED%98%84%ED%95%98%EB%8A%94-%EB%8D%B0-%EC%8B%9C%EA%B0%84%EC%9D%B4-%EC%96%BC%EB%A7%88%EB%82%98-%EA%B1%B8%EB%A6%AC%EB%82%98%EC%9A%94
     */
    private fun isCapturePrevented(capturedBitmap: Bitmap): Pair<Boolean, Bitmap> {
        var isCapturePrevented = true
        val checker_w = capturedBitmap.width * 3 / 5
        val checker_h = capturedBitmap.height * 3 / 5
        val checkerScreenshotPixels = IntArray(checker_w * checker_h)
        val checkerBitmap = Bitmap.createBitmap(
            capturedBitmap,
            capturedBitmap.width / 5,
            capturedBitmap.height / 5,
            checker_w,
            checker_h
        )
        Timber.tag(TAG).d("checkerBitmap width ${checkerBitmap.width} height ${checkerBitmap.height}")
        checkerBitmap.getPixels(checkerScreenshotPixels, 0, checker_w, 0, 0, checker_w, checker_h)
        val firstPixel = checkerScreenshotPixels[0]
//        Timber.tag(TAG).d("firstPixel : $firstPixel checkerScreenshotPixels.size ${checkerScreenshotPixels.size}");
        /*

         */
        var i = 0
        while (i < checkerScreenshotPixels.size) {
//            Timber.tag(TAG).d("checkerScreenshot[$i] : ${checkerScreenshotPixels[i]} ${(firstPixel == checkerScreenshotPixels[i])}");
            if (firstPixel != checkerScreenshotPixels[i]) {
                isCapturePrevented = false
                break
            }
            i += 30
        }

        return Pair(isCapturePrevented, checkerBitmap)
    }

    private fun clearResources() {
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.setOnImageAvailableListener(null, null)
        imageReader?.close()
        imageReader = null
        mediaProjectionStopCallback?.let {
            Handler(Looper.getMainLooper()).post {
                mediaProjection?.unregisterCallback(it)
            }
            mediaProjectionStopCallback = null
        }
        mediaProjection?.stop()
        mediaProjection = null
    }

    suspend fun request(cropRect: Rect? = null): CaptureResponse {
        Timber.tag(TAG).i("#### request() ####")
        captureResponseFlow.value = null
        requestedScreenRect = cropRect

        Timber.tag(TAG).i("State $state")
        if (state == State.Uninitialized) {
            start()
        }

        var captureResponse: CaptureResponse = captureResponseFlow.filterNotNull().first()
        if (captureResponse is CaptureResponse.Success) {
//            val (isCapturePrevented, checkerBitmap) = isCapturePrevented(capturedBitmap)
//            captureWorkFlow.value =
//                if (isCapturePrevented) {
//                    Response.Error(CapturePreventedException(checkerBitmap))
//                } else {
//                    Response.Success(capturedBitmap)
//                }
        }
        requestedScreenRect = null
        return captureResponse
    }

    override fun onZeroReferences() {
        Timber.tag(TAG).d("====================== mediaProjectionToken = null ============================ ")
        clearResources()
        mediaProjectionToken = null
    }

}
