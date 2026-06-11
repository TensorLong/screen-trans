package com.yiqun.translator.ui.screen.main


import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Point
import android.os.Build
import android.os.Bundle
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.view.WindowInsets
import androidx.activity.compose.BackHandler
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.FiberNew
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VoiceChat
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.yiqun.translator.BuildConfig
import com.yiqun.translator.R
import com.yiqun.translator.data.local.capture.CaptureRepository
import com.yiqun.translator.data.local.preference.DefaultLanguagePolicy
import com.yiqun.translator.data.local.screen.ScreenInfoHolder
import com.yiqun.translator.data.local.secure.ApiKeyInfo
import com.yiqun.translator.data.local.vision.TextDetectMode
import com.yiqun.translator.data.remote.translation.TranslationKitType
import com.yiqun.translator.extensions.finishService
import com.yiqun.translator.extensions.gotoStore
import com.yiqun.translator.extensions.toPx
import com.yiqun.translator.extensions.vibrate
import com.yiqun.translator.ui.common.AutoRefreshEveryMinute
import com.yiqun.translator.ui.common.fontDimensionResource
import com.yiqun.translator.ui.screen.AVDActivity
import com.yiqun.translator.ui.screen.intro.SplashActivity
import com.yiqun.translator.ui.screen.overlay.languagelist.LanguageListView
import com.yiqun.translator.ui.screen.overlay.menubar.MenuBarAttachmentPolicy
import com.yiqun.translator.ui.screen.overlay.menubar.MenuBarView
import com.yiqun.translator.ui.screen.overlay.menubar.SettingsSurface
import com.yiqun.translator.ui.screen.overlay.settings.HelpTextDetectModeView
import com.yiqun.translator.ui.screen.overlay.settings.HelpTranslationKitView
import com.yiqun.translator.ui.screen.overlay.settings.SliderDialogView
import com.yiqun.translator.ui.screen.overlay.targethandle.DeviceFormFactorResolver
import com.yiqun.translator.ui.screen.overlay.targethandle.PointerDisplayPolicy
import com.yiqun.translator.ui.screen.overlay.targethandle.PointerOffset
import com.yiqun.translator.ui.screen.overlay.targethandle.PointerSide
import com.yiqun.translator.ui.screen.overlay.targethandle.TargetHandleView
import com.yiqun.translator.ui.screen.overlay.translation.TranslationView
import com.yiqun.translator.ui.screen.overlay.visiontext.VisionTextView
import com.yiqun.translator.ui.screen.overlay.voicelist.VoiceListView
import com.yiqun.translator.ui.screen.permissions.ScreenCapturePermissionRequesterActivity
import com.yiqun.translator.ui.theme.SenseGroupTranslatorTheme
import com.google.android.play.core.review.ReviewException
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.android.play.core.review.testing.FakeReviewManager
import com.google.firebase.Firebase
import com.google.firebase.analytics.analytics
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.ceil
import kotlin.math.round
import kotlin.math.roundToInt


@AndroidEntryPoint
class SettingsActivity : AVDActivity() {

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, SettingsActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }

        val liveStateFlow = MutableStateFlow(false)

        val aiApiSettingsDialogLiveStateFlow = MutableStateFlow(false)

        val settingsSurfaceFlow = MutableStateFlow(SettingsSurface.HOME)

        val menuBarViewSettlePositionFlow = MutableStateFlow<Point?>(null)
    }

    private val viewModel: SettingsViewModel by viewModels()

    private val settingFloatFlow = MutableStateFlow(1.0f)

    private val settingStringFlow = MutableStateFlow("")

//    private val snackMessageFlow = MutableStateFlow("")

    private var ttsAcquiredWhileResumed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        ScreenInfoHolder.collectAndStoreScreenInfo(this)
        settingsSurfaceFlow.value = SettingsSurface.HOME

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            val policy = ThreadPolicy.Builder().permitAll().build()
            StrictMode.setThreadPolicy(policy)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val isDarkMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            val colorWhite = "#FFf2f1f4".toColorInt()
            val colorBlack = "#FF010102".toColorInt()
            window.decorView.setOnApplyWindowInsetsListener { view, insets ->
                val statusBarInsets = insets.getInsets(WindowInsets.Type.statusBars())
                view.setBackgroundColor(if (isDarkMode) colorBlack else colorWhite)
                view.setPadding(0, statusBarInsets.top, 0, 0)
                insets
            }
            WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = !isDarkMode
            WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = !isDarkMode
        }

        setContent {
            SenseGroupTranslatorTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val isDarkMode = isSystemInDarkTheme()
                    val backgroundColor = if (isDarkMode) Color(0xFF010102) else Color(0xFFf2f1f4)

                    val snackBarHostState = remember { SnackbarHostState() }

                    Scaffold(
                        snackbarHost = { SnackbarHost(hostState = snackBarHostState) }
                    ) { _paddingValues: PaddingValues ->
                        val paddingValues = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            PaddingValues()
                        } else {
                            _paddingValues
                        }
                        Box(
                            modifier = Modifier
                                .background(backgroundColor)
                                .padding(paddingValues)
                        ) {
                            Settings(paddingValues = paddingValues)
                        }
                    }

                }
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (LanguageListView.INSTANCE.isRunning.get()) {
                    LanguageListView.INSTANCE.clear()
                } else if (HelpTextDetectModeView.INSTANCE.isRunning.get()) {
                    HelpTextDetectModeView.INSTANCE.clear()
                } else if (HelpTranslationKitView.INSTANCE.isRunning.get()) {
                    HelpTranslationKitView.INSTANCE.clear()
                } else if (SliderDialogView.INSTANCE.isRunning.get()) {
                    closeTranslation()
                    SliderDialogView.INSTANCE.clear()
                } else if (VoiceListView.INSTANCE.isRunning.get()) {
                    VoiceListView.INSTANCE.clear()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        appReview()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val isDarkMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            val colorWhite = android.graphics.Color.parseColor("#FFf2f1f4")
            val colorBlack = android.graphics.Color.parseColor("#FF010102")
            window.statusBarColor = if (isDarkMode) colorBlack else colorWhite
            window.navigationBarColor = if (isDarkMode) colorBlack else colorWhite
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
    }

    override fun onResume() {
        super.onResume()

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val areNotificationsEnabled = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || notificationManager.areNotificationsEnabled()
        val canDrawOverlays = android.provider.Settings.canDrawOverlays(applicationContext)

        if (!areNotificationsEnabled || !canDrawOverlays) {
            val intent = Intent(applicationContext, SplashActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            finish()
            return
        }

        liveStateFlow.value = true
        acquireTtsForVisibleSettings()

        lifecycleScope.launch {
            syncMenuBarWindow()
            TargetHandleView.castConfigured(
                applicationContext = applicationContext,
                dualPointerMode = viewModel.preferenceRepository.dualPointerEnabledFlow.first()
            )

            delay(1000)
            if (isActive) {
                if (CaptureRepository.mediaProjectionToken == null) {
                    val intent = Intent(this@SettingsActivity, ScreenCapturePermissionRequesterActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                }
            }
        }

    }

    override fun onPause() {
        closeTranslation()
        LanguageListView.INSTANCE.clear()
        SliderDialogView.INSTANCE.clear()
        VoiceListView.INSTANCE.clear()
        HelpTextDetectModeView.INSTANCE.clear()
        HelpTranslationKitView.INSTANCE.clear()
        if (MenuBarView.INSTANCE.isRunning.get()) {
            MenuBarView.INSTANCE.clear()
        }
        liveStateFlow.value = false
        aiApiSettingsDialogLiveStateFlow.value = false
        releaseTtsForVisibleSettings()
        super.onPause()
    }

    private suspend fun syncMenuBarWindow(surface: SettingsSurface = settingsSurfaceFlow.value) {
        if (MenuBarAttachmentPolicy.shouldAttach(liveStateFlow.value, surface)) {
            if (!MenuBarView.INSTANCE.isRunning.get()) {
                MenuBarView.INSTANCE.cast(applicationContext)
            }
        } else if (MenuBarView.INSTANCE.isRunning.get()) {
            MenuBarView.INSTANCE.clear()
        }
    }

    private fun acquireTtsForVisibleSettings() {
        if (!ttsAcquiredWhileResumed) {
            viewModel.ttsRepository.acquire()
            ttsAcquiredWhileResumed = true
        }
    }

    private fun releaseTtsForVisibleSettings() {
        if (ttsAcquiredWhileResumed) {
            viewModel.ttsRepository.release()
            ttsAcquiredWhileResumed = false
        }
    }

    private var _textDetectMode: TextDetectMode? = null

    private fun runTranslation(point: Point, textDetectMode: TextDetectMode) {
        _textDetectMode = textDetectMode
        TargetHandleView.INSTANCE.runTranslation(point, textDetectMode)
    }

    private fun closeTranslation() {
        TargetHandleView.INSTANCE.closeTranslation(_textDetectMode)
    }

    enum class MenuItemPosition {
        Single,
        Top,
        Middle,
        Bottom,
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //                                                                                            //
    //                                          Composable                                        //
    //                                                                                            //
    ////////////////////////////////////////////////////////////////////////////////////////////////
    @SuppressLint("LocalContextConfigurationRead")
    @Composable
    fun Settings(
        paddingValues: PaddingValues,
    ) {
        val context = LocalContext.current
        val localView = LocalView.current
        val lifecycleOwner = LocalLifecycleOwner.current
        val coroutineScope = rememberCoroutineScope()

        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val versionCode: Long = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            packageInfo.versionCode.toLong()
        }

        val latestVersionCode by viewModel.latestVersionCodeFlow.collectAsStateWithLifecycle(
            lifecycle = lifecycleOwner.lifecycle,
            initialValue = 0
        )

        val layoutDirection = LocalLayoutDirection.current
        val isRtl = layoutDirection == LayoutDirection.Rtl
        val configuration = LocalConfiguration.current
        val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

        val contentPadding = 9.dp
        val cornerRound = 32.dp
        val startPadding = paddingValues.calculateLeftPadding(layoutDirection).toPx(context)

        val isDarkMode = isSystemInDarkTheme()
        val contentColor = if (isDarkMode) Color(0xFFfcfcfc) else Color(0xFF010000)
        val subContentColor = if (isDarkMode) Color(0xFFb7b7ba) else Color(0xFF626265)
        val switchScale = 0.70f
        val switchThumbColor = if (isDarkMode) Color.Black else Color.White
        val switchTrackColor = if (isDarkMode) Color(0xFF6a91b2) else Color(0xFF446987)
        val dividerColor = if (isDarkMode) Color(0xFF343434) else Color(0xFFd5d5d5)
        val buttonColor = if (isDarkMode) Color(0xFFfafafa) else Color(0xFF171717)

        // Pointer docking delay
        val dockingDelayTextOffset = remember { mutableStateOf(Point(0, 0)) }
        val dockingDelaySubtextOffset = remember { mutableStateOf(Point(0, 0)) }
        val dockingDelay by viewModel.preferenceRepository.dockingDelayFlow.collectAsStateWithLifecycle(
            lifecycle = lifecycleOwner.lifecycle,
            // Must match the repository default (15s = docking off); 3000L made the first
            // frames render "3.0 sec" and treat docking as enabled before DataStore emitted.
            initialValue = 15000L
        )

        // Haptic feedback to detection
        val dragHandleHaptic by viewModel.preferenceRepository.dragHandleHapticFlow.collectAsStateWithLifecycle(
            lifecycle = lifecycleOwner.lifecycle,
            initialValue = false
        )

        val defaultPointerOffset = remember { viewModel.preferenceRepository.defaultPointerOffset }
        val pointerLeftOffset by viewModel.preferenceRepository.pointerLeftOffsetFlow.collectAsStateWithLifecycle(
            lifecycle = lifecycleOwner.lifecycle,
            initialValue = defaultPointerOffset
        )
        val pointerRightOffset by viewModel.preferenceRepository.pointerRightOffsetFlow.collectAsStateWithLifecycle(
            lifecycle = lifecycleOwner.lifecycle,
            initialValue = defaultPointerOffset
        )
        val dualPointerEnabled by viewModel.preferenceRepository.dualPointerEnabledFlow.collectAsStateWithLifecycle(
            lifecycle = lifecycleOwner.lifecycle,
            initialValue = PointerDisplayPolicy.defaultDualPointerEnabled(DeviceFormFactorResolver.resolve(configuration))
        )
        val supportsDualPointer = PointerDisplayPolicy.defaultDualPointerEnabled(DeviceFormFactorResolver.resolve(configuration))
        var showPointerCalibration by remember { mutableStateOf(false) }

        // Translation transparency
        val translationTransparencyTextOffset = remember { mutableStateOf(Point(0, 0)) }
        val translationTransparencySubtextOffset = remember { mutableStateOf(Point(0, 0)) }
        val translationPoint = remember { mutableStateOf(Point(0, 0)) }
        val translationTransparency by viewModel.preferenceRepository.translationTransparencyFlow.collectAsStateWithLifecycle(
            lifecycle = lifecycleOwner.lifecycle,
            initialValue = 1.0f
        )

        // Translation close delay
        val translationCloseDelayTextOffset = remember { mutableStateOf(Point(0, 0)) }
        val translationCloseDelaySubtextOffset = remember { mutableStateOf(Point(0, 0)) }
        val translationCloseDelay by viewModel.preferenceRepository.translationCloseDelayFlow.collectAsStateWithLifecycle(
            lifecycle = lifecycleOwner.lifecycle,
            initialValue = 1600L
        )

        // Reply transparency
        val replyTransparencyTextOffset = remember { mutableStateOf(Point(0, 0)) }
        val replyTransparencySubtextOffset = remember { mutableStateOf(Point(0, 0)) }
        val replyTransparency by viewModel.preferenceRepository.replyTransparencyFlow.collectAsStateWithLifecycle(
            lifecycle = lifecycleOwner.lifecycle,
            initialValue = 1.0f
        )

        var showAiApiSettingsDialog by remember { mutableStateOf(false) }
        var aiApiKeyIsSet by remember {
            mutableStateOf(ApiKeyInfo.chatgptKeyAvailable(context))
        }
        LaunchedEffect(showPointerCalibration, showAiApiSettingsDialog) {
            val surface = when {
                showPointerCalibration -> SettingsSurface.POINTER_DISTANCE
                showAiApiSettingsDialog -> SettingsSurface.AI_API
                else -> SettingsSurface.HOME
            }
            settingsSurfaceFlow.value = surface
            syncMenuBarWindow(surface)
            aiApiSettingsDialogLiveStateFlow.value = showAiApiSettingsDialog
        }
        var pointerCalibrationWasOpen by remember { mutableStateOf(false) }
        LaunchedEffect(showPointerCalibration) {
            if (showPointerCalibration) {
                pointerCalibrationWasOpen = true
                closeTranslation()
                TargetHandleView.clearAll()
                VisionTextView.INSTANCE.clear()
                TranslationView.INSTANCE.clear()
            } else if (pointerCalibrationWasOpen) {
                TargetHandleView.castConfigured(
                    applicationContext = applicationContext,
                    dualPointerMode = dualPointerEnabled
                )
                pointerCalibrationWasOpen = false
            }
        }

        // Automatic translation playback
        val automaticTranslationPlayback by viewModel.preferenceRepository.automaticTranslationPlaybackFlow.collectAsStateWithLifecycle(
            lifecycle = lifecycleOwner.lifecycle,
            initialValue = false
        )

        // E-ink display mode
        val einkDisplayMode by viewModel.preferenceRepository.einkDisplayModeFlow.collectAsStateWithLifecycle(
            lifecycle = lifecycleOwner.lifecycle,
            initialValue = false
        )

        // TTS Speech rate
        val ttsSpeechRateTextOffset = remember { mutableStateOf(Point(0, 0)) }
        val ttsSpeechRateIconOffset = remember { mutableStateOf(Point(0, 0)) }
        val ttsSpeechRate by viewModel.preferenceRepository.ttsSpeechRateFlow.collectAsStateWithLifecycle(
            lifecycle = lifecycleOwner.lifecycle,
            initialValue = 1.0f
        )

        // TTS Voices
        val ttsAvailableVoices by viewModel.ttsRepository.availableVoicesFlow.collectAsStateWithLifecycle(
            lifecycle = lifecycleOwner.lifecycle,
            initialValue = emptyList()
        )

        // TTS Voice
        val ttsCurrentVoice by viewModel.ttsRepository.currentVoiceFlow.collectAsStateWithLifecycle(
            lifecycle = lifecycleOwner.lifecycle,
            initialValue = null
        )

        // Text detect mode
        val textDetectMode by viewModel.preferenceRepository.textDetectModeFlow.collectAsStateWithLifecycle(
            lifecycle = lifecycleOwner.lifecycle,
            initialValue = TextDetectMode.SENSE_GROUP
        )

        // source language
        val sourceLanguageCode by viewModel.preferenceRepository.sourceLanguageCodeFlow.collectAsStateWithLifecycle(
            lifecycle = lifecycleOwner.lifecycle,
            initialValue = "auto"
        )
        val sourceLanguage = viewModel.translationRepository.getSupportedSourceLanguage(sourceLanguageCode)

        // target language
        val targetLanguageCode by viewModel.preferenceRepository.targetLanguageCodeFlow.collectAsStateWithLifecycle(
            lifecycle = lifecycleOwner.lifecycle,
            initialValue = DefaultLanguagePolicy.defaultTargetLanguageCode(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    context.resources.configuration.locales.get(0)
                } else {
                    @Suppress("DEPRECATION")
                    context.resources.configuration.locale
                }
            )
        )
        val targetLanguage = viewModel.translationRepository.getSupportedTargetLanguage(targetLanguageCode)

        // translationKit Type
        val kitType by viewModel.preferenceRepository.translationKitTypeFlow.collectAsStateWithLifecycle(
            lifecycle = lifecycleOwner.lifecycle,
            initialValue = TranslationKitType.GOOGLE
        )

        fun getTransparencyValueText(transparency: Float): String {
            return "${ceil((1.0f - transparency) * 100).toInt()}%"
        }

        fun getSecondValueText(second: Long): String {
            return "${round(second / 1000.0 * 10) / 10} sec"
        }

        AutoRefreshEveryMinute {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // ActionBar
                Row(
                    modifier = Modifier
                        .height(58.dp)
                        .fillMaxWidth()
//                    .background(Color(0x3399ffff))
                        .padding(start = 18.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(id = R.string.settings_title),
                        color = contentColor,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 22.sp),
                        modifier = Modifier
                            .align(Alignment.CenterVertically)
                            .weight(1f)
                    )

                    // Share IconButton
                    IconButton(
                        onClick = {
//                        Toast.makeText(context, "Share clicked", Toast.LENGTH_SHORT).show()
                            val appPackageName = context.packageName
                            val appStoreLink = "https://play.google.com/store/apps/details?id=$appPackageName"
                            val appName = context.getString(R.string.app_name)
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "$appName: $appStoreLink")
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share via"))
                        },
                        modifier = Modifier.align(Alignment.CenterVertically)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            modifier = Modifier
                                .size(18.dp)
                                .alpha(0.75f),
                            tint = contentColor
                        )
                    }

                    // Power IconButton
                    IconButton(
                        onClick = {
                            viewModel.analyticsRepository.settingsReport(
                                dockDelay = dockingDelay.toString(),
                                haptic = dragHandleHaptic.toString(),
                                menuTransparency = "removed",
                                menuComposition = "removed",
                                transTransparency = (translationTransparency * 100).roundToInt().toString(),
                                closeDelay = translationCloseDelay.toString(),
                                replyTransparency = (replyTransparency * 100).roundToInt().toString(),
                                autoTTS = automaticTranslationPlayback.toString(),
                                TTSVoice = ttsCurrentVoice?.name ?: "unknown",
                                TTSRate = BigDecimal(ttsSpeechRate.toDouble()).setScale(1, RoundingMode.HALF_UP).toString(),
                            )

                            coroutineScope.launch {
                                Firebase.analytics.setAnalyticsCollectionEnabled(false)
                                delay(200L)
                                finish()
//                            moveTaskToBack(true)
                                delay(200L)
                                applicationContext.finishService()
                            }
                        },
                        modifier = Modifier.align(Alignment.CenterVertically)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = "Exit App",
                            modifier = Modifier
                                .size(21.dp)
                                .alpha(0.75f),
                            tint = contentColor
                        )
                    }
                }

                // MenuBarView Area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (isPortrait) 56.dp else 38.dp)
//                    .background(Color(0x5517fa23))
                        .onGloballyPositioned { layoutCoordinates ->
                            val center = layoutCoordinates.boundsInRoot().center
                            Timber
                                .tag(TAG)
                                .d("paddingValues $paddingValues")
                            val endPadding = paddingValues
                                .calculateRightPadding(layoutDirection)
                                .toPx(context)
                            Timber
                                .tag(TAG)
                                .d("startPadding $startPadding")
                            Timber
                                .tag(TAG)
                                .d("endPadding $endPadding")
                            val posX = (endPadding - startPadding) / 2
                            Timber
                                .tag(TAG)
                                .d("posX $posX")

                            val topPadding = paddingValues
                                .calculateTopPadding()
                                .toPx(context)
                            Timber
                                .tag(TAG)
                                .d("topPadding $topPadding")
                            val posY = center.y.toInt() - topPadding - (if (isPortrait) 0.dp else 12.dp).toPx(context)
                            Timber
                                .tag(TAG)
                                .d("menuBarViewSettlePosition ${Point(posX, posY)}")

                            menuBarViewSettlePositionFlow.value = Point(posX, posY)
                        }
                )

                Box(
                    modifier = Modifier.padding(start = contentPadding, top = contentPadding, end = contentPadding)
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(cornerRound),
                        color = Color.Transparent
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(viewModel.scrollState)
                        ) {
                            MenuCategory(
                                painter = painterResource(id = R.drawable.ic_drag_handle),
                                categoryName = getString(R.string.settings_menu_cat_pointer),
                                isRtl = isRtl,
                            )

                            MenuItem(
                                menuItemPosition = MenuItemPosition.Top,
                                onClick = {
                                    showPointerCalibration = true
                                }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 50.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    MenuText(text = getString(R.string.settings_menu_pointer_distance))
                                    Text(
                                        modifier = Modifier.padding(end = 10.dp),
                                        text = if (dualPointerEnabled) getString(R.string.settings_menu_pointer_distance_dual) else getString(R.string.settings_menu_pointer_distance_single),
                                        color = subContentColor,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = fontDimensionResource(R.dimen.settings_menu_subtext_size)),
                                    )
                                }
                            }

                            if (supportsDualPointer) {
                                MenuItem(
                                    menuItemPosition = MenuItemPosition.Middle,
                                    onClick = {
                                        val updated = !dualPointerEnabled
                                        viewModel.updateDualPointerEnabled(updated)
                                        coroutineScope.launch {
                                            TargetHandleView.castConfigured(applicationContext, updated, repositionPrimary = true)
                                        }
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        MenuText(text = getString(R.string.settings_menu_dual_pointer))
                                        Switch(
                                            checked = dualPointerEnabled,
                                            onCheckedChange = { value ->
                                                viewModel.updateDualPointerEnabled(value)
                                                coroutineScope.launch {
                                                    TargetHandleView.castConfigured(applicationContext, value, repositionPrimary = true)
                                                }
                                            },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = switchThumbColor,
                                                checkedTrackColor = switchTrackColor
                                            ),
                                            modifier = Modifier
                                                .scale(switchScale)
                                                .align(Alignment.CenterVertically)
                                                .semantics {
                                                    contentDescription = if (dualPointerEnabled) {
                                                        "Dual pointer on"
                                                    } else {
                                                        "Dual pointer off"
                                                    }
                                                }
                                        )
                                    }
                                }
                            }

                            MenuItem(
                                menuItemPosition = MenuItemPosition.Middle,
                                onClick = {
                                    coroutineScope.launch {
                                        settingStringFlow.value = getSecondValueText(dockingDelay)
                                        SliderDialogView.INSTANCE.cast(
                                            applicationContext = applicationContext,
                                            initialValue = dockingDelay.toFloat(),
                                            valueRange = 1000f..15000f,
                                            steps = 13,
                                            onValueChange = { value ->
                                                Timber.tag(TAG).d("Pointer docking delay onValueChange : $value")
                                                viewModel.updateDockingDelay(value.toLong())
                                                viewModel.updateDragHandleDocking(value < 15000.0f)
                                                settingStringFlow.value = getSecondValueText(value.toLong())
                                            },
                                            menuText = Pair(getString(R.string.settings_menu_pointer_docking_delay), dockingDelayTextOffset.value),
                                            dockingDelayText = Pair(settingStringFlow, dockingDelaySubtextOffset.value),
                                            onDismissRequest = {
                                                SliderDialogView.INSTANCE.clear()
                                            },
                                        )
                                    }
                                }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 50.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    MenuText(
                                        text = getString(R.string.settings_menu_pointer_docking_delay),
                                        onTextPositioned = { offset ->
                                            dockingDelayTextOffset.value = Point(offset.x - startPadding, offset.y)
                                        },
                                    )
                                    Box(
                                        modifier = Modifier
                                            .height(38.dp)
                                            .width(80.dp)
//                                        .background(Color(0x33aaff22))
                                            .onGloballyPositioned { layoutCoordinates ->
                                                val offset = layoutCoordinates.positionOnScreen()
                                                val startPadding = paddingValues
                                                    .calculateLeftPadding(layoutDirection)
                                                    .toPx(context)
                                                val posX = offset.x.toInt() + layoutCoordinates.size.width - startPadding
//                                            Timber.tag(TAG).d("Pointer docking delay posX $posX")
                                                dockingDelaySubtextOffset.value = Point(posX, offset.y.toInt())
                                            },
                                        contentAlignment = Alignment.CenterEnd
                                    ) {
                                        if (dockingDelay < 15000.0f) {
                                            Text(
                                                modifier = Modifier.padding(end = 6.dp),
                                                text = getSecondValueText(dockingDelay),
                                                color = subContentColor,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = fontDimensionResource(R.dimen.settings_menu_subtext_size)),
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.AllInclusive,
                                                contentDescription = "Pointer docking delay Infinity",
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .padding(end = 12.dp),
                                                tint = subContentColor
                                            )
                                        }
                                    }
                                }
                            }

                            MenuItem(
                                menuItemPosition = MenuItemPosition.Middle,
                                onClick = {
                                    if (!dragHandleHaptic) {
                                        context.vibrate()
                                    }
                                    viewModel.updateDragHandleHaptic(!dragHandleHaptic)
                                }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    MenuText(
                                        text = getString(R.string.settings_menu_haptic_feedback_to_detection),
                                    )
                                    Switch(
                                        checked = dragHandleHaptic,
                                        onCheckedChange = { value ->
                                            if (value) {
                                                context.vibrate()
                                            }
                                            viewModel.updateDragHandleHaptic(value)
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = switchThumbColor,
                                            checkedTrackColor = switchTrackColor
                                        ),
                                        modifier = Modifier
                                            .scale(switchScale)
                                            .align(Alignment.CenterVertically)
                                            .semantics {
                                                contentDescription = if (dragHandleHaptic) {
                                                    "Haptic feedback to detection on"
                                                } else {
                                                    "Haptic feedback to detection off"
                                                }
                                            }
                                    )
                                }
                            }

                            MenuItem(
                                menuItemPosition = MenuItemPosition.Bottom,
                                onClick = {
                                    viewModel.updateEinkDisplayMode(!einkDisplayMode)
                                }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    MenuText(
                                        text = getString(R.string.settings_menu_eink_display_mode),
                                    )
                                    Switch(
                                        checked = einkDisplayMode,
                                        onCheckedChange = { value ->
                                            viewModel.updateEinkDisplayMode(value)
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = switchThumbColor,
                                            checkedTrackColor = switchTrackColor
                                        ),
                                        modifier = Modifier
                                            .scale(switchScale)
                                            .align(Alignment.CenterVertically)
                                            .semantics {
                                                contentDescription = if (einkDisplayMode) {
                                                    "E-ink display mode on"
                                                } else {
                                                    "E-ink display mode off"
                                                }
                                            }
                                    )
                                }
                            }

                            MenuCategory(
                                painter = painterResource(id = R.drawable.ic_ai),
                                categoryName = getString(R.string.settings_menu_cat_ai),
                                iconSize = 25.dp,
                                isRtl = isRtl,
                            )

                            MenuTextItem(
                                menuItemPosition = MenuItemPosition.Single,
                                text = getString(R.string.settings_menu_ai_api_settings),
                                paddingValues = paddingValues,
                                subText = if (aiApiKeyIsSet) {
                                    getString(R.string.settings_menu_ai_api_set)
                                } else {
                                    getString(R.string.settings_menu_ai_api_not_set)
                                },
                                onClick = {
                                    showAiApiSettingsDialog = true
                                }
                            )

                            MenuCategory(
                                painter = painterResource(id = R.drawable.ic_translation_window),
                                categoryName = getString(R.string.settings_menu_cat_translation),
                                isRtl = isRtl,
                            )

                            MenuTextItem(
                                menuItemPosition = MenuItemPosition.Top,
                                text = getString(R.string.settings_menu_translation_transparency),
                                paddingValues = paddingValues,
                                onTextPositioned = { offset ->
                                    translationTransparencyTextOffset.value = Point(offset.x - startPadding, offset.y)
                                },
                                onGloballyPositioned = { layoutCoordinates ->
                                    val center = layoutCoordinates.boundsInWindow().center
                                    val startPadding = paddingValues.calculateLeftPadding(layoutDirection).toPx(context)
                                    val posX = center.x.toInt() - startPadding
                                    translationPoint.value = Point(posX, center.y.toInt())
                                },
                                subText = getTransparencyValueText(translationTransparency),
                                onSubtextPositioned = { offset ->
                                    translationTransparencySubtextOffset.value = Point(offset.x - startPadding, offset.y)
                                },
                                onClick = {
                                    Timber.tag(TAG).d("onClick Translation transparency ${CaptureRepository.mediaProjectionToken}")
                                    if (CaptureRepository.mediaProjectionToken == null) {
                                        val intent = Intent(
                                            context,
                                            ScreenCapturePermissionRequesterActivity::class.java
                                        )
                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(intent)
                                    } else {
                                        runTranslation(translationPoint.value, textDetectMode)
                                        coroutineScope.launch {
                                            settingStringFlow.value = getTransparencyValueText(translationTransparency)
                                            SliderDialogView.INSTANCE.cast(
                                                applicationContext = applicationContext,
                                                initialValue = 1.0f - translationTransparency,
                                                valueRange = 0.0f..0.5f,
                                                onValueChange = { value ->
                                                    viewModel.updateTranslationTransparency(1.0f - value)
                                                    settingStringFlow.value = getTransparencyValueText(1.0f - value)
                                                },
                                                menuText = Pair(getString(R.string.settings_menu_translation_transparency), translationTransparencyTextOffset.value),
                                                menuSubtext = Pair(settingStringFlow, translationTransparencySubtextOffset.value),
                                                onDismissRequest = {
                                                    closeTranslation()
                                                    SliderDialogView.INSTANCE.clear()
                                                },
                                            )
                                        }
                                    }
                                }
                            )

                            MenuTextItem(
                                menuItemPosition = MenuItemPosition.Middle,
                                text = getString(R.string.settings_menu_translation_close_delay),
                                paddingValues = paddingValues,
                                onTextPositioned = { offset ->
                                    translationCloseDelayTextOffset.value = Point(offset.x - startPadding, offset.y)
                                },
                                subText = getSecondValueText(translationCloseDelay),
                                onSubtextPositioned = { offset ->
                                    translationCloseDelaySubtextOffset.value = Point(offset.x - startPadding, offset.y)
                                },
                                onClick = {
                                    coroutineScope.launch {
                                        settingStringFlow.value = getSecondValueText(translationCloseDelay)
                                        SliderDialogView.INSTANCE.cast(
                                            applicationContext = applicationContext,
                                            initialValue = translationCloseDelay.toFloat(),
                                            valueRange = 500.0f..7000.0f,
                                            steps = 12,
                                            onValueChange = { value ->
                                                viewModel.updateTranslationCloseDelay(value.toLong())
                                                settingStringFlow.value = getSecondValueText(value.toLong())
                                            },
                                            menuText = Pair(getString(R.string.settings_menu_translation_close_delay), translationCloseDelayTextOffset.value),
                                            menuSubtext = Pair(settingStringFlow, translationCloseDelaySubtextOffset.value),
                                            onDismissRequest = {
                                                SliderDialogView.INSTANCE.clear()
                                            },
                                        )
                                    }
                                }
                            )

                            MenuItem(
                                menuItemPosition = MenuItemPosition.Middle,
                                onClick = {
                                    viewModel.updateAutomaticTranslationPlayback(!automaticTranslationPlayback)
                                }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    MenuText(
                                        text = getString(R.string.settings_menu_automated_read_aloud),
                                    )
                                    Switch(
                                        checked = automaticTranslationPlayback,
                                        onCheckedChange = { value ->
                                            viewModel.updateAutomaticTranslationPlayback(value)
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = switchThumbColor,
                                            checkedTrackColor = switchTrackColor
                                        ),
                                        modifier = Modifier
                                            .scale(switchScale)
                                            .align(Alignment.CenterVertically)
                                            .semantics {
                                                contentDescription = if (automaticTranslationPlayback) {
                                                    "Automated read aloud on"
                                                } else {
                                                    "Automated read aloud off"
                                                }
                                            },
                                    )
                                }
                            }

                            MenuTextItem(
                                menuItemPosition = MenuItemPosition.Bottom,
                                text = getString(R.string.settings_menu_reply_transparency),
                                paddingValues = paddingValues,
                                onTextPositioned = { offset ->
                                    replyTransparencyTextOffset.value = Point(offset.x - startPadding, offset.y)
                                },
                                subText = getTransparencyValueText(replyTransparency),
                                onSubtextPositioned = { offset ->
                                    replyTransparencySubtextOffset.value = Point(offset.x - startPadding, offset.y)
                                },
                                onClick = {
                                    coroutineScope.launch {
                                        settingStringFlow.value = getTransparencyValueText(replyTransparency)
                                        SliderDialogView.INSTANCE.cast(
                                            applicationContext = applicationContext,
                                            initialValue = 1.0f - replyTransparency,
                                            valueRange = 0.0f..0.5f,
                                            onValueChange = { value ->
                                                viewModel.updateReplyTransparency(1.0f - value)
                                                settingStringFlow.value = getTransparencyValueText(1.0f - value)
                                            },
                                            menuText = Pair(getString(R.string.settings_menu_reply_transparency), replyTransparencyTextOffset.value),
                                            menuSubtext = Pair(settingStringFlow, replyTransparencySubtextOffset.value),
                                            onDismissRequest = {
                                                SliderDialogView.INSTANCE.clear()
                                            },
                                        )
                                    }
                                }
                            )

                            if (ttsCurrentVoice != null) {
                                MenuCategory(
                                    icon = Icons.Default.VoiceChat,
                                    categoryName = getString(R.string.settings_menu_cat_tts),
                                    isRtl = isRtl,
                                )

                                if (ttsAvailableVoices.isNotEmpty()) {
                                    MenuTextItem(
                                        menuItemPosition = MenuItemPosition.Top,
                                        text = getString(R.string.settings_menu_tts_voices),
                                        paddingValues = paddingValues,
                                        subText = ttsCurrentVoice?.name,
                                        onClick = {
                                            coroutineScope.launch {
                                                VoiceListView.INSTANCE.cast(applicationContext)
                                            }
                                        }
                                    )
                                }

                                MenuItem(
                                    menuItemPosition = if (ttsAvailableVoices.isEmpty()) MenuItemPosition.Single else MenuItemPosition.Bottom,
                                    onClick = {
                                        coroutineScope.launch {
                                            settingFloatFlow.value = ttsSpeechRate
                                            SliderDialogView.INSTANCE.cast(
                                                applicationContext = applicationContext,
                                                initialValue = ttsSpeechRate,
                                                valueRange = 0.5f..2.0f,
                                                steps = 6,
                                                onValueChange = { value ->
                                                    viewModel.updateTtsSpeechRate(value)
                                                    settingFloatFlow.value = value
                                                },
                                                menuText = Pair(getString(R.string.settings_menu_tts_rate), ttsSpeechRateTextOffset.value),
                                                speechRateText = Pair(settingFloatFlow, ttsSpeechRateIconOffset.value),
                                                onDismissRequest = {
                                                    SliderDialogView.INSTANCE.clear()
                                                },
                                            )
                                        }
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 50.dp)
                                            .padding(end = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        MenuText(
                                            text = getString(R.string.settings_menu_tts_rate),
                                            onTextPositioned = { offset ->
                                                ttsSpeechRateTextOffset.value = Point(offset.x - startPadding, offset.y)
                                            },
                                        )

                                        /*
                                            Speech rate. 1.0 is the normal speech rate,
                                            lower values slow down the speech (0.5 is half the normal speech rate),
                                            greater values accelerate it (2.0 is twice the normal speech rate).
                                         */
                                        var isToggled by remember { mutableStateOf(false) }
                                        LaunchedEffect(ttsSpeechRate) {
                                            while (true) {
                                                delay((((2.2f - ttsSpeechRate) / 4) * 1000).toLong())
                                                isToggled = !isToggled
                                            }
                                        }

                                        Crossfade(targetState = isToggled, label = "Crossfade") { toggled ->
                                            val imageResource = if (toggled) R.drawable.tts_rate_0 else R.drawable.tts_rate_1
                                            Image(
                                                painter = painterResource(id = imageResource),
                                                contentDescription = getString(R.string.settings_menu_tts_rate),
                                                colorFilter = ColorFilter.tint(Color(0xFF848487)),
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .onGloballyPositioned { layoutCoordinates ->
                                                        val offset = layoutCoordinates.positionOnScreen()
                                                        val startPadding = paddingValues
                                                            .calculateLeftPadding(layoutDirection)
                                                            .toPx(context)
                                                        val posX = offset.x.toInt() - startPadding
                                                        ttsSpeechRateIconOffset.value = Point(posX, offset.y.toInt())
                                                    }
                                            )
                                        }
                                    }
                                }
                            }

                            MenuCategory(
                                icon = Icons.Outlined.Info,
                                categoryName = getString(R.string.settings_menu_cat_about),
                                isRtl = isRtl,
                            )

                            MenuItem(
                                menuItemPosition = MenuItemPosition.Single,
                                onClick = {
                                    context.gotoStore(
                                        newTask = false,
                                        finishService = false
                                    )
                                    viewModel.analyticsRepository.screenViewReport("AppVersion")
                                },
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 50.dp)
                                        .padding(end = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    MenuText(
                                        text = getString(R.string.settings_menu_app_version),
                                    )
                                    if (latestVersionCode > versionCode) {
                                        Row(
                                            modifier = Modifier.wrapContentSize(),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            MenuSubText(
                                                text = "${packageInfo.versionName}",
                                                paddingValues = paddingValues
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Icon(
                                                imageVector = Icons.Default.FiberNew,
                                                contentDescription = "new version",
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .padding(end = 8.dp),
                                                tint = Color(0xFF446987)
                                            )
                                        }
                                    } else {
                                        MenuSubText(
                                            text = "${packageInfo.versionName}  ${getString(R.string.settings_menu_app_version_latest)}",
                                            paddingValues = paddingValues
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }
        }

        if (showPointerCalibration) {
            BackHandler {
                showPointerCalibration = false
            }
            PointerDistanceCalibrationView(
                leftOffset = pointerLeftOffset,
                rightOffset = pointerRightOffset,
                defaultOffset = defaultPointerOffset,
                dualPointerEnabled = supportsDualPointer && dualPointerEnabled,
                onDismissRequest = {
                    closeTranslation()
                    showPointerCalibration = false
                },
                onConfirm = { left, right ->
                    closeTranslation()
                    viewModel.updatePointerOffset(left, right)
                    showPointerCalibration = false
                },
            )
        }

        if (showAiApiSettingsDialog) {
            AiApiSettingsDialog(
                onDismissRequest = { showAiApiSettingsDialog = false },
                onSaved = {
                    aiApiKeyIsSet = ApiKeyInfo.chatgptKeyAvailable(context)
                    showAiApiSettingsDialog = false
                    android.widget.Toast.makeText(
                        context,
                        context.getString(R.string.settings_menu_ai_api_saved),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                },
                onCleared = {
                    aiApiKeyIsSet = false
                    showAiApiSettingsDialog = false
                    android.widget.Toast.makeText(
                        context,
                        context.getString(R.string.settings_menu_ai_api_cleared),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                },
                onFetchModels = { viewModel.fetchAiModels() },
                onTestConnection = { viewModel.testAiConnection() },
            )
        }
    }

    @Composable
    fun PointerDistanceCalibrationView(
        leftOffset: PointerOffset,
        rightOffset: PointerOffset,
        defaultOffset: PointerOffset,
        dualPointerEnabled: Boolean,
        onDismissRequest: () -> Unit,
        onConfirm: (PointerOffset, PointerOffset) -> Unit,
    ) {
        var currentLeftOffset by remember(leftOffset) { mutableStateOf(leftOffset) }
        var currentRightOffset by remember(rightOffset) { mutableStateOf(rightOffset) }
        var activeSide by remember(dualPointerEnabled) { mutableStateOf(PointerSide.LEFT) }
        var previewTick by remember { mutableStateOf(0) }
        val isDarkMode = isSystemInDarkTheme()
        val backgroundColor = if (isDarkMode) Color(0xFF101012) else Color(0xFFf8f8fb)
        val contentColor = if (isDarkMode) Color(0xFFfcfcfc) else Color(0xFF010000)
        val subContentColor = if (isDarkMode) Color(0xFFc8c8cc) else Color(0xFF626265)
        val activeOffset = when (activeSide) {
            PointerSide.LEFT -> currentLeftOffset
            PointerSide.RIGHT -> currentRightOffset
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = backgroundColor
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = getString(R.string.settings_menu_pointer_distance),
                        color = contentColor,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = getString(R.string.settings_menu_pointer_distance_helper),
                        color = subContentColor,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (dualPointerEnabled) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    activeSide = PointerSide.LEFT
                                    previewTick = 0
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (activeSide == PointerSide.LEFT) Color(0xFF446987) else Color(0xFF777777)
                                )
                            ) {
                                Text(getString(R.string.settings_menu_pointer_left))
                            }
                            Button(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    activeSide = PointerSide.RIGHT
                                    previewTick = 0
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (activeSide == PointerSide.RIGHT) Color(0xFF446987) else Color(0xFF777777)
                                )
                            ) {
                                Text(getString(R.string.settings_menu_pointer_right))
                            }
                        }
                    }
                }

                PointerCalibrationTarget(
                    modifier = Modifier.fillMaxWidth(),
                    label = if (dualPointerEnabled) {
                        when (activeSide) {
                            PointerSide.LEFT -> getString(R.string.settings_menu_pointer_left)
                            PointerSide.RIGHT -> getString(R.string.settings_menu_pointer_right)
                        }
                    } else {
                        getString(R.string.settings_menu_pointer_single)
                    },
                    offset = activeOffset,
                    previewTick = previewTick,
                    onOffsetChange = { offset ->
                        previewTick = 0
                        when (activeSide) {
                            PointerSide.LEFT -> currentLeftOffset = offset
                            PointerSide.RIGHT -> currentRightOffset = offset
                        }
                    },
                    onPreview = { previewTick++ },
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                previewTick = 0
                                when (activeSide) {
                                    PointerSide.LEFT -> currentLeftOffset = defaultOffset
                                    PointerSide.RIGHT -> currentRightOffset = defaultOffset
                                }
                            },
                        ) {
                            Text(getString(R.string.settings_menu_pointer_distance_reset))
                        }
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = onDismissRequest,
                        ) {
                            Text(getString(R.string.settings_menu_ai_api_cancel))
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onConfirm(currentLeftOffset, currentRightOffset) },
                    ) {
                        Text(getString(R.string.settings_menu_ai_api_save))
                    }
                }
            }
        }
    }

    @Composable
    fun PointerCalibrationTarget(
        modifier: Modifier = Modifier,
        label: String,
        offset: PointerOffset,
        previewTick: Int,
        onOffsetChange: (PointerOffset) -> Unit,
        onPreview: () -> Unit,
    ) {
        val isDarkMode = isSystemInDarkTheme()
        val borderColor = if (isDarkMode) Color(0xFF6a91b2) else Color(0xFF446987)
        val textColor = if (isDarkMode) Color(0xFFfcfcfc) else Color(0xFF010000)
        val previewVisible = previewTick > 0
        val latestOffset by rememberUpdatedState(offset)

        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                color = textColor,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .background(
                        color = if (isDarkMode) Color(0xFF1f1f22) else Color.White,
                        shape = RoundedCornerShape(22.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val targetCenter = Offset(size.width / 2f, size.height / 2f)
                    val handleCenter = Offset(
                        x = targetCenter.x - offset.x,
                        y = targetCenter.y - offset.y,
                    )
                    drawLine(
                        color = borderColor.copy(alpha = 0.65f),
                        start = handleCenter,
                        end = targetCenter,
                        strokeWidth = 3f,
                    )
                    drawCircle(
                        color = borderColor.copy(alpha = 0.28f),
                        radius = 8f,
                        center = targetCenter,
                    )
                }
                Box(
                    modifier = Modifier
                        .padding(12.dp)
                        .background(
                            color = if (previewVisible) Color(0x3348baef) else Color.Transparent,
                            shape = RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = getString(R.string.settings_menu_pointer_distance_sample),
                        color = textColor,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp)
                    )
                }
                Image(
                    painter = painterResource(id = R.drawable.drag_pointer),
                    contentDescription = null,
                    modifier = Modifier
                        .size(34.dp)
                        .border(
                            width = if (previewVisible) 2.dp else 0.dp,
                            color = if (previewVisible) Color(0xFF48baef) else Color.Transparent,
                            shape = RoundedCornerShape(10.dp)
                        ),
                    colorFilter = ColorFilter.tint(if (previewVisible) Color(0xFF48baef) else borderColor)
                )
                Image(
                    painter = painterResource(id = if (isDarkMode) R.drawable.drag_handle_dark else R.drawable.drag_handle),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(58.dp)
                        .offset { IntOffset(-offset.x, -offset.y) }
                        .pointerInput(Unit) {
                            detectDragGestures { _, dragAmount ->
                                onOffsetChange(
                                    PointerOffset(
                                        x = latestOffset.x - dragAmount.x.roundToInt(),
                                        y = latestOffset.y - dragAmount.y.roundToInt(),
                                    )
                                )
                            }
                        }
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onPreview,
            ) {
                Text(getString(R.string.settings_menu_ai_api_test))
            }
            if (previewVisible) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = getString(R.string.settings_menu_pointer_distance_preview),
                    color = textColor,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }

    @Composable
    fun MenuCategory(
        icon: ImageVector? = null,
        painter: Painter? = null,
        iconSize: Dp = 22.dp,
        categoryName: String,
        isRtl: Boolean,
    ) {
        val isDarkMode = isSystemInDarkTheme()
        val menuCategoryColor = if (isDarkMode) Color(0xFFb7b7ba) else Color(0xFF626265)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(start = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = "$categoryName Icon",
                    modifier = Modifier
                        .size(iconSize)
                        .graphicsLayer {
                            if (isRtl) rotationY = 180f
                        },
                    tint = menuCategoryColor
                )
            }
            if (painter != null) {
                Image(
                    modifier = Modifier
                        .size(iconSize)
                        .graphicsLayer {
                            if (isRtl) rotationY = 180f
                        },
                    painter = painter,
                    contentDescription = "$categoryName Image",
                    contentScale = ContentScale.Fit,
                    colorFilter = ColorFilter.tint(menuCategoryColor)
                )
            }
            Text(
                modifier = Modifier.padding(start = 8.dp),
                text = categoryName,
                color = menuCategoryColor,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
            )
        }
    }

    @Composable
    fun MenuItem(
        menuItemPosition: MenuItemPosition,
        onClick: (() -> Unit)? = null,
        composable: @Composable () -> Unit,
    ) {
        val cornerRound = 32.dp
        val coroutineScope = rememberCoroutineScope()
        val shape = when (menuItemPosition) {
            MenuItemPosition.Single -> RoundedCornerShape(cornerRound)
            MenuItemPosition.Top -> RoundedCornerShape(topStart = cornerRound, topEnd = cornerRound)
            MenuItemPosition.Middle -> RoundedCornerShape(0.dp)
            MenuItemPosition.Bottom -> RoundedCornerShape(bottomStart = cornerRound, bottomEnd = cornerRound)
        }

        val isDarkMode = isSystemInDarkTheme()
        val backgroundColor = if (isDarkMode) Color(0xFF171717) else Color(0xFFfafafa)
        val dividerColor = if (isDarkMode) Color(0xFF343434) else Color(0xFFd5d5d5)
        val buttonColor = if (isDarkMode) Color(0xFFfafafa) else Color(0xFF171717)

        Box(
            modifier = Modifier
                .wrapContentSize()
                .background(
                    color = backgroundColor,
                    shape = shape
                )
        ) {
            if (menuItemPosition == MenuItemPosition.Middle || menuItemPosition == MenuItemPosition.Bottom) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 18.dp),
                    thickness = 0.7.dp,
                    color = dividerColor
                )
            }

            Button(
                enabled = onClick != null,
                onClick = {
                    onClick?.let {
                        coroutineScope.launch {
                            delay(200L)
                            onClick()
                        }
                    }
                },
                colors = ButtonDefaults.textButtonColors(contentColor = buttonColor),
                shape = shape,
                modifier = Modifier
                    .wrapContentSize(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp)
                        .padding(top = 5.dp, bottom = 5.dp, start = 5.dp, end = 0.dp),
                ) {
                    composable()
                }
            }
        }
    }

    @Composable
    fun MenuTextItem(
        menuItemPosition: MenuItemPosition,
        text: String,
        paddingValues: PaddingValues,
        onTextPositioned: ((Point) -> Unit)? = null,
        onGloballyPositioned: ((LayoutCoordinates) -> Unit)? = null,
        subText: String? = null,
        onSubtextPositioned: ((Point) -> Unit)? = null,
        onClick: (() -> Unit)? = null,
        menuTextModifier: Modifier = Modifier
    ) {
        MenuItem(
            menuItemPosition = menuItemPosition,
            onClick = onClick,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MenuText(
                    text = text,
                    onGloballyPositioned = onGloballyPositioned,
                    onTextPositioned = onTextPositioned,
                    modifier = menuTextModifier
                )
                subText?.let {
                    MenuSubText(
                        text = it,
                        paddingValues = paddingValues,
                        onSubtextPositioned = onSubtextPositioned
                    )
                }
            }
        }
    }

    @Composable
    fun MenuText(
        modifier: Modifier = Modifier,
        text: String,
        onGloballyPositioned: ((LayoutCoordinates) -> Unit)? = null,
        onTextPositioned: ((Point) -> Unit)? = null,
    ) {
        val isDarkMode = isSystemInDarkTheme()
        val textColor = if (isDarkMode) Color(0xFFfcfcfc) else Color(0xFF010000)
        val fontSize = fontDimensionResource(R.dimen.settings_menu_text_size)

        Text(
            text = text,
            color = textColor,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = fontSize),
            modifier = modifier.onGloballyPositioned { layoutCoordinates ->
                onGloballyPositioned?.let { it(layoutCoordinates) }
                val offset = layoutCoordinates.positionOnScreen()
                onTextPositioned?.let { it(Point(offset.x.toInt(), offset.y.toInt())) }
            },
        )
    }

    @Composable
    fun MenuSubText(
        text: String,
        paddingValues: PaddingValues,
        onSubtextPositioned: ((Point) -> Unit)? = null,
    ) {
        val context = LocalContext.current
        val isDarkMode = isSystemInDarkTheme()
        val layoutDirection = LocalLayoutDirection.current
        val subTextColor = if (isDarkMode) Color(0xFFb7b7ba) else Color(0xFF626265)
        val fontSize = fontDimensionResource(R.dimen.settings_menu_subtext_size)

        Text(
            modifier = Modifier
                .padding(end = 6.dp)
                .onGloballyPositioned { layoutCoordinates ->
                    val offset = layoutCoordinates.positionOnScreen()
                    val startPadding = paddingValues
                        .calculateLeftPadding(layoutDirection)
                        .toPx(context)
                    val posX = offset.x.toInt() + layoutCoordinates.size.width - startPadding
                    onSubtextPositioned?.let { it(Point(offset.x.toInt() + layoutCoordinates.size.width, offset.y.toInt())) }
                },
            text = text,
            color = subTextColor,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = fontSize),
        )

    }

    private fun appReview() {
        Timber.tag(TAG).d("appReview()")
        val manager =
            if (BuildConfig.DEBUG) {
                FakeReviewManager(applicationContext)
            } else {
                ReviewManagerFactory.create(applicationContext)
            }

        lifecycleScope.launch {
            val usageCount = viewModel.secureRepository.getTranslationUsageCount()
            Timber.tag(TAG).d("appReview() usageCount $usageCount")
            val isReviewDone = viewModel.preferenceRepository.isReviewDoneFlow.first()
            Timber.tag(TAG).d("appReview() isReviewDone $isReviewDone")
            if (usageCount > 30 && !isReviewDone) {
                while (true) {
                    delay(3000L)
                    if (
                        !LanguageListView.INSTANCE.isRunning.get()
                        && !HelpTextDetectModeView.INSTANCE.isRunning.get()
                        && !HelpTranslationKitView.INSTANCE.isRunning.get()
                        && !SliderDialogView.INSTANCE.isRunning.get()
                        && !VoiceListView.INSTANCE.isRunning.get()
                    ) {
                        Timber.tag(TAG).d("All states are false. Proceeding with review flow.")
                        startReviewFlow(manager)
                        break
                    }
                }
            }
        }
    }

    private fun startReviewFlow(manager: ReviewManager) {
        MenuBarView.INSTANCE.clear()
        TargetHandleView.clearAll()

        val request = manager.requestReviewFlow()
//        Timber.tag(TAG).d("appReview() startReviewFlow request $request")
        request.addOnCompleteListener { task ->
//            Timber.tag(TAG).d("appReview() startReviewFlow task ${task.isSuccessful}")
            if (task.isSuccessful) {
                val reviewInfo = task.result
                val flow = manager.launchReviewFlow(this, reviewInfo)
                flow.addOnCompleteListener { _ ->
                    viewModel.updateIsReviewDone()
                }
            } else {
                val reviewErrorCode = (task.exception as ReviewException).errorCode
                Timber.tag(TAG).d("appReview() startReviewFlow reviewErrorCode $reviewErrorCode")
            }
            lifecycleScope.launch {
                syncMenuBarWindow()
                TargetHandleView.castConfigured(
                    applicationContext = applicationContext,
                    dualPointerMode = viewModel.preferenceRepository.dualPointerEnabledFlow.first()
                )
            }
        }
    }
}
