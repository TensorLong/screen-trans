package com.yiqun.translator.ui.common

import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun MP4Player(resourceId: Int, width: Dp = 300.dp, height: Dp = 300.dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    var videoUri = Uri.EMPTY
    var showErrorMessage = false

    val videoView = remember { VideoView(context) }

    try {
        val resourceUri = Uri.parse("android.resource://${context.packageName}/$resourceId")
        videoUri = resourceUri
    } catch (e: Exception) {
        showErrorMessage = true
    }

    Box(
        modifier = modifier
            .width(width)
            .height(height)
    ) {
        if (showErrorMessage) {
            Text(
                text = "Video resource not found.",
                fontSize = 20.sp,
                color = Color.Gray,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            AndroidView(
                factory = { _ ->
                    videoView.apply {
                        setOnPreparedListener { mediaPlayer ->
                            mediaPlayer.isLooping = true
                        }
                    }
                },
                update = { videoView ->
                    videoView.setVideoURI(videoUri)
                    videoView.start()
                },
                modifier = Modifier.matchParentSize()
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            videoView.stopPlayback()
        }
    }
}

