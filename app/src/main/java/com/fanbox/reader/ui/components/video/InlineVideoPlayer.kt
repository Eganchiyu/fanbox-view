package com.fanbox.reader.ui.components.video

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.fanbox.reader.ui.navigation.BackStack
import com.fanbox.reader.ui.navigation.Screen

@Composable
fun InlineVideoPlayer(uri: Uri) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state = rememberVideoPlayerState(context, uri, autoPlay = false)

    DisposableEffect(lifecycleOwner, state.player) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                state.player.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 200.dp, max = 500.dp)
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { state.toggleControls() })
            }
    ) {
        VideoSurface(player = state.player)
        
        VideoControls(
            state = state,
            isFullscreen = false,
            onToggleFullscreen = {
                val pos = state.player.currentPosition
                state.player.pause()
                BackStack.open(Screen.FullscreenVideo(uri, pos))
            }
        )
    }
}
