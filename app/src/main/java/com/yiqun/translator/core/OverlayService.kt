package com.yiqun.translator.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import com.yiqun.translator.ACTION_SERVICE_CONTROL
import com.yiqun.translator.EXTRA_SERVICE_STOP
import com.yiqun.translator.FOREGROUND_SERVICE_NOTIFICATION_ID
import com.yiqun.translator.NOTIFICATION_CHANNEL_ID
import com.yiqun.translator.NOTIFICATION_CHANNEL_NAME
import com.yiqun.translator.R
import com.yiqun.translator.REQUEST_CODE_SERVICE_STOP
import com.yiqun.translator.data.local.capture.CaptureRepository
import com.yiqun.translator.data.local.preference.PreferenceRepository
import com.yiqun.translator.data.local.screen.ScreenInfoHolder
import com.yiqun.translator.data.local.secure.SecureRepository
import com.yiqun.translator.data.local.tts.TTSRepository
import com.yiqun.translator.data.local.vision.VisionRepository
import com.yiqun.translator.data.remote.ai.SenseGroupRepository
import com.yiqun.translator.data.remote.firebase.AnalyticsRepository
import com.yiqun.translator.data.remote.firebase.RemoteConfigRepository
import com.yiqun.translator.data.remote.translation.TranslationRepository
import com.yiqun.translator.ui.screen.intro.SplashActivity
import com.yiqun.translator.ui.screen.main.SettingsActivity
import com.yiqun.translator.ui.screen.overlay.Event
import com.yiqun.translator.ui.screen.overlay.languagelist.LanguageListViewModel
import com.yiqun.translator.ui.screen.overlay.languagelist.LanguageListViewModelFactory
import com.yiqun.translator.ui.screen.overlay.menubar.MenuBarViewModel
import com.yiqun.translator.ui.screen.overlay.menubar.MenuBarViewModelFactory
import com.yiqun.translator.ui.screen.overlay.settings.SliderDialogViewModel
import com.yiqun.translator.ui.screen.overlay.settings.SliderDialogViewModelFactory
import com.yiqun.translator.ui.screen.overlay.targethandle.TargetHandleView
import com.yiqun.translator.ui.screen.overlay.targethandle.TargetHandleViewModel
import com.yiqun.translator.ui.screen.overlay.targethandle.TargetHandleViewModelFactory
import com.yiqun.translator.ui.screen.overlay.voicelist.VoiceListViewModel
import com.yiqun.translator.ui.screen.overlay.voicelist.VoiceListViewModelFactory
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject


/**
 */
@AndroidEntryPoint
class OverlayService : LifecycleService(), SavedStateRegistryOwner, ViewModelStoreOwner {

    private val TAG = javaClass.simpleName

    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): OverlayService = this@OverlayService
    }

    override val viewModelStore: ViewModelStore = ViewModelStore()

    override fun onCreate() {
        super.onCreate()
        Timber.tag(TAG).d("#################### OverlayService onCreate ####################")

        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Timber.tag(TAG).i("#### onStartCommand() ####")

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    FOREGROUND_SERVICE_NOTIFICATION_ID,
                    buildNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
                )
            } else {
                startForeground(
                    FOREGROUND_SERVICE_NOTIFICATION_ID,
                    buildNotification(),
                )
            }
        } catch (e: SecurityException) {
            // Without foreground status this service must not keep running (or be
            // restarted by START_STICKY) — send the user back through the permission
            // flow and stop.
            val splashIntent = Intent(applicationContext, SplashActivity::class.java)
            splashIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(splashIntent)
            stopSelf()
            return START_NOT_STICKY
        }

        intent?.let {
            when (intent.getStringExtra(ACTION_SERVICE_CONTROL)) {
                EXTRA_SERVICE_STOP -> {
                    Timber.tag(TAG).d("EXTRA_SERVICE_STOP")
                    broadcastEvent(Event.Unbind)
                    viewModelStore.clear()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    } else {
                        @Suppress("DEPRECATION")
                        stopForeground(true)
                    }
                    stopSelf()
                }

                else -> {}
            }
        }

        /**
         */
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        Timber.tag(TAG).i("#### onBind() ####")
        return binder
    }

    override fun onUnbind(intent: Intent): Boolean {
        Timber.tag(TAG).i("#### onUnbind() ####")
        broadcastEvent(Event.Unbind)
        return true // allowRebind
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Timber.tag(TAG).i("#### onConfigurationChanged() ####")
        ScreenInfoHolder.updateScreenInfoInService(this)
        broadcastEvent(Event.ConfigurationChanged)
    }

    override fun onDestroy() {
        Timber.tag(TAG).i("#### onDestroy() ####")
        viewModelStore.clear()
        super.onDestroy()
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    //                                                                                            //
    //                                   Event, Event Listeners                                   //
    //                                                                                            //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private var overlayServiceEventListeners = mutableListOf<OverlayServiceEventListener>()

    fun registerListener(listener: OverlayServiceEventListener) {
        unregisterListener(listener)
        overlayServiceEventListeners.add(listener)
    }

    fun broadcastEvent(event: Event) {
        val listenersSnapshot = ArrayList(overlayServiceEventListeners)
        listenersSnapshot.forEach { listener ->
            listener.onOverlayServiceEvent(this@OverlayService, event)
        }
    }

    fun unregisterListener(listener: OverlayServiceEventListener) {
        val tempList = ArrayList(overlayServiceEventListeners)
        tempList.remove(listener)
        overlayServiceEventListeners = tempList
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    //                                                                                            //
    //                                          Repository                                        //
    //                                                                                            //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    lateinit var secureRepository: SecureRepository

    @Inject
    lateinit var remoteConfigRepository: RemoteConfigRepository

//    @Inject
//    lateinit var geoLocaleRepository: GeoLocaleRepository

    @Inject
    lateinit var preferenceRepository: PreferenceRepository

    @Inject
    lateinit var captureRepository: CaptureRepository

    @Inject
    lateinit var visionRepository: VisionRepository

    @Inject
    lateinit var senseGroupRepository: SenseGroupRepository

    @Inject
    lateinit var translationRepository: TranslationRepository

    @Inject
    lateinit var ttsRepository: TTSRepository

    @Inject
    lateinit var analyticsRepository: AnalyticsRepository

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //                                                                                            //
    //                               TargetHandleViewModel Provider                               //
    //                                                                                            //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    fun getTargetHandleViewModel(): TargetHandleViewModel {
        val viewModelFactory = TargetHandleViewModelFactory(
            applicationContext = applicationContext,
            secureRepository = secureRepository,
            remoteConfigRepository = remoteConfigRepository,
            preferenceRepository = preferenceRepository,
            captureRepository = captureRepository,
            visionRepository = visionRepository.apply { addObserver(lifecycle) },
            senseGroupRepository = senseGroupRepository,
            translationRepository = translationRepository,
            ttsRepository = ttsRepository,
            analyticsRepository = analyticsRepository,
        )
        return ViewModelProvider(this, viewModelFactory)[TargetHandleViewModel::class.java]
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    //                                                                                            //
    //                                  MenuBarViewModel Provider                                 //
    //                                                                                            //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    fun getMenuBarViewModel(): MenuBarViewModel {
        val viewModelFactory = MenuBarViewModelFactory(
            applicationContext = applicationContext,
            preferenceRepository = preferenceRepository,
            translationRepository = translationRepository,
        )
        return ViewModelProvider(this, viewModelFactory)[MenuBarViewModel::class.java]
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    //                                                                                            //
    //                                LanguageListViewModel Provider                              //
    //                                                                                            //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    fun getLanguageListViewModel(): LanguageListViewModel {
        val viewModelFactory = LanguageListViewModelFactory(
            applicationContext = applicationContext,
            preferenceRepository = preferenceRepository,
            translationRepository = translationRepository,
        )
        return ViewModelProvider(this, viewModelFactory)[LanguageListViewModel::class.java]
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    //                                                                                            //
    //                                SliderDialogViewModel Provider                              //
    //                                                                                            //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    fun getSliderDialogViewModel(): SliderDialogViewModel {
        val viewModelFactory = SliderDialogViewModelFactory(
            preferenceRepository = preferenceRepository,
            translationRepository = translationRepository,
            ttsRepository = ttsRepository,
        )
        return ViewModelProvider(this, viewModelFactory)[SliderDialogViewModel::class.java]
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    //                                                                                            //
    //                                  VoiceListViewModel Provider                               //
    //                                                                                            //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    fun getVoiceListViewModel(): VoiceListViewModel {
        val viewModelFactory = VoiceListViewModelFactory(
            applicationContext = applicationContext,
            preferenceRepository = preferenceRepository,
            translationRepository = translationRepository,
            ttsRepository = ttsRepository,
        )
        return ViewModelProvider(this, viewModelFactory)[VoiceListViewModel::class.java]
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //                                                                                            //
    //                                         Notification                                       //
    //                                                                                            //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).apply {
                createNotificationChannel(
                    NotificationChannel(
                        NOTIFICATION_CHANNEL_ID,
                        NOTIFICATION_CHANNEL_NAME,
                        NotificationManager.IMPORTANCE_DEFAULT
                    )
                )
            }
        }

        val settingsPendingIntent: PendingIntent =
            Intent(this, SettingsActivity::class.java).let { notificationIntent ->
                PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
            }

        val exitPendingIntent = PendingIntent.getService(
            applicationContext,
            REQUEST_CODE_SERVICE_STOP,
            Intent(applicationContext, OverlayService::class.java).apply {
                putExtra(ACTION_SERVICE_CONTROL, EXTRA_SERVICE_STOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentText(application.resources.getString(R.string.notification_foreground_service))
            .setSmallIcon(R.drawable.outline_translate_white_24)
            .setContentIntent(settingsPendingIntent)
            .addAction(
                0,
                application.resources.getString(R.string.service_menu_finish),
                exitPendingIntent
            )
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
}


