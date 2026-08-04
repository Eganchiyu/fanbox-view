package com.fanbox.reader.ui.components.video

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize

private const val SEEK_STEP_MS = 10000L

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GestureVideoSurface(
    state: VideoPlayerState,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }

    fun clampOffset(value: Offset, targetScale: Float): Offset {
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
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { viewport = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { state.toggleControls() },
                    onDoubleTap = { tapOffset ->
                        val width = viewport.width.toFloat()
                        if (width > 0f) {
                            when {
                                tapOffset.x < width * 0.35f -> state.seekBy(-SEEK_STEP_MS)
                                tapOffset.x > width * 0.65f -> state.seekBy(SEEK_STEP_MS)
                                else -> state.toggleControls()
                            }
                        }
                    },
                    onLongPress = { state.isSpeedUp = true },
                    onPress = {
                        try {
                            awaitRelease()
                        } finally {
                            state.isSpeedUp = false
                        }
                    }
                )
            }
            .transformable(state = transformState, canPan = { scale > 1f })
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
        contentAlignment = Alignment.Center
    ) {
        VideoSurface(player = state.player)
    }
}
