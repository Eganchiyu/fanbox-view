package com.fanbox.reader.ui.components

import android.net.Uri
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.fanbox.reader.R
import com.fanbox.reader.ui.navigation.BackStack
import com.fanbox.reader.ui.navigation.Screen
import com.shuyu.gsyvideoplayer.video.StandardGSYVideoPlayer

@Composable
fun InlineVideoPlayer(uri: Uri) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var playerRef by remember(uri) { mutableStateOf<StandardGSYVideoPlayer?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> playerRef?.onVideoPause()
                Lifecycle.Event.ON_RESUME -> playerRef?.onVideoResume()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            playerRef?.release()
        }
    }

    Box(modifier = Modifier.fillMaxWidth().height(260.dp)) {
        AndroidView(
            factory = { ctx ->
                StandardGSYVideoPlayer(ctx).apply {
                    setUp(uri.toString(), false, "")
                    fullscreenButton.visibility = View.GONE
                    setIsTouchWiget(true)
                    setLooping(false)
                }.also { playerRef = it }
            },
            modifier = Modifier.fillMaxSize()
        )
        TextButton(
            onClick = {
                val position = playerRef?.currentPositionWhenPlaying ?: 0
                playerRef?.onVideoPause()
                BackStack.open(Screen.FullscreenVideo(uri, position))
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .background(Color.Black.copy(alpha = 0.5f), MaterialTheme.shapes.small)
        ) {
            Text(stringResource(R.string.btn_fullscreen), color = Color.White)
        }
    }
}
