package com.fanbox.reader.ui.screens

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fanbox.reader.R
import com.fanbox.reader.data.model.Post
import com.fanbox.reader.data.repository.PostParser
import com.fanbox.reader.data.repository.PostRepository
import com.fanbox.reader.ui.components.ContentBlockItem
import com.fanbox.reader.ui.components.LazyListVerticalScrollbar
import com.fanbox.reader.ui.navigation.BackStack
import com.fanbox.reader.ui.navigation.Screen

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DetailPager(
    posts: List<Post>,
    startIndex: Int,
    favorites: Set<String>,
    updateFavorites: (Set<String>) -> Unit,
    openImage: (List<Uri>, Int) -> Unit
) {
    if (posts.isEmpty()) return
    val pagerState = rememberPagerState(initialPage = startIndex.coerceIn(posts.indices)) { posts.size }
    
    // 预加载前后页
    val currentPage = pagerState.currentPage
    LaunchedEffect(currentPage) {
        BackStack.replace(Screen.Detail(currentPage))
        
        // 后台预加载相邻页的内容
        withContext(Dispatchers.IO) {
            val toPreload = listOf(currentPage - 1, currentPage + 1)
                .filter { it in posts.indices }
            toPreload.forEach { index ->
                val p = posts[index]
                if (p.blocks.isEmpty()) {
                    PostParser.withBlocks(p)
                }
            }
        }
    }
    
    HorizontalPager(
        pagerState, 
        Modifier.fillMaxSize(),
        beyondViewportPageCount = 1 // 预加载相邻页的 UI
    ) { page ->
        val post = posts[page]
        DetailScreen(
            post, page, posts.size, favorites.contains(post.folder.uri.toString()),
            {
                val id = post.folder.uri.toString()
                val updated = if (favorites.contains(id)) favorites - id else favorites + id
                PostRepository.saveFavorites(updated)
                updateFavorites(updated)
            },
            openImage
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailScreen(
    post: Post,
    index: Int,
    total: Int,
    favorite: Boolean,
    toggleFavorite: () -> Unit,
    openImage: (List<Uri>, Int) -> Unit
) {
    var blocks by remember(post.folder.uri) { 
        mutableStateOf(post.blocks.ifEmpty { PostParser.getCachedBlocks(post.folder.uri.toString()) ?: emptyList() }) 
    }
    val listState = rememberLazyListState()
    
    LaunchedEffect(post.folder.uri) {
        if (blocks.isEmpty()) {
            val result = withContext(Dispatchers.IO) {
                PostParser.withBlocks(post)
            }
            blocks = result.blocks
        }
    }
    
    val images = remember(blocks) {
        blocks.filterIsInstance<com.fanbox.reader.data.model.Block.Image>().map { it.file.uri }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(post.title, maxLines = 1, style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = { BackStack.pop() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.btn_back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = toggleFavorite) {
                        Icon(
                            imageVector = if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = stringResource(if (favorite) R.string.btn_unfavorite else R.string.btn_favorite),
                            tint = if (favorite) Color.Red else MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(
                    bottom = 32.dp
                )
            ) {
                item {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                    ) {
                        Text(
                            text = post.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 32.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    text = "${index + 1} / $total",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = post.date,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
                items(blocks) { block ->
                    ContentBlockItem(block, images, openImage)
                }
            }
            
            LazyListVerticalScrollbar(
                listState = listState,
                modifier = Modifier.align(Alignment.CenterEnd).padding(padding)
            )
        }
    }
}
