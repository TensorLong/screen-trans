package com.yiqun.translator.ui.screen.overlay.settings


import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yiqun.translator.R
import com.yiqun.translator.core.OverlayService
import com.yiqun.translator.data.local.vision.TextDetectMode
import com.yiqun.translator.ui.common.MP4Player
import com.yiqun.translator.ui.screen.overlay.OverlayView
import com.yiqun.translator.ui.screen.overlay.menubar.MenuBarViewModel
import com.yiqun.translator.ui.screen.overlay.menubar.TextDetectModeIconButton
import javax.inject.Singleton

/**
 */
@Singleton
class HelpTextDetectModeView private constructor() : OverlayView() {

    companion object {
        val INSTANCE: HelpTextDetectModeView by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { HelpTextDetectModeView() }
    }

    private lateinit var menuBarViewModel: MenuBarViewModel

    private lateinit var initialDetectMode: TextDetectMode

    override val layoutParams: WindowManager.LayoutParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        },
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                or WindowManager.LayoutParams.FLAG_DIM_BEHIND
                or WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
//        windowAnimations = android.R.style.Animation_Toast
        dimAmount = 0.85f
    }

    override val composable: @Composable () -> Unit = @Composable {
        if (isAttachedToWindow()) {
            val configuration = LocalConfiguration.current
            val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

            val textDetectMode = remember { mutableStateOf(initialDetectMode) }

            if (isPortrait) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.0f),
                                    Color.Black.copy(alpha = 1f),
                                    Color.Black.copy(alpha = 0.0f)
                                )
                            )
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1.35f)
//                            .background(Color(0x4422f4ff))
                        ,
                        contentAlignment = Alignment.Center
                    ) {
                        PlayerBox(
                            textDetectMode.value,
                            updateTextDetectMode = { _textDetectMode ->
                                textDetectMode.value = _textDetectMode
                            }
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.65f)
//                            .background(Color(0x44ff44ff))
                        ,
                        contentAlignment = Alignment.TopCenter
                    ) {
                        FreeModeBox()
                    }
                }
            }

            // landscape
            else {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .wrapContentWidth()
                            .padding(start = 50.dp)
//                            .background(Color(0x4422f4ff))
                        ,
                        contentAlignment = Alignment.Center
                    ) {
                        PlayerBox(
                            textDetectMode.value,
                            updateTextDetectMode = { _textDetectMode ->
                                textDetectMode.value = _textDetectMode
                            }
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
//                            .background(Color(0x44ff44ff))
                        ,
                        contentAlignment = Alignment.Center
                    ) {
                        FreeModeBox()
                    }
                }
            }
        }
    }

    @Composable
    fun PlayerBox(
        textDetectMode: TextDetectMode,
        updateTextDetectMode: (textDetectMode: TextDetectMode) -> Unit
    ) {
        Column {
            Row(
                modifier = Modifier.wrapContentSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextDetectModeIconButton(
                    contentColor = Color.White,
                    textDetectMode = textDetectMode,
                    updateTextDetectMode = updateTextDetectMode,
                )
                Text(
                    text = textDetectMode.text,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            MP4Player(textDetectMode.videoResourceId)
        }
    }

    suspend fun cast(
        applicationContext: Context,
        initialDetectMode: TextDetectMode,
    ) {
        super.cast(applicationContext)
        this.initialDetectMode = initialDetectMode
    }

    override fun onServiceConnected(overlayService: OverlayService) {
        menuBarViewModel = overlayService.getMenuBarViewModel()
        super.onServiceConnected(overlayService)
    }
}

@Composable
fun FreeModeBox() {
    Column {
        Image(
            painter = painterResource(id = R.drawable.image_free),
            contentDescription = "image_free",
            modifier = Modifier
                .sizeIn(
                    maxWidth = 58.dp,
                    maxHeight = 58.dp,
                )
                .align(Alignment.CenterHorizontally),
            contentScale = ContentScale.Fit
        )
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            textAlign = TextAlign.Center,
            text = stringResource(id = R.string.help_text_no_limit_free),
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp)
        )
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            textAlign = TextAlign.Center,
            text = stringResource(id = R.string.help_text_no_limit_free_title),
            color = Color.White,
            style = MaterialTheme.typography.headlineLarge.copy(fontSize = 28.sp)
        )
    }
}















