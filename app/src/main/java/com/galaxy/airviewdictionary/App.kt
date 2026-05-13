package com.galaxy.airviewdictionary

import android.app.Application
import com.google.android.gms.ads.MobileAds
import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.initialize
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltAndroidApp
class App : Application() {
    override fun onCreate() {
        super.onCreate()

        // timber
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        if (!BuildConfig.DEBUG) {
            // firebase
            Firebase.initialize(context = this)

            // firebase app check
            Firebase.appCheck.installAppCheckProviderFactory(
                PlayIntegrityAppCheckProviderFactory.getInstance(),
            )

            /**
             * Google 애널리틱스에서 동의 설정 확인 및 업데이트하기
             * https://support.google.com/analytics/answer/14275483?hl=ko&utm_id=ad
             */
            val consentMap = mapOf(
                FirebaseAnalytics.ConsentType.ANALYTICS_STORAGE to FirebaseAnalytics.ConsentStatus.GRANTED,
                FirebaseAnalytics.ConsentType.AD_STORAGE to FirebaseAnalytics.ConsentStatus.GRANTED,
                FirebaseAnalytics.ConsentType.AD_PERSONALIZATION to FirebaseAnalytics.ConsentStatus.GRANTED,
                FirebaseAnalytics.ConsentType.AD_USER_DATA to FirebaseAnalytics.ConsentStatus.GRANTED
            )
            Firebase.analytics.setConsent(consentMap)
        }

        CoroutineScope(Dispatchers.IO).launch {
            // Initialize the Google Mobile Ads SDK on a background thread.
            MobileAds.initialize(applicationContext) {}
        }
    }
}
