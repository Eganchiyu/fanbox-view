package com.fanbox.reader.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.TextureView
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.fanbox.reader.ui.navigation.BackStack

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FullscreenVideoScreen(uri: Uri, startPosition: Long) {
    val context = LocalContext.current
    val activity = context as? Activity
    var isPlaying by remember { mutableStateOf(true) }
    var duration by remember { mutableLongStateOf(0L) }
    var sliderPosition by remember { mutableFloatStateOf(startPosition.toFloat()) }
    val sliderInteractionSource = remember { MutableInteractionSource() }
    val isDragging by sliderInteractionSource.collectIsDraggedAsState()
    var showControls by remember { mutableStateOf(true) }
    var isSpeedUp by remember { mutableStateOf(false) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }

    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            seekTo(startPosition)
            prepare()
            playWhenReady = true
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) duration = duration.coerceAtLeast(this@apply.duration)
                }
            })
        }
    }

    DisposableEffect(Unit) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            player.release()
        }
    }

    LaunchedEffect(player, isDragging) {
        while (true) {
            if (!isDragging) sliderPosition = player.currentPosition.toFloat()
            kotlinx.coroutines.delay(500)
        }
    }

    LaunchedEffect(isSpeedUp) {
        player.setPlaybackSpeed(if (isSpeedUp) 2.5f else 1f)
    }

    fun clampOffset(value: Offset, targetScale: Float = scale): Offset {
        val maxX = viewport.width * (targetScale - 1f) / 2f
        val maxY = viewport.height * (targetScale - 1f) / 2f
        return Offset(value.x.coerceIn(-maxX, maxX), value.y.coerceIn(-maxY, maxY))
    }

    val transformState = rememberTransformableState { zoom, pan, _ ->
        val newScale = (scale * zoom).coerceIn(1f, 5f)
        scale = newScale
        offset = if (newScale == 1f) Offset.Zero else clampOffset(offset + pan, newScale)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { showControls = !showControls },
                    onDoubleTap = { tapOffset ->
                        val width = viewport.width.toFloat()
                        when {
                            width <= 0f -> showControls = !showControls
                            tapOffset.x < width * 0.35f -> player.seekTo((player.currentPosition - 10000L).coerceAtLeast(0L))
                            tapOffset.x > width * 0.65f -> player.seekTo((player.currentPosition + 10000L).coerceAtMost(duration.coerceAtLeast(0L)))
                            else -> showControls = !showControls
                        }
                    },
                    onLongPress = { isSpeedUp = true },
                    onPress = {
                        try {
                            awaitRelease()
                        } finally {
                            isSpeedUp = false
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx -> TextureView(ctx) },
            update = { view -> player.setVideoTextureView(view) },
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { viewport = it }
                .transformable(state = transformState, canPan = { scale > 1f })
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
        )

        if (showControls) {
            TextButton(
                onClick = { BackStack.pop() },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(16.dp)
            ) { Text("X", color = Color.White) }

            IconButton(
                onClick = { player.playWhenReady = !player.playWhenReady },
                modifier = Modifier.align(Alignment.Center).size(64.dp)
            ) {
                Text(
                    text = if (isPlaying) "⏸" else "▶",
                    color = Color.White,
                    style = MaterialTheme.typography.displayMedium
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(16.dp)
            ) {
                Slider(
                    value = sliderPosition.coerceIn(0f, duration.toFloat().coerceAtLeast(1f)),
                    onValueChange = { sliderPosition = it },
                    onValueChangeFinished = { player.seekTo(sliderPosition.toLong()) },
                    valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                    modifier = Modifier.fillMaxWidth(),
                    interactionSource = sliderInteractionSource,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.Gray
                    )
                )
                Text(
                    text = "${formatTime(sliderPosition.toLong())} / ${formatTime(duration)}",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }

        if (isSpeedUp) {
            Text(
                "x2.5",
                Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(16.dp),
                color = Color.Yellow,
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
