package com.galaxy.airviewdictionary.ui.common

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
    // 현재 시간을 remember하여, 1분마다 값이 바뀌도록 트리거
    var refreshKey by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000) // 60초 대기
            refreshKey++  // 값 변경 → recomposition 유도
        }
    }

    // refreshKey를 key로 사용해서 1분마다 recomposition 발생
    key(refreshKey) {
        content()
    }
}
