package com.galaxy.airviewdictionary.ui.common

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.galaxy.airviewdictionary.R
import com.galaxy.airviewdictionary.data.remote.translation.TranslationKitType
import com.galaxy.airviewdictionary.extensions.getScreenWidthDp
import com.galaxy.airviewdictionary.extensions.toDp
import com.galaxy.airviewdictionary.ui.screen.overlay.OverlayView
import com.galaxy.airviewdictionary.ui.screen.overlay.menubar.TranslationKitIconButton
import timber.log.Timber
import javax.inject.Singleton

/*


        // TTS Voice
        val setTTSVoice = remember { mutableStateOf(false) }

        when {
            setTTSVoice.value -> {
                SliderDialog(
                    initialValue = 1.0f - menuTransparency,
                    valueRange = 0.0f..0.5f,
                    onValueChange = { value ->
                        viewModel.updateMenuTransparency(1.0f - value)
                    },
                    onDismissRequest = {
                        setTTSVoice.value = false
                    },
                )
            }
        }
 */
@Composable
fun SampleDialog(
    initialValue: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onDismissRequest: () -> Unit
) {

    Dialog(
        properties = DialogProperties(
            usePlatformDefaultWidth = false  // 플랫폼 기본 너비 사용 안 함
        ),
        onDismissRequest = { onDismissRequest() }) {
        val dialogWindowProvider = LocalView.current.parent as? DialogWindowProvider
        dialogWindowProvider?.window?.setGravity(Gravity.BOTTOM)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x5517fa23))
        ) {
            Text(
                text = "gfkjshgfkjsgh",
                color = Color.Gray,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 23.sp)
            )
        }
    }
}


/**
 * Settings About TranslationKit 뷰
 */
@Singleton
class AboutTranslationKitView private constructor() : OverlayView() {

    companion object {
        val INSTANCE: AboutTranslationKitView by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { AboutTranslationKitView() }
    }

    private var topPaddingPx: Int = 0

    private lateinit var kitType: TranslationKitType

    override var layoutParams: WindowManager.LayoutParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        },
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.CENTER
        windowAnimations = android.R.style.Animation_Toast
    }

    override val composable: @Composable () -> Unit = @Composable {
        if (isAttachedToWindow()) {
            AboutTranslationKit(kitType)
        }
    }

    suspend fun cast(
        applicationContext: Context,
        topPaddingPx: Int,
        kitType: TranslationKitType = TranslationKitType.GOOGLE,
    ) {
        this.topPaddingPx = topPaddingPx
        this.kitType = kitType
        super.cast(applicationContext)
    }

    @Composable
    fun AboutTranslationKit(initialKitType: TranslationKitType) {
        val localView = LocalView.current
        val context = LocalContext.current
        val configuration = LocalConfiguration.current
        val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

        val isDarkMode = isSystemInDarkTheme()
        val backgroundColor = if (isDarkMode) Color(0xFF010102) else Color(0xFFf2f1f4)
        val contentColor = if (isDarkMode) Color(0xFFfafafa) else Color(0xFF010102)

        val selectedKitType = remember { mutableStateOf(initialKitType) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
                .onGloballyPositioned { layoutCoordinates ->
                    Timber.tag(TAG).i("onGloballyPositioned  ${localView.height} ${view?.height} ${layoutCoordinates.size.height}")
                },

            ) {
            Spacer(modifier = Modifier.height(if (isPortrait) 11.dp else topPaddingPx.toDp()))

            ActionBar(
                contentColor = contentColor,
                isPortrait = isPortrait,
                isRtl = isRtl,
                kitType = selectedKitType.value,
                updateTranslationKitType = { kitType ->
                    selectedKitType.value = kitType
                }
            )

            Box {
                when (selectedKitType.value) {
                    TranslationKitType.AZURE -> MyWebView(url = TranslationKitType.AZURE.providersUrl)
                    TranslationKitType.DEEPL -> MyWebView(url = TranslationKitType.DEEPL.providersUrl)
//                    TranslationKitType.YANDEX -> MyWebView(url = TranslationKitType.YANDEX.providersUrl)
                    TranslationKitType.PAPAGO -> MyWebView(url = TranslationKitType.PAPAGO.providersUrl)
                    TranslationKitType.GOOGLE -> MyWebView(url = TranslationKitType.GOOGLE.providersUrl)
                }

                ExtendedFloatingActionButton(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 20.dp, bottom = 40.dp),
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(selectedKitType.value.providersUrl))
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    },
                    icon = { Icon(Icons.Outlined.OpenInBrowser, "open in browser") },
                    text = { Text(text = "open in browser") },
                    containerColor = Color(0xFFaccfeb),
                    contentColor = Color(0xFF010102)
                )
            }
        }
    }

    @Composable
    fun ActionBar(
        contentColor: Color,
        isPortrait: Boolean,
        isRtl: Boolean,
        kitType: TranslationKitType,
        updateTranslationKitType: (kitType: TranslationKitType) -> Unit,
    ) {
        Row(
            modifier = Modifier
                .height(if (isPortrait) 74.dp else 54.dp)
                .fillMaxWidth()
                .padding(start = 2.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    clear()
                },
                modifier = Modifier.align(Alignment.CenterVertically)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_arrow_back),
                    contentDescription = "ArrowBack",
                    colorFilter = ColorFilter.tint(contentColor),
                    modifier = Modifier
                        .size(24.dp)
                        .graphicsLayer {
                            if (isRtl) rotationY = 180f
                        }
                )
            }

            Text(
                text = "About ${kitType.text}",
                color = contentColor,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 20.sp),
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .weight(1f)
            )

            TranslationKitIconButton(
                translationKitType = kitType,
                colored = true,
                updateTranslationKitType = updateTranslationKitType
            )
        }
    }
}

object WebViewPool {
    private val webViewCache = mutableMapOf<String, WebView>()

    fun getWebView(context: Context, url: String): WebView {
        return webViewCache.getOrPut(url) {
            WebView(context).apply {
                settings.javaScriptEnabled = true // 자바스크립트 활성화
                settings.domStorageEnabled = true // DOM 저장소 활성화
                webViewClient = WebViewClient() // WebView 내부에서 로드
                loadUrl(url) // URL 로드
            }
        }
    }
}

@Composable
fun MyWebView(url: String) {
    val isDarkMode = isSystemInDarkTheme()
    AndroidView(
        factory = { context ->
            WebViewPool.getWebView(context, url)
        },
        modifier = Modifier
            .fillMaxHeight()
            .width(getScreenWidthDp())
            .padding(horizontal = 10.dp)
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(16.dp)) // 둥근 사각형 적용
    )
}








