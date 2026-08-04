package com.fanbox.reader.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.max

@Composable
fun LazyGridVerticalScrollbar(
    gridState: LazyGridState,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    ScrollbarImpl(
        isScrollInProgress = gridState.isScrollInProgress,
        totalItemsCount = { gridState.layoutInfo.totalItemsCount },
        visibleItemsCount = { gridState.layoutInfo.visibleItemsInfo.size },
        firstVisibleItemIndex = { gridState.firstVisibleItemIndex },
        scrollToItem = { index -> coroutineScope.launch { gridState.scrollToItem(index) } },
        modifier = modifier
    )
}

@Composable
fun LazyListVerticalScrollbar(
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    ScrollbarImpl(
        isScrollInProgress = listState.isScrollInProgress,
        totalItemsCount = { listState.layoutInfo.totalItemsCount },
        visibleItemsCount = { listState.layoutInfo.visibleItemsInfo.size },
        firstVisibleItemIndex = { listState.firstVisibleItemIndex },
        scrollToItem = { index -> coroutineScope.launch { listState.scrollToItem(index) } },
        modifier = modifier
    )
}

@Composable
private fun ScrollbarImpl(
    isScrollInProgress: Boolean,
    totalItemsCount: () -> Int,
    visibleItemsCount: () -> Int,
    firstVisibleItemIndex: () -> Int,
    scrollToItem: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }
    val scrollbarAlpha by animateFloatAsState(
        targetValue = if (isDragging || isScrollInProgress) 1f else 0f,
        animationSpec = tween(durationMillis = if (isDragging || isScrollInProgress) 0 else 500),
        label = "alpha"
    )

    BoxWithConstraints(modifier.fillMaxHeight().padding(vertical = 4.dp).padding(end = 2.dp)) {
        val viewportHeight = constraints.maxHeight.toFloat()
        
        val scrollInfo by remember {
            derivedStateOf {
                val total = totalItemsCount()
                val visible = visibleItemsCount()
                if (total == 0 || visible == 0) null
                else {
                    val estimatedTotalHeight = (viewportHeight / visible) * total
                    val thumbHeight = max(viewportHeight * (viewportHeight / estimatedTotalHeight), 40f)
                    val scrollOffset = firstVisibleItemIndex().toFloat() / total * viewportHeight
                    Pair(thumbHeight, scrollOffset)
                }
            }
        }

        val info = scrollInfo ?: return@BoxWithConstraints
        val thumbHeight = info.first
        val scrollOffset = info.second

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .graphicsLayer {
                    translationY = scrollOffset * (viewportHeight - thumbHeight) / viewportHeight
                }
                .width(5.dp)
                .height(thumbHeight.dp)
                .alpha(scrollbarAlpha)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                .pointerInput(viewportHeight, thumbHeight) {
                    detectDragGestures(
                        onDragStart = { isDragging = true },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false },
                        onDrag = { _, dragAmount ->
                            val total = totalItemsCount()
                            if (total > 0) {
                                val delta = dragAmount.y / (viewportHeight - thumbHeight)
                                val itemsToScroll = (delta * total).toInt()
                                scrollToItem((firstVisibleItemIndex() + itemsToScroll).coerceIn(0, total - 1))
                            }
                        }
                    )
                }
        )
    }
}
