package com.fanbox.reader.ui.screens

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fanbox.reader.R
import com.fanbox.reader.data.model.Post
import com.fanbox.reader.data.repository.PostParser
import com.fanbox.reader.data.repository.PostRepository
import com.fanbox.reader.ui.components.ContentBlockItem
import com.fanbox.reader.ui.navigation.BackStack
import com.fanbox.reader.ui.navigation.Screen

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
    
    LaunchedEffect(pagerState.currentPage) {
        BackStack.replace(Screen.Detail(pagerState.currentPage))
    }
    
    HorizontalPager(pagerState, Modifier.fillMaxSize()) { page ->
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
    var blocks by remember(post.folder.uri) { mutableStateOf(post.blocks) }
    val listState = rememberLazyListState()
    
    LaunchedEffect(post.folder.uri) {
        if (blocks.isEmpty()) blocks = PostParser.withBlocks(post).blocks
    }
    
    val images = remember(blocks) {
        blocks.filterIsInstance<com.fanbox.reader.data.model.Block.Image>().map { it.file.uri }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(post.title, maxLines = 1) },
                navigationIcon = {
                    TextButton(onClick = { BackStack.pop() }) {
                        Text(stringResource(R.string.btn_back))
                    }
                },
                actions = {
                    TextButton(onClick = toggleFavorite) {
                        Text(stringResource(if (favorite) R.string.btn_unfavorite else R.string.btn_favorite))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(
                bottom = 24.dp + padding.calculateBottomPadding()
            )
        ) {
            item {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Text(post.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("${post.date} · ${index + 1}/${total}", color = MaterialTheme.colorScheme.primary)
                }
            }
            items(blocks) { block ->
                ContentBlockItem(block, images, openImage)
            }
        }
    }
}
