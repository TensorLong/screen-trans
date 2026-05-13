package com.galaxy.airviewdictionary.ui.screen

import android.os.Bundle
import androidx.activity.ComponentActivity
import timber.log.Timber


open class AVDActivity : ComponentActivity() {

    protected open val TAG = javaClass.simpleName

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.tag(TAG).i("#### onCreate ####")
    }

    override fun onResume() {
        super.onResume()
        Timber.tag(TAG).i("#### onResume ####")
    }

    override fun onPause() {
        super.onPause()
        Timber.tag(TAG).i("#### onPause ####")
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.tag(TAG).i("#### onDestroy ####")
    }
}
