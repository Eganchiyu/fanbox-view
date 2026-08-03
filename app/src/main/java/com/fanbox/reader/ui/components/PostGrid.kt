package com.fanbox.reader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
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
        itemsIndexed(visible, key = { _, post -> post.folder.uri.toString() }) { _, post ->
            val index = all.indexOfFirst { it.folder.uri == post.folder.uri }
            PostGridItem(post) { if (index >= 0) open(index) }
        }
    }
}

@Composable
private fun PostGridItem(post: Post, onClick: () -> Unit) {
    var cover by remember(post.folder.uri) { mutableStateOf(post.cover) }
    
    LaunchedEffect(post.folder.uri) {
        if (cover == null) {
            PostRepository.loadCover(post) { loaded ->
                cover = loaded
            }
        }
    }

    Card(Modifier.fillMaxWidth().clickable { onClick() }) {
        Column {
            if (cover != null) {
                AsyncImage(
                    cover!!.uri,
                    post.title,
                    Modifier.fillMaxWidth().height(180.dp),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    Modifier.fillMaxWidth().height(180.dp).background(Color.LightGray),
                    Alignment.Center
                ) {
                    Text(stringResource(R.string.state_indexing))
                }
            }
            Column(Modifier.padding(12.dp)) {
                Text(post.title, maxLines = 2, fontWeight = FontWeight.SemiBold)
                Text(
                    post.date,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    if (post.blocks.isEmpty()) stringResource(R.string.state_reading) 
                    else stringResource(R.string.unit_blocks, post.blocks.size),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}
