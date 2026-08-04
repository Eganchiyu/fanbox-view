package com.fanbox.reader.ui.components.video

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.runtime.*
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay

@Stable
class VideoPlayerState(
    val player: Player,
    isPlaying: Boolean,
    duration: Long,
    sliderPosition: Float,
    showControls: Boolean
) {
    var isPlaying by mutableStateOf(isPlaying)
    var duration by mutableLongStateOf(duration)
    var sliderPosition by mutableFloatStateOf(sliderPosition)
    var showControls by mutableStateOf(showControls)
    var isDragging by mutableStateOf(false)
    var isSpeedUp by mutableStateOf(false)

    fun toggleControls() {
        showControls = !showControls
    }

    fun seekBy(offsetMs: Long) {
        val newPos = (player.currentPosition + offsetMs).coerceIn(0, duration)
        player.seekTo(newPos)
        sliderPosition = newPos.toFloat()
    }
}

@OptIn(UnstableApi::class)
@Composable
fun rememberVideoPlayerState(
    context: Context,
    uri: Uri,
    startPosition: Long = 0L,
    autoPlay: Boolean = true
): VideoPlayerState {
    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            if (startPosition > 0) seekTo(startPosition)
            prepare()
            playWhenReady = autoPlay
        }
    }

    val state = remember(player) {
        VideoPlayerState(
            player = player,
            isPlaying = autoPlay,
            duration = 0L,
            sliderPosition = startPosition.toFloat(),
            showControls = false
        )
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                state.isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    state.duration = player.duration.coerceAtLeast(0L)
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    // Progress update loop
    LaunchedEffect(player, state.isPlaying, state.isDragging) {
        if (state.isPlaying && !state.isDragging) {
            while (true) {
                state.sliderPosition = player.currentPosition.toFloat()
                delay(500)
            }
        }
    }

    LaunchedEffect(state.isSpeedUp) {
        player.setPlaybackSpeed(if (state.isSpeedUp) 2.5f else 1f)
    }

    return state
}
