package com.yiqun.translator.ui.screen.overlay.translation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yiqun.translator.R
import com.yiqun.translator.core.OverlayService
import com.yiqun.translator.data.local.screen.ScreenInfo
import com.yiqun.translator.data.local.screen.ScreenInfoHolder
import com.yiqun.translator.data.local.secure.ApiKeyInfo
import com.yiqun.translator.data.local.vision.TextDetectMode
import com.yiqun.translator.data.local.vision.WritingDirection
import com.yiqun.translator.data.local.vision.model.VisionText
import com.yiqun.translator.data.remote.ai.chatgpt.AiModelIcon
import com.yiqun.translator.data.remote.ai.chatgpt.AiModelIconResolver
import com.yiqun.translator.data.remote.translation.Language
import com.yiqun.translator.data.remote.translation.Transaction
import com.yiqun.translator.data.remote.translation.TranslationKitType
import com.yiqun.translator.extensions.toPx
import com.yiqun.translator.extensions.toSpValue
import com.yiqun.translator.ui.common.AutoResizeText
import com.yiqun.translator.ui.screen.overlay.Event
import com.yiqun.translator.ui.screen.overlay.OverlayView
import com.yiqun.translator.ui.screen.overlay.targethandle.TargetHandleViewModel
import com.yiqun.translator.ui.screen.overlay.visiontext.VisionTextView
import com.yiqun.translator.ui.screen.reply.ReplyActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Singleton
import kotlin.math.min
import kotlin.math.roundToInt


/**
 */
@Singleton
open class TranslationView : OverlayView() {

    companion object {
        val INSTANCE: TranslationView by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { TranslationView() }

        val liveStateFlow = MutableStateFlow(false)
    }

    private lateinit var targetHandleViewModel: TargetHandleViewModel

    override lateinit var layoutParams: WindowManager.LayoutParams

    override val composable: @Composable () -> Unit = @Composable {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current

        val translationState by targetHandleViewModel.translationFlow.collectAsStateWithLifecycle(
            lifecycle = lifecycleOwner.lifecycle,
            initialValue = Pair<VisionText?, Transaction?>(null, null)
        )

        translationState?.let { (visionText, translation) ->
            if (visionText != null && translation != null) {
                if (isAttachedToWindow()) {
                    val fontSizeSp = getRenderFontSizeSp(context, visionText.fontHeight).sp
                    TranslationBox(
                        translation,
                        fontSizeSp,
                        onPauseDismissRunning = { targetHandleViewModel.pauseDismissRunning() },
                        onResumeDismissRunning = { targetHandleViewModel.resumeDismissRunning() },
                        onRerunDismissRunning = { targetHandleViewModel.rerunDismissRunning() }
                    )
                    targetHandleViewModel.analyticsRepository.translationReport(
                        transaction = translation,
                        textDetectMode = targetHandleViewModel.textDetectMode,
                    )
                }
            }
        } ?: clear()
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

    open suspend fun cast(
        applicationContext: Context,
        translation: Transaction,
        visionText: VisionText
    ) {
        layoutParams = getTranslationLayout(
            applicationContext,
            translation,
            visionText
        )
        super.cast(applicationContext)
        liveStateFlow.value = true
    }

    override fun clear() {
        liveStateFlow.value = false
        drawnTranslation = null
        super.clear()
    }

    /**
     */
    private var drawnTranslation: Transaction? = null

    private val FONT_SIZE_MIN_SP: Float = 13f
    private val FONT_SIZE_MAX_SP: Float = 24f
    private val FONT_SIZE_RATIO: Float = 0.9f
    private val CONTENT_WIDTH_RATIO: Float = 1.2f
    private val SOURCE_TEXT_RESULT_TEXT_SPACE: String = "  "

    private fun getSpeakerSpace(fontSize: Float): String {
        return if (fontSize > 60.0) {
            "    "
        } else if (fontSize > 50.0) {
            "     "
        } else if (fontSize > 40.0) {
            "      "
        } else {
            "       "
        }
    }

    private fun getTranslationLayout(
        applicationContext: Context,
        translation: Transaction,
        visionText: VisionText
    ): WindowManager.LayoutParams {
        val screenInfo: ScreenInfo = ScreenInfoHolder.get()
        Timber.tag(TAG).d("translation [${translation}] ")
        Timber.tag(TAG).d("visionText [${visionText}] ")

        Timber.tag(TAG).d("translationTransaction.sourceText [${translation.sourceText}] ")
        Timber.tag(TAG).d("drawnTranslationTransaction?.sourceText [${drawnTranslation?.sourceText}] ")
        Timber.tag(TAG).d("translationTransaction.detectedLanguageCode [${translation.detectedLanguageCode}] ")
        Timber.tag(TAG).d("drawnTranslationTransaction?.detectedLanguageCode [${drawnTranslation?.detectedLanguageCode}] ")

        if (
            translation.sourceText != drawnTranslation?.sourceText
            || translation.detectedLanguageCode != drawnTranslation?.detectedLanguageCode
        ) {
            Timber.tag(TAG).e("+++++++++++ clear() !!!!!!!!!!!!!!!!!!")
            clear()
        }

        val sourceText = translation.sourceText

        val fontSizeSp = getRenderFontSizeSp(applicationContext, visionText.fontHeight)
        Timber.tag(TAG).d("+++++++++++ getTranslationLayout fontSizeSp [${fontSizeSp}]")

        // text
        val text = getSpeakerSpace(fontSizeSp) + sourceText + SOURCE_TEXT_RESULT_TEXT_SPACE + translation.resultText

        val textWidth = measureTextWidth(applicationContext, text, fontSizeSp)
        Timber.tag(TAG).d("textWidth [${textWidth}]")

        val screenViewMinMargin = applicationContext.resources.getDimensionPixelSize(R.dimen.translation_view_screen_min_margin)
        Timber.tag(TAG).d("screenViewMargin [${screenViewMinMargin}]")

        val viewShadowPadding = applicationContext.resources.getDimensionPixelSize(R.dimen.translation_view_shadow_padding)
        Timber.tag(TAG).d("viewShadowPadding [${viewShadowPadding}]")

        val viewContentPadding = applicationContext.resources.getDimensionPixelSize(R.dimen.translation_view_content_padding)
        Timber.tag(TAG).d("viewContentPadding [${viewContentPadding}]")

        val bottomMenuHeight = applicationContext.resources.getDimensionPixelSize(R.dimen.translation_view_bottom_menu_height)
        Timber.tag(TAG).d("bottomMenuHeight [${bottomMenuHeight}]")

        val contentWidth: Float =
            if ((screenViewMinMargin + viewShadowPadding + viewContentPadding + textWidth + viewContentPadding + viewShadowPadding + screenViewMinMargin) < screenInfo.width) {
                textWidth
            } else {
                visionText.width * CONTENT_WIDTH_RATIO
            }
        Timber.tag(TAG).d("contentWidth [${contentWidth}]")

        val viewMinWidth = applicationContext.resources.getDimensionPixelSize(R.dimen.translation_view_min_width)

        val viewMaxWidth = screenInfo.width - screenViewMinMargin * 2

        val viewWidth: Int = (viewShadowPadding + viewContentPadding + contentWidth + viewContentPadding + viewShadowPadding).roundToInt().coerceIn(viewMinWidth, viewMaxWidth)
        Timber.tag(TAG).d("viewWidth [${viewWidth}]")

        val contentHeight = calculateTextHeight(
            applicationContext,
            text,
            viewWidth - (viewShadowPadding * 2) - (viewContentPadding * 2),
            fontSizeSp,
        )
        Timber.tag(TAG).d("contentHeight [${contentHeight}]")

        val viewMaxHeight = screenInfo.height - screenViewMinMargin * 2
        Timber.tag(TAG).d("viewMaxHeight [${viewMaxHeight}]")

        val viewHeight: Int =
            if ((screenViewMinMargin + viewShadowPadding + viewContentPadding + textWidth + viewContentPadding + viewShadowPadding + screenViewMinMargin) < screenInfo.width) {
                viewShadowPadding + viewContentPadding + contentHeight + bottomMenuHeight + viewShadowPadding
            } else {
                min(
                    (viewShadowPadding + viewContentPadding + contentHeight + bottomMenuHeight + viewShadowPadding),
                    viewMaxHeight
                )
            }
        Timber.tag(TAG).d("viewHeight [${viewHeight}]")

        val layoutPosX = (visionText.start - (viewWidth - visionText.width) / 2).coerceIn(
            0,
            (screenInfo.width - viewWidth)
        )
        val layoutPosY =
            visionText.boundingBox.top - VisionTextView.paragraphFrameMargin - applicationContext.resources.getDimensionPixelSize(
                R.dimen.translation_view_vision_text_v_margin
            ) - viewHeight
        Timber.tag(TAG).d("layoutPosX [${layoutPosX}] layoutPosY [${layoutPosY}]")

        return WindowManager.LayoutParams(
            viewWidth,
            viewHeight,
            layoutPosX,
            layoutPosY,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
    }

    /**
     *
     */
    private fun getRenderFontSizeSp(context: Context, visionTextFontHeight: Double): Float {
        val fontHeight = (visionTextFontHeight * FONT_SIZE_RATIO).toFloat().toSpValue(context)
            .coerceIn(FONT_SIZE_MIN_SP, FONT_SIZE_MAX_SP)
        return (fontHeight * 100).roundToInt() / 100f
    }

    /**
     *
     */
    private fun measureTextWidth(context: Context, text: String, fontSizeSp: Float): Float {
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                fontSizeSp,
                context.resources.displayMetrics
            )
        }
        return textPaint.measureText(text)
    }

    /**
     *
     */
    private fun calculateTextHeight(
        context: Context,
        text: String,
        width: Int,
        fontSizeSp: Float
    ): Int {
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                fontSizeSp,
                context.resources.displayMetrics
            )
        }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.0f)
            .setIncludePad(true)
            .build()
//        return (layout.height * 1.2).toInt()
        return layout.height
    }

    @Composable
    fun TranslationBox(
        translation: Transaction,
        fontSize: TextUnit = 60.sp,
        enableAutoResize: Boolean = true,
        onPauseDismissRunning: () -> Unit,
        onResumeDismissRunning: () -> Unit,
        onRerunDismissRunning: () -> Unit,
    ) {
        drawnTranslation = translation

        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        val coroutineScope = rememberCoroutineScope()

        val isDarkMode = isSystemInDarkTheme()

        val backgroundColor = if (isDarkMode) Color.Black else Color.White
        val sourceTextColor = colorResource(if (isDarkMode) R.color.selected_text_color_dark else R.color.selected_text_color)
        val resultTextColor = if (isDarkMode) Color(0xFFFDFDFD) else Color(0xFF454545)
        val borderColor = if (isDarkMode) Color(0xFF6A6A6A) else Color(0xFFD6D6D6)
        val roundedCornerShape = RoundedCornerShape(16.dp)

        var readyToDisplay by remember { mutableStateOf(!enableAutoResize) }

        val shadowPadding = dimensionResource(R.dimen.translation_view_shadow_padding)
        val viewContentPadding = dimensionResource(R.dimen.translation_view_content_padding)
        val bottomMenuHeight = dimensionResource(R.dimen.translation_view_bottom_menu_height)

        val isWritingRtl = remember { mutableStateOf(false) }
        val writingDirection = remember { mutableStateOf(WritingDirection.LTR) }
        val sourceLanguageCode by targetHandleViewModel.preferenceRepository.sourceLanguageCodeFlow.collectAsStateWithLifecycle(
            lifecycle = lifecycleOwner.lifecycle,
            initialValue = "auto"
        )
        val textDetectMode by targetHandleViewModel.preferenceRepository.textDetectModeFlow.collectAsStateWithLifecycle(
            lifecycle = lifecycleOwner.lifecycle,
            initialValue = TextDetectMode.SENTENCE
        )
        val selectedAiModel = ApiKeyInfo.getApiModelChatgpt(context)
        val showAiModelBadge = textDetectMode == TextDetectMode.SENSE_GROUP &&
                translation.translationKitType == TranslationKitType.GOOGLE &&
                ApiKeyInfo.chatgptKeyAvailable(context)
        val aiModelIcon = remember(selectedAiModel) {
            AiModelIconResolver.resolve(selectedAiModel)
        }
        LaunchedEffect(sourceLanguageCode) {
            writingDirection.value = Language.writingDirection(sourceLanguageCode, false)
            isWritingRtl.value = writingDirection.value == WritingDirection.RTL
        }

        val sourceText = translation.sourceText
        val annotatedText = buildAnnotatedString {
            append(getSpeakerSpace(fontSize.toPx()))
            if (isWritingRtl.value) {
                withStyle(
                    style = SpanStyle(
                        color = resultTextColor,
                    )
                ) {
                    append(translation.resultText)
                }
                append(SOURCE_TEXT_RESULT_TEXT_SPACE)
                withStyle(
                    style = SpanStyle(
                        color = sourceTextColor,
                        fontWeight = FontWeight.Bold
                    )
                ) {
                    append(sourceText)
                }
            } else {
                withStyle(
                    style = SpanStyle(
                        color = sourceTextColor,
                        fontWeight = FontWeight.Bold
                    )
                ) {
                    append(sourceText)
                }
                append(SOURCE_TEXT_RESULT_TEXT_SPACE)
                withStyle(
                    style = SpanStyle(
                        color = resultTextColor,
                    )
                ) {
                    append(translation.resultText)
                }
            }
        }

        // Window transparency
        val translationTransparency by targetHandleViewModel.preferenceRepository.translationTransparencyFlow.collectAsStateWithLifecycle(
            lifecycle = lifecycleOwner.lifecycle,
            initialValue = 1.0f
        )

        val automaticTranslationPlayback by targetHandleViewModel.preferenceRepository.automaticTranslationPlaybackFlow.collectAsStateWithLifecycle(
            lifecycle = lifecycleOwner.lifecycle,
            initialValue = false
        )

        if (automaticTranslationPlayback) {
            targetHandleViewModel.playTTS(translation.sourceText!!)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(translationTransparency),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(shadowPadding),
            ) {
                Box(
                    modifier = Modifier
                        .shadow(shadowPadding / 2, shape = roundedCornerShape, clip = true)
                        .background(
                            color = backgroundColor,
                            shape = roundedCornerShape
                        )
                        .border(
                            width = 0.4.dp,
                            color = borderColor,
                            shape = roundedCornerShape
                        )
                        .fillMaxSize()
                        .alpha(if (readyToDisplay) 1.0f else 0f)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    onPauseDismissRunning()
                                    tryAwaitRelease()
                                    onResumeDismissRunning()
                                }
                            )
                        }
                ) {
                    ConstraintLayout(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val (textBox, image) = createRefs()

                        Row(
                            modifier = Modifier
                                .constrainAs(image) {
                                    bottom.linkTo(parent.bottom)
                                    start.linkTo(parent.start)
                                }
                                .wrapContentHeight()
//                                .background(Color(0x33000000))
                                .fillMaxWidth()
                                .padding(start = viewContentPadding),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End
                        ) {
                            Image(
                                painter = painterResource(id = translation.translationKitType!!.logoResourceId),
                                contentDescription = "Translated by ${translation.translationKitType}",
                                modifier = Modifier
                                    .sizeIn(
//                                        maxWidth = 40.dp,
                                        maxHeight = 16.dp,
                                    ),
                                contentScale = ContentScale.Fit,
                            )
                            if (showAiModelBadge) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "+",
                                    color = Color(0xFF747278),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                AiModelBadge(icon = aiModelIcon)
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            // Copy
                            IconButton(
                                onClick = {
                                    onRerunDismissRunning()
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText(
                                        "Translated Text",
                                        sourceText + SOURCE_TEXT_RESULT_TEXT_SPACE + translation.resultText
                                    )
                                    clipboard.setPrimaryClip(clip)
                                },
                                modifier = Modifier
                                    .size(bottomMenuHeight)
                                    .padding(1.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy content",
                                    tint = Color(0xFF747278),
                                    modifier = Modifier
                                        .size(18.dp)
                                        .padding(2.dp)
                                )
                            }

                            // Reply
                            IconButton(
                                onClick = {
                                    coroutineScope.launch {
                                        delay(300)
                                        ReplyActivity.start(
                                            context,
                                            translation.resultText,
                                            translation.detectedLanguageCode,
                                            translation.targetLanguageCode
                                        )
                                        clear()
                                    }
                                },
                                modifier = Modifier
                                    .size(bottomMenuHeight)
                                    .padding(1.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_reply),
                                    contentDescription = "Reply",
                                    modifier = Modifier
                                        .size(24.dp)
                                        .padding(2.dp),
                                    tint = Color(0xFF747278),
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .constrainAs(textBox) {
                                    top.linkTo(parent.top)
                                    bottom.linkTo(image.top)
                                    start.linkTo(parent.start)
                                    end.linkTo(parent.end)
                                    height = Dimension.fillToConstraints
                                }
//                                .background(Color(0x3312df87))
                                .fillMaxWidth()
                                .padding(
                                    start = viewContentPadding,
                                    top = viewContentPadding,
                                    end = viewContentPadding
                                )
                        ) {
                            AutoResizeText(
                                text = annotatedText,
                                maxFontSize = fontSize,
                                enableAutoResize = enableAutoResize,
                                onReadyToDisplay = { readyToDisplay = true },
                                modifier = Modifier.align(Alignment.TopStart)
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            Timber.tag("TranslationView").d("Speaker icon clicked")
                            targetHandleViewModel.playTTS(sourceText!!)
                        },
                        modifier = Modifier
                            .size(bottomMenuHeight)
                            .padding(0.1.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.VolumeUp,
                            contentDescription = "Listen to translation",
                            tint = sourceTextColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun AiModelBadge(icon: AiModelIcon) {
        Box(
            modifier = Modifier
                .sizeIn(minWidth = 16.dp, minHeight = 16.dp)
                .background(Color(icon.backgroundColor), CircleShape)
                .padding(horizontal = 4.dp, vertical = 2.dp)
                .semantics { contentDescription = icon.contentDescription },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = icon.label,
                color = Color(icon.contentColor),
                fontSize = if (icon.label.length == 1) 10.sp else 8.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }

}
















