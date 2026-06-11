package com.yiqun.translator.ui.screen.intro

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.ScreenshotMonitor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.lifecycleScope
import com.yiqun.translator.R
import com.yiqun.translator.data.local.capture.CaptureRepository
import com.yiqun.translator.data.local.preference.PreferenceRepository
import com.yiqun.translator.ui.screen.AVDActivity
import com.yiqun.translator.ui.common.MyDialog
import com.yiqun.translator.ui.screen.main.SettingsActivity
import com.yiqun.translator.ui.screen.overlay.menubar.MenuBarView
import com.yiqun.translator.ui.screen.overlay.targethandle.TargetHandleView
import com.yiqun.translator.ui.screen.permissions.NotificationPermissionRequesterActivity
import com.yiqun.translator.ui.screen.permissions.PermissionStatus
import com.yiqun.translator.ui.screen.permissions.ScreenCapturePermissionRequesterActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber


@SuppressLint("CustomSplashScreen")
@AndroidEntryPoint
class SplashActivity : AVDActivity() {

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, SplashActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    private val viewModel: SplashViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        MenuBarView.INSTANCE.clear()

        lifecycleScope.launch {
            val wasTrailerShown = viewModel.preferenceRepository.wasTrailerShownFlow.first()
            Timber.tag(TAG).d("wasTrailerShown $wasTrailerShown")
            if (!wasTrailerShown) {
                // Show the welcome page on top and stay alive underneath.
                // Finishing here and letting the welcome page relaunch this
                // singleTop activity raced the dying instance: the launch
                // intent was delivered to the finishing record (START result
                // DELIVERED_TO_TOP) and dropped, stranding the user on the
                // home screen. Waiting on the DataStore flow also removes the
                // race against the asynchronous preference write.
                WelcomeActivity.start(applicationContext)
                viewModel.preferenceRepository.wasTrailerShownFlow.first { it }
            }

            val layoutParams = window.attributes
            layoutParams.dimAmount = 0.90f
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.attributes = layoutParams

            setContent {
                DelayedFadeInContent()
            }
        }
    }


    override fun onResume() {
        super.onResume()
        Timber.tag(TAG).i("#### onResume ####")
    }

    @Composable
    fun DelayedFadeInContent() {
        val isVisible = remember { mutableStateOf(false) }

        LaunchedEffect(key1 = true) {
            delay(300)
            Timber.tag(TAG).i("--------- Visibility set to true ----------")
            isVisible.value = true
        }

        AnimatedVisibility(
            visible = isVisible.value,
            enter = fadeIn(
                animationSpec = tween(durationMillis = 900)
            )
        ) {
            Splash()
        }
    }

    @SuppressLint("BatteryLife")
    @Composable
    fun Splash(viewModel: SplashViewModel = hiltViewModel()) {
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()

        val dialogAnimDuration = 200

        val isTargetHandleRunning = TargetHandleView.INSTANCE.isRunning.get()

        val (overlayPermissionState, setOverlayPermissionState) = remember {
            mutableStateOf(
                if (Settings.canDrawOverlays(context)) PermissionStatus.Granted else PermissionStatus.Prepared
            )
        }

        // NotificationManagerCompat: NotificationManager.areNotificationsEnabled() needs
        // API 24 and would crash the unguarded call sites below on minSdk 23 devices.
        val notificationManager = NotificationManagerCompat.from(context)

        val (notificationPermissionState_0, setNotificationPermissionState_0) = remember {
            mutableStateOf(
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || notificationManager.areNotificationsEnabled())
                    PermissionStatus.Granted
                else
                    PermissionStatus.Prepared
            )
        }

        val (notificationPermissionState_1, setNotificationPermissionState_1) = remember {
            mutableStateOf(
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || notificationManager.areNotificationsEnabled())
                    PermissionStatus.Granted
                else
                    PermissionStatus.Prepared
            )
        }

        val (mediaProjectionState, setMediaProjectionState) = remember {
            mutableStateOf(
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                    || CaptureRepository.mediaProjectionToken != null
                    || isTargetHandleRunning
                )
                    PermissionStatus.Granted
                else
                    PermissionStatus.Prepared
            )
        }

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        // Optional step: starts as Requested (neutral, no dialog) until the
        // "already offered once?" preference is read; Granted here means
        // "resolved", not necessarily exempted — the step never blocks startup.
        val (batteryExemptionState, setBatteryExemptionState) = remember {
            mutableStateOf(
                if (powerManager.isIgnoringBatteryOptimizations(context.packageName))
                    PermissionStatus.Granted
                else
                    PermissionStatus.Requested
            )
        }
        LaunchedEffect(Unit) {
            if (batteryExemptionState == PermissionStatus.Requested) {
                val alreadyOffered =
                    viewModel.preferenceRepository.wasBatteryExemptionRequestedFlow.first()
                setBatteryExemptionState(
                    if (alreadyOffered) PermissionStatus.Granted else PermissionStatus.Prepared
                )
            }
        }


        ////////////////////////////////////////////////////////////////////////////////////////////////
        //                                                                                            //
        //                                         Notification                                       //
        //                                                                                            //
        ////////////////////////////////////////////////////////////////////////////////////////////////

        val notificationPermissionLauncher_0 = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            setNotificationPermissionState_0(if (isGranted) PermissionStatus.Granted else PermissionStatus.Denied)
        }

        LaunchedEffect(notificationPermissionState_0) {
            if (notificationPermissionState_0 == PermissionStatus.Ready) {
                delay(400)
                notificationPermissionLauncher_0.launch(android.Manifest.permission.POST_NOTIFICATIONS).also {
                    setNotificationPermissionState_0(PermissionStatus.Requested)
                }
            }
        }

        val notificationPermissionLauncher_1 = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            Timber.tag(TAG).d("notificationPermissionLauncher_1 resultCode ${result.resultCode}  ${notificationManager.areNotificationsEnabled()}")
            val isGranted = notificationManager.areNotificationsEnabled()
            setNotificationPermissionState_1(if (isGranted) PermissionStatus.Granted else PermissionStatus.Denied)
        }

        LaunchedEffect(notificationPermissionState_1) {
            if (notificationPermissionState_1 == PermissionStatus.Ready) {
                delay(400)
                notificationPermissionLauncher_1.launch(Intent(context, NotificationPermissionRequesterActivity::class.java))
                    .also { setNotificationPermissionState_1(PermissionStatus.Requested) }
            }
        }

        AnimatedVisibility(
            visible = notificationPermissionState_0 == PermissionStatus.Prepared
                    || notificationPermissionState_0 == PermissionStatus.Denied
                    || notificationPermissionState_1 == PermissionStatus.Denied,
            enter = fadeIn(animationSpec = tween(dialogAnimDuration)) + scaleIn(animationSpec = tween(dialogAnimDuration)),
            exit = fadeOut(animationSpec = tween(dialogAnimDuration)) + scaleOut(animationSpec = tween(dialogAnimDuration)),
            content = {
                MyDialog(
                    icon = Icons.Default.NotificationsActive,
                    dialogTitle = stringResource(id = R.string.message_permission_notification),
                    dialogText = stringResource(id = R.string.message_permission_notification_detail),
                    onConfirmLabel = stringResource(id = android.R.string.ok),
                    onConfirm = {
                        if (notificationPermissionState_0 == PermissionStatus.Prepared) {
                            setNotificationPermissionState_0(PermissionStatus.Ready)
                        } else {
                            setNotificationPermissionState_0(PermissionStatus.Canceled)
                            setNotificationPermissionState_1(PermissionStatus.Ready)
                        }
                    },
                    onDismissLabel = stringResource(id = android.R.string.cancel),
                    onDismiss = {
                        finish()
                    },
                )
            }
        )


        ////////////////////////////////////////////////////////////////////////////////////////////////
        //                                                                                            //
        //                                            Overlay                                         //
        //                                                                                            //
        ////////////////////////////////////////////////////////////////////////////////////////////////

        AnimatedVisibility(
            visible = (notificationPermissionState_0 == PermissionStatus.Granted || notificationPermissionState_1 == PermissionStatus.Granted)
                    && (overlayPermissionState == PermissionStatus.Prepared || overlayPermissionState == PermissionStatus.Denied),
            enter = fadeIn(animationSpec = tween(dialogAnimDuration)) + scaleIn(animationSpec = tween(dialogAnimDuration)),
            exit = fadeOut(animationSpec = tween(dialogAnimDuration)) + scaleOut(animationSpec = tween(dialogAnimDuration)),
            content = {
                MyDialog(
                    painterResource = R.drawable.ic_overlay,
                    dialogTitle = stringResource(id = R.string.message_permission_overlay),
                    dialogText = stringResource(id = R.string.message_permission_overlay_detail),
                    onConfirmLabel = stringResource(id = android.R.string.ok),
                    onConfirm = { setOverlayPermissionState(PermissionStatus.Ready) },
                    onDismissLabel = stringResource(id = android.R.string.cancel),
                    onDismiss = {
                        finish()
                    },
                )
            }
        )

        val overlayPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            Timber.tag(TAG).d("overlayPermissionLauncher resultCode ${result.resultCode} token ${result.data} ${Settings.canDrawOverlays(context)}")
            setOverlayPermissionState(if (Settings.canDrawOverlays(context)) PermissionStatus.Granted else PermissionStatus.Denied)
        }

        LaunchedEffect(notificationPermissionState_0, notificationPermissionState_1, overlayPermissionState) {
            if ((notificationPermissionState_0 == PermissionStatus.Granted || notificationPermissionState_1 == PermissionStatus.Granted)
                && overlayPermissionState == PermissionStatus.Ready
            ) {
                delay(400)
                overlayPermissionLauncher.launch(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                ).also { setOverlayPermissionState(PermissionStatus.Requested) }
            }
        }


        ////////////////////////////////////////////////////////////////////////////////////////////////
        //                                                                                            //
        //                                        MediaProjection                                     //
        //                                                                                            //
        ////////////////////////////////////////////////////////////////////////////////////////////////

        val mediaProjectionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            Timber.tag(TAG).d("mediaProjectionLauncher resultCode ${result.resultCode} token ${result.data} ")
            val isGranted = result.resultCode == Activity.RESULT_OK && result.data != null
            setMediaProjectionState(if (isGranted) PermissionStatus.Granted else PermissionStatus.Denied)
        }

        AnimatedVisibility(
            visible = (notificationPermissionState_0 == PermissionStatus.Granted || notificationPermissionState_1 == PermissionStatus.Granted)
                    && overlayPermissionState == PermissionStatus.Granted
                    && (mediaProjectionState == PermissionStatus.Prepared || mediaProjectionState == PermissionStatus.Denied),
            enter = fadeIn(animationSpec = tween(dialogAnimDuration)) + scaleIn(animationSpec = tween(dialogAnimDuration)),
            exit = fadeOut(animationSpec = tween(dialogAnimDuration)) + scaleOut(animationSpec = tween(dialogAnimDuration)),
            content = {
                MyDialog(
                    icon = Icons.Default.ScreenshotMonitor,
                    dialogTitle = stringResource(id = R.string.message_media_projection),
                    dialogText = stringResource(id = R.string.message_media_projection_detail),
                    onConfirmLabel = stringResource(id = android.R.string.ok),
                    onConfirm = { setMediaProjectionState(PermissionStatus.Ready) },
                    onDismissLabel = stringResource(id = android.R.string.cancel),
                    onDismiss = {
                        finish()
                    },
                )
            }
        )

        LaunchedEffect(notificationPermissionState_0, notificationPermissionState_1, overlayPermissionState, mediaProjectionState) {
            if (
                (notificationPermissionState_0 == PermissionStatus.Granted || notificationPermissionState_1 == PermissionStatus.Granted)
                && overlayPermissionState == PermissionStatus.Granted
                && mediaProjectionState == PermissionStatus.Ready
            ) {
                delay(300)
                mediaProjectionLauncher.launch(Intent(context, ScreenCapturePermissionRequesterActivity::class.java))
                    .also { setMediaProjectionState(PermissionStatus.Requested) }
            }
        }


        ////////////////////////////////////////////////////////////////////////////////////////////////
        //                                                                                            //
        //                                Battery optimization exemption                              //
        //                                                                                            //
        ////////////////////////////////////////////////////////////////////////////////////////////////

        val batteryExemptionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // Optional: whatever the user chose, the step is resolved and must
            // not block startup.
            setBatteryExemptionState(PermissionStatus.Granted)
        }

        AnimatedVisibility(
            visible = (notificationPermissionState_0 == PermissionStatus.Granted || notificationPermissionState_1 == PermissionStatus.Granted)
                    && overlayPermissionState == PermissionStatus.Granted
                    && mediaProjectionState == PermissionStatus.Granted
                    && batteryExemptionState == PermissionStatus.Prepared,
            enter = fadeIn(animationSpec = tween(dialogAnimDuration)) + scaleIn(animationSpec = tween(dialogAnimDuration)),
            exit = fadeOut(animationSpec = tween(dialogAnimDuration)) + scaleOut(animationSpec = tween(dialogAnimDuration)),
            content = {
                MyDialog(
                    icon = Icons.Default.BatteryChargingFull,
                    dialogTitle = stringResource(id = R.string.message_permission_battery),
                    dialogText = stringResource(id = R.string.message_permission_battery_detail),
                    onConfirmLabel = stringResource(id = android.R.string.ok),
                    onConfirm = { setBatteryExemptionState(PermissionStatus.Ready) },
                    onDismissLabel = stringResource(id = android.R.string.cancel),
                    onDismiss = {
                        // Skippable: remember the offer and move on.
                        viewModel.preferenceRepository.update(
                            PreferenceRepository.WAS_BATTERY_EXEMPTION_REQUESTED, true
                        )
                        setBatteryExemptionState(PermissionStatus.Granted)
                    },
                )
            }
        )

        LaunchedEffect(batteryExemptionState) {
            if (batteryExemptionState == PermissionStatus.Ready) {
                delay(300)
                viewModel.preferenceRepository.update(
                    PreferenceRepository.WAS_BATTERY_EXEMPTION_REQUESTED, true
                )
                batteryExemptionLauncher.launch(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                )
            }
        }


        ////////////////////////////////////////////////////////////////////////////////////////////////
        //                                                                                            //
        //                                             Start                                          //
        //                                                                                            //
        ////////////////////////////////////////////////////////////////////////////////////////////////

        Column(modifier = Modifier.fillMaxSize()) {

//            Column(modifier = Modifier.wrapContentSize()) {
//                Text(text = "notificationPermissionState_0: $notificationPermissionState_0", color = Color.White)
//                Text(text = "notificationPermissionState_1: $notificationPermissionState_1", color = Color.White)
//                Text(text = "overlayPermissionState: $overlayPermissionState ${Settings.canDrawOverlays(context)}", color = Color.White)
//                Text(text = "mediaProjectionState: $mediaProjectionState", color = Color.White)
//            }

//            val permitRequiredCount = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE || isTargetHandleRunning) 2 else 3
            val permitRequiredCount = if (CaptureRepository.mediaProjectionToken != null) 2 else 3
            var permitRequestIndex = 1
            if (notificationPermissionState_0 == PermissionStatus.Granted || notificationPermissionState_1 == PermissionStatus.Granted) permitRequestIndex++
            if (overlayPermissionState == PermissionStatus.Granted) permitRequestIndex++

            val permitCountVisible = permitRequestIndex <= permitRequiredCount

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, start = 0.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = R.mipmap.ic_launcher),
                    contentDescription = "launcher icon",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(20.dp)
                )

                Text(
                    text = stringResource(id = R.string.app_name),
                    fontSize = 18.sp,
                    color = Color.White,
                    modifier = Modifier.padding(start = 8.dp)
                )
                if (permitCountVisible) {
                    Text(
                        text = "Permissions $permitRequestIndex of $permitRequiredCount",
                        fontSize = 15.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }
            }
        }

        LaunchedEffect(notificationPermissionState_0, notificationPermissionState_1, overlayPermissionState, mediaProjectionState, batteryExemptionState) {
            if (
                isTargetHandleRunning
                && (notificationPermissionState_0 == PermissionStatus.Granted || notificationPermissionState_1 == PermissionStatus.Granted)
                && overlayPermissionState == PermissionStatus.Granted
                && mediaProjectionState == PermissionStatus.Granted
                && batteryExemptionState == PermissionStatus.Granted
            ) {
                delay(200)
                SettingsActivity.start(context)
                finish()
            }
        }

        AnimatedVisibility(
            visible = !isTargetHandleRunning
                    && (notificationPermissionState_0 == PermissionStatus.Granted || notificationPermissionState_1 == PermissionStatus.Granted)
                    && overlayPermissionState == PermissionStatus.Granted
                    && mediaProjectionState == PermissionStatus.Granted
                    && batteryExemptionState == PermissionStatus.Granted,
            enter = fadeIn(animationSpec = tween(dialogAnimDuration)) + scaleIn(animationSpec = tween(dialogAnimDuration)),
            exit = fadeOut(animationSpec = tween(dialogAnimDuration)) + scaleOut(animationSpec = tween(dialogAnimDuration)),
            content = {
                MyDialog(
                    painterResource = R.drawable.img_launch,
                    dialogTitle = stringResource(id = R.string.title_start_foreground_service),
                    dialogText = stringResource(id = R.string.message_start_foreground_service),
                    onConfirmLabel = stringResource(id = R.string.label_confirm_start_foreground_service),
                    onConfirm = {
                        coroutineScope.launch {
                            delay(200)
                            TargetHandleView.castConfigured(
                                applicationContext = applicationContext,
                                dualPointerMode = viewModel.preferenceRepository.dualPointerEnabledFlow.first(),
                            )
                            // First run only: land on the practice playground so
                            // the user performs the real gesture on safe sample
                            // text while the handle is freshly on screen.
                            if (!viewModel.preferenceRepository.wasPracticeShownFlow.first()) {
                                PracticeActivity.start(applicationContext)
                            }
                            finish()
                        }
                    },
                    onDismissLabel = stringResource(id = R.string.label_dismiss_start_foreground_service),
                    onDismiss = {
                        finish()
                    },
                )
            }
        )
    }
}

