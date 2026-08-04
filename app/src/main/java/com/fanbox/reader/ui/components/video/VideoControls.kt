package com.fanbox.reader.ui.components.video

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun VideoControls(
    state: VideoPlayerState,
    isFullscreen: Boolean = false,
    isLandscape: Boolean = false,
    onToggleRotation: () -> Unit = {},
    onClose: () -> Unit = {},
    onToggleFullscreen: () -> Unit = {}
) {
    val durationText = remember(state.duration) { VideoPlayerUtils.formatTime(state.duration) }
    val positionText = remember(state.sliderPosition) { VideoPlayerUtils.formatTime(state.sliderPosition.toLong()) }

    AnimatedVisibility(
        visible = state.showControls,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Background gradient (bottom only)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f))
                        )
                    )
            )

            // Top controls
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isFullscreen) {
                    IconButton(
                        onClick = onToggleRotation,
                        colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.2f))
                    ) {
                        Icon(
                            imageVector = if (isLandscape) Icons.Default.ScreenRotation else Icons.Default.ScreenLockRotation,
                            contentDescription = "Rotate",
                            tint = Color.White
                        )
                    }
                    IconButton(
                        onClick = onClose,
                        colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.2f))
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                } else {
                    IconButton(
                        onClick = onToggleFullscreen,
                        colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.3f))
                    ) {
                        Icon(Icons.Default.Fullscreen, contentDescription = "Fullscreen", tint = Color.White)
                    }
                }
            }

            // Bottom controls
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .navigationBarsPadding()
            ) {
                // Progress Bar
                Slider(
                    value = state.sliderPosition.coerceIn(0f, state.duration.toFloat().coerceAtLeast(1f)),
                    onValueChange = { 
                        state.isDragging = true
                        state.sliderPosition = it 
                    },
                    onValueChangeFinished = { 
                        state.isDragging = false
                        state.player.seekTo(state.sliderPosition.toLong()) 
                    },
                    valueRange = 0f..state.duration.toFloat().coerceAtLeast(1f),
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                    )
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { if (state.isPlaying) state.player.pause() else state.player.play() },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (state.isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "$positionText / $durationText",
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}
