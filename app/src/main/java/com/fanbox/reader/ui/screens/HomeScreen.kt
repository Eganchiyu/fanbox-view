package com.fanbox.reader.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fanbox.reader.R
import com.fanbox.reader.data.model.HomeTab
import com.fanbox.reader.data.model.Post
import com.fanbox.reader.ui.components.LazyGridVerticalScrollbar
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
    
    val visible = remember(tab, posts, favorites) {
        if (tab == HomeTab.All) posts else posts.filter { favorites.contains(it.folder.uri.toString()) }
    }
    
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    TextButton(
                        onClick = choose,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text(stringResource(if (hasRoot) R.string.btn_change_dir else R.string.btn_choose_dir))
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            PrimaryTabRow(
                selectedTabIndex = if (tab == HomeTab.All) 0 else 1,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                divider = {}
            ) {
                Tab(
                    selected = tab == HomeTab.All,
                    onClick = { tab = HomeTab.All },
                    text = { Text(stringResource(R.string.tab_all), fontWeight = if (tab == HomeTab.All) FontWeight.Bold else FontWeight.Normal) }
                )
                Tab(
                    selected = tab == HomeTab.Favorites,
                    onClick = { tab = HomeTab.Favorites },
                    text = { Text(stringResource(R.string.tab_favorites), fontWeight = if (tab == HomeTab.Favorites) FontWeight.Bold else FontWeight.Normal) }
                )
            }
            
            if (visible.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .weight(1f),
                    Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (hasRoot) Icons.Default.Inbox else Icons.Default.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            stringResource(if (hasRoot) R.string.hint_no_content else R.string.hint_select_dir),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(24.dp))
                        Button(
                            onClick = choose,
                            shape = MaterialTheme.shapes.medium,
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                        ) {
                            Text(stringResource(R.string.btn_choose_dir))
                        }
                    }
                }
            } else {
                Box(Modifier.fillMaxSize()) {
                    PostGrid(visible, posts, gridState, open)
                    LazyGridVerticalScrollbar(
                        gridState = gridState,
                        modifier = Modifier.align(Alignment.CenterEnd)
                    )
                }
            }
        }
    }
}
