package com.yiqun.translator.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

@Composable
fun AutoRefreshEveryMinute(content: @Composable () -> Unit) {
    var refreshKey by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            refreshKey++
        }
    }

    key(refreshKey) {
        content()
    }
}
