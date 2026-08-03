package com.fanbox.reader.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fanbox.reader.R
import com.fanbox.reader.data.model.HomeTab
import com.fanbox.reader.data.model.Post
import com.fanbox.reader.ui.components.PostGrid

private object HomeMemory {
    var firstIndex = 0
    var firstOffset = 0
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(posts: List<Post>, hasRoot: Boolean, favorites: Set<String>, choose: () -> Unit, open: (Int) -> Unit) {
    var tab by remember { mutableStateOf(HomeTab.All) }
    val gridState = rememberLazyGridState(HomeMemory.firstIndex, HomeMemory.firstOffset)
    
    DisposableEffect(gridState) {
        onDispose {
            HomeMemory.firstIndex = gridState.firstVisibleItemIndex
            HomeMemory.firstOffset = gridState.firstVisibleItemScrollOffset
        }
    }
    
    val visible = if (tab == HomeTab.All) posts else posts.filter { favorites.contains(it.folder.uri.toString()) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name), fontWeight = FontWeight.Bold) },
                actions = {
                    TextButton(onClick = choose) {
                        Text(stringResource(if (hasRoot) R.string.btn_change_dir else R.string.btn_choose_dir))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
        ) {
            TabRow(selectedTabIndex = if (tab == HomeTab.All) 0 else 1) {
                Tab(tab == HomeTab.All, { tab = HomeTab.All }, text = { Text(stringResource(R.string.tab_all)) })
                Tab(tab == HomeTab.Favorites, { tab = HomeTab.Favorites }, text = { Text(stringResource(R.string.tab_favorites)) })
            }
            if (visible.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding()), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(if (hasRoot) R.string.hint_no_content else R.string.hint_select_dir))
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = choose) {
                            Text(stringResource(R.string.btn_choose_dir))
                        }
                    }
                }
            } else {
                PostGrid(visible, posts, gridState, open, contentPadding = padding)
            }
        }
    }
}
