package com.fanbox.reader.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fanbox.reader.ui.components.video.GestureVideoSurface
import com.fanbox.reader.ui.components.video.VideoControls
import com.fanbox.reader.ui.components.video.rememberVideoPlayerState
import com.fanbox.reader.ui.navigation.BackStack

@Composable
fun FullscreenVideoScreen(uri: Uri, startPosition: Long) {
    val context = LocalContext.current
    val activity = context as? Activity
    val state = rememberVideoPlayerState(context, uri, startPosition)
    var isLandscape by remember { mutableStateOf(false) }

    DisposableEffect(isLandscape) {
        activity?.requestedOrientation = if (isLandscape)
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        else
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        onDispose { }
    }

    DisposableEffect(Unit) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        GestureVideoSurface(state = state)

        VideoControls(
            state = state,
            isFullscreen = true,
            isLandscape = isLandscape,
            onToggleRotation = { isLandscape = !isLandscape },
            onClose = { BackStack.pop() }
        )

        if (state.isSpeedUp) {
            SpeedUpIndicator(Modifier.align(Alignment.TopCenter))
        }
    }
}

@Composable
private fun SpeedUpIndicator(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .statusBarsPadding()
            .padding(top = 16.dp),
        color = Color.Black.copy(alpha = 0.4f),
        shape = CircleShape
    ) {
        Text(
            "x2.5 >>",
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            color = Color.White,
            style = MaterialTheme.typography.labelLarge
        )
    }
}
