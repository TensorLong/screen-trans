package com.galaxy.airviewdictionary.ui.screen.intro

import androidx.lifecycle.ViewModel
import com.galaxy.airviewdictionary.data.local.capture.CaptureRepository
import com.galaxy.airviewdictionary.data.local.preference.PreferenceRepository
import com.galaxy.airviewdictionary.data.local.secure.SecureRepository
import com.galaxy.airviewdictionary.data.local.capture.CaptureResponse
import com.galaxy.airviewdictionary.ui.screen.permissions.PermissionStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val secureRepository: SecureRepository,
    val preferenceRepository: PreferenceRepository,
    private val captureRepository: CaptureRepository,
) : ViewModel() {

    private val TAG = javaClass.simpleName

    suspend fun getMediaProjectionState(): PermissionStatus {
        try {
            val captureResponse: CaptureResponse = captureRepository.request()
            Timber.tag(TAG).i("getMediaProjectionState captureResponse $captureResponse")
            if (captureResponse is CaptureResponse.Success) {
                return PermissionStatus.Granted
            }
        } catch (e: Exception) {
        }
        return PermissionStatus.Prepared
    }

    init {
        Timber.tag(TAG).i("#### init ####")
        secureRepository.acquire()
        captureRepository.acquire()
    }

    override fun onCleared() {
        secureRepository.release()
        captureRepository.release()
        super.onCleared()
    }
}


