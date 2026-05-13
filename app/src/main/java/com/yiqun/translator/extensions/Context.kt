package com.yiqun.translator.extensions

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.edit
import androidx.core.net.toUri
import com.yiqun.translator.ACTION_SERVICE_CONTROL
import com.yiqun.translator.EXTRA_SERVICE_STOP
import com.yiqun.translator.core.OverlayService
import timber.log.Timber
import java.util.UUID


/**
 * UUID
 */
fun Context.getOrCreateAppInstanceId(): String {
    val prefs = getSharedPreferences("app_instance_prefs", Context.MODE_PRIVATE)
    val key = "app_instance_id"
    var id = prefs.getString(key, null)
    if (id == null) {
        id = UUID.randomUUID().toString()
        prefs.edit { putString(key, id) }
    }
    return id
}

/**
 */
fun Context.isNetworkAvailable(): Boolean {
    val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = connectivityManager.activeNetwork ?: return false
    val networkCapabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    return networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
}

/**
 */
fun Context.vibrate(durationMillis: Long = 10) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(VibrationEffect.createOneShot(durationMillis, VibrationEffect.DEFAULT_AMPLITUDE))
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(durationMillis)
    }
}

/**
 */
fun Context.gotoStore(
    newTask: Boolean = true,
    finishService: Boolean = false
) {
    val playStoreIntent = Intent(Intent.ACTION_VIEW, "market://details?id=$packageName".toUri()).apply {
        setPackage("com.android.vending")
    }

    val webIntent = Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=$packageName".toUri())

    try {
        if (newTask) playStoreIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(playStoreIntent)
    } catch (e: ActivityNotFoundException) {
        if (newTask) webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(webIntent)
    }

    if (finishService) finishService()
}

/**
 */
fun Context.openGoogleApp() {
    try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = "https://www.google.com".toUri()
            setPackage("com.google.android.googlequicksearchbox")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        startActivity(intent)
    } catch (e: Exception) {
        val playStoreIntent = Intent(Intent.ACTION_VIEW).apply {
            data = "market://details?id=com.google.android.googlequicksearchbox".toUri()
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(playStoreIntent)
    }
}

fun Context.playStoreUpdate() {
    try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = "market://details?id=com.android.vending".toUri()
            setPackage("com.android.vending")
        }
        startActivity(intent)
    } catch (e: Exception) {
        Timber.tag("Context").e("Failed to redirect to Play Store update: ${e.message}")
    }
}

/**
 */
fun Context.finishService() = Intent().also { intent ->
    intent.setClass(this, OverlayService::class.java)
    intent.putExtra(ACTION_SERVICE_CONTROL, EXTRA_SERVICE_STOP)
    startService(intent)
}