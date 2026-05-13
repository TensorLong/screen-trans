package com.galaxy.airviewdictionary.ui.screen.onboarding

import androidx.lifecycle.ViewModel
import com.galaxy.airviewdictionary.data.local.preference.PreferenceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class OnBoardingViewModel @Inject constructor(
    val preferenceRepository: PreferenceRepository,
) : ViewModel() {

    private val TAG = javaClass.simpleName

    init {
        Timber.tag(TAG).i("#### init ####")
    }

    override fun onCleared() {
        super.onCleared()
    }
}


