package com.fanbox.reader.ui.components.video

import android.view.LayoutInflater
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.fanbox.reader.R

@OptIn(UnstableApi::class)
@Composable
fun VideoSurface(
    player: Player,
    modifier: Modifier = Modifier.fillMaxSize()
) {
    AndroidView(
        factory = { ctx ->
            LayoutInflater.from(ctx).inflate(R.layout.player_view_texture, null).apply {
                (this as PlayerView).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setBackgroundColor(android.graphics.Color.BLACK)
                }
            }
        },
        update = { view ->
            (view as PlayerView).player = player
        },
        modifier = modifier
    )
}
