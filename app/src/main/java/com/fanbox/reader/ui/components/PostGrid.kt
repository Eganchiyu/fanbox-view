package com.fanbox.reader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.fanbox.reader.R
import com.fanbox.reader.data.model.Post
import com.fanbox.reader.data.repository.PostRepository

@Composable
fun PostGrid(
    visible: List<Post>,
    all: List<Post>,
    gridState: LazyGridState,
    open: (Int) -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    // 预先建立 URI 到索引的映射，避免在 itemsIndexed 中循环查找 (O(N) -> O(1))
    val indexMap = remember(all) {
        all.mapIndexed { index, post -> post.folder.uri to index }.toMap()
    }

    LazyVerticalGrid(
        GridCells.Adaptive(170.dp),
        Modifier.fillMaxSize(),
        state = gridState,
        contentPadding = PaddingValues(
            start = 10.dp,
            top = 10.dp,
            end = 10.dp,
            bottom = 10.dp + contentPadding.calculateBottomPadding()
        ),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        itemsIndexed(
            items = visible, 
            key = { _, post -> post.folder.uri.toString() },
            contentType = { _, _ -> "post" } // 增加 contentType 优化回收性能
        ) { _, post ->
            val index = indexMap[post.folder.uri] ?: -1
            PostGridItem(post) { if (index >= 0) open(index) }
        }
    }
}

@Composable
private fun PostGridItem(post: Post, onClick: () -> Unit) {
    var cover by remember(post.folder.uri) { mutableStateOf(post.cover) }

    LaunchedEffect(post.folder.uri) {
        if (cover == null) {
            cover = PostRepository.loadCover(post)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (cover != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(cover!!.uri)
                            .crossfade(true)
                            .size(coil.size.Size.ORIGINAL) // 这里可以考虑限制大小以提升性能
                            .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                            .build(),
                        contentDescription = post.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        Modifier.fillMaxSize(),
                        Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        )
                    }
                }
                
                // 渐变蒙层，提升文字可读性（如果需要在图片上显示文字的话，目前是分开放的，但也增加质感）
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.05f)),
                                startY = 100f
                            )
                        )
                )
            }
            
            Column(Modifier.padding(12.dp)) {
                Text(
                    text = post.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(Modifier.height(4.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = post.date,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (post.blocks.isEmpty()) stringResource(R.string.state_reading)
                        else stringResource(R.string.unit_blocks, post.blocks.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
