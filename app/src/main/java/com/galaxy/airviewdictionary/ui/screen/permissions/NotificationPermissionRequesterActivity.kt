package com.galaxy.airviewdictionary.ui.screen.permissions

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.galaxy.airviewdictionary.NOTIFICATION_CHANNEL_ID
import com.galaxy.airviewdictionary.ui.screen.AVDActivity


/**
 * Notification 권한을 요청하는 Activity.
 */
class NotificationPermissionRequesterActivity : AVDActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val screenCaptureLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val resultIntent = Intent()
            if (result.resultCode == Activity.RESULT_OK) {
                setResult(Activity.RESULT_OK, resultIntent)
            } else {
                setResult(Activity.RESULT_CANCELED, resultIntent)
            }
            finish()
        }

        val notificationIntent: Intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, NOTIFICATION_CHANNEL_ID)
        screenCaptureLauncher.launch(notificationIntent)
    }
}
