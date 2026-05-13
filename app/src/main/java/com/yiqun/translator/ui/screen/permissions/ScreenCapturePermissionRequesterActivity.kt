package com.yiqun.translator.ui.screen.permissions

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.yiqun.translator.data.local.capture.CaptureRepository
import com.yiqun.translator.ui.screen.AVDActivity
import timber.log.Timber


/**
 * @see CaptureRepository
 */
class ScreenCapturePermissionRequesterActivity : AVDActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val mediaProjectionManager: MediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val screenCaptureLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            Timber.tag(TAG).d("ActivityResultContracts MediaProjection resultCode ${result.resultCode} token ${result.data} ")
            val resultIntent = Intent()
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                CaptureRepository.mediaProjectionToken = result.data!!.clone() as Intent
                setResult(Activity.RESULT_OK, resultIntent)
            } else {
                setResult(Activity.RESULT_CANCELED, resultIntent)
            }
            finish()
        }

        val captureIntent = mediaProjectionManager.createScreenCaptureIntent()
        screenCaptureLauncher.launch(captureIntent)
    }
}
