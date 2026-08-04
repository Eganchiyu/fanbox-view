package com.fanbox.reader.ui.screens

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.fanbox.reader.ui.navigation.BackStack

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FullscreenScreen(images: List<Uri>, startIndex: Int) {
    if (images.isEmpty()) return
    val pagerState = rememberPagerState(initialPage = startIndex.coerceIn(images.indices)) { images.size }
    var currentImageZoomed by remember { mutableStateOf(false) }
    var verticalDrag by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(pagerState.currentPage) {
        currentImageZoomed = false
        verticalDrag = 0f
    }

    val fadeLimit = 800f
    
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = (1f - (verticalDrag / fadeLimit)).coerceIn(0f, 1f)))
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = verticalDrag
                }
                .pointerInput(currentImageZoomed) {
                    if (!currentImageZoomed) {
                        detectVerticalDragGestures(
                            onDragCancel = { verticalDrag = 0f },
                            onDragEnd = {
                                if (verticalDrag > 200f) BackStack.pop()
                                else verticalDrag = 0f
                            },
                            onVerticalDrag = { change, amount ->
                                if (amount > 0f || verticalDrag > 0f) {
                                    verticalDrag = (verticalDrag + amount).coerceAtLeast(0f)
                                    change.consume()
                                }
                            }
                        )
                    }
                },
            userScrollEnabled = !currentImageZoomed,
            beyondViewportPageCount = 1
        ) { page ->
            ZoomImage(
                uri = images[page],
                active = page == pagerState.currentPage,
                onZoomChanged = { zoomed ->
                    if (page == pagerState.currentPage) currentImageZoomed = zoomed
                }
            )
        }
        TextButton(
            onClick = { BackStack.pop() },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(16.dp)
        ) {
            Text("X", color = Color.White)
        }
        Text(
            "${pagerState.currentPage + 1}/${images.size}",
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(24.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ZoomImage(uri: Uri, active: Boolean, onZoomChanged: (Boolean) -> Unit) {
    var scale by remember(uri) { mutableFloatStateOf(1f) }
    var offset by remember(uri) { mutableStateOf(Offset.Zero) }
    var viewport by remember(uri) { mutableStateOf(IntSize.Zero) }

    fun clampOffset(value: Offset, targetScale: Float = scale): Offset {
        val maxX = viewport.width * (targetScale - 1f) / 2f
        val maxY = viewport.height * (targetScale - 1f) / 2f
        return Offset(value.x.coerceIn(-maxX, maxX), value.y.coerceIn(-maxY, maxY))
    }

    val transformState = rememberTransformableState { zoom, pan, _ ->
        val newScale = (scale * zoom).coerceIn(1f, 5f)
        scale = newScale
        offset = if (newScale == 1f) Offset.Zero else clampOffset(offset + pan, newScale)
        onZoomChanged(newScale > 1.01f)
    }

    LaunchedEffect(active) {
        if (!active) {
            scale = 1f
            offset = Offset.Zero
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { viewport = it }
            .transformable(
                state = transformState,
                canPan = { scale > 1f },
                enabled = active
            ),
        Alignment.Center
    ) {
        AsyncImage(
            model = uri,
            contentDescription = null,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
            contentScale = ContentScale.Fit
        )
    }
}
