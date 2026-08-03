package com.fanbox.reader

import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.documentfile.provider.DocumentFile
import com.fanbox.reader.data.model.Post
import com.fanbox.reader.data.repository.PostRepository
import com.fanbox.reader.ui.navigation.BackStack
import com.fanbox.reader.ui.navigation.Screen
import com.fanbox.reader.ui.screens.DetailPager
import com.fanbox.reader.ui.screens.FullscreenScreen
import com.fanbox.reader.ui.screens.FullscreenVideoScreen
import com.fanbox.reader.ui.screens.HomeScreen
import com.fanbox.reader.ui.theme.FanboxReaderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // 1. 开启 Edge-to-Edge 沉浸式
        enableEdgeToEdge()

        super.onCreate(savedInstanceState)

        // 2. 放置在此处：禁用导航栏对比度强制遮罩（需 API 29 / Android 10+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!BackStack.pop()) finish()
            }
        })
        setContent {
            FanboxReaderTheme {
                FanboxReaderApp()
            }
        }
    }
}

@Composable
fun FanboxReaderApp() {
    val context = LocalContext.current
    var root by remember { mutableStateOf<DocumentFile?>(null) }
    var posts by remember { mutableStateOf(emptyList<Post>()) }
    var favorites by remember { mutableStateOf(PostRepository.loadFavorites()) }
    
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching { context.contentResolver.takePersistableUriPermission(uri, 3) }
        context.getSharedPreferences("storage", 0).edit().putString("tree_uri", uri.toString()).apply()
        val selectedRoot = DocumentFile.fromTreeUri(context, uri)
        root = selectedRoot
        selectedRoot?.let { PostRepository.loadPosts(it) { list -> posts = list } }
    }
    
    LaunchedEffect(Unit) {
        val saved = context.getSharedPreferences("storage", 0).getString("tree_uri", null)?.let(Uri::parse)
        if (saved != null && context.contentResolver.persistedUriPermissions.any { it.uri == saved && it.isReadPermission }) {
            root = DocumentFile.fromTreeUri(context, saved)
            root?.let { PostRepository.loadPosts(it) { list -> posts = list } }
        }
    }
    
    when (val screen = BackStack.current) {
        Screen.Home -> HomeScreen(
            posts = posts,
            hasRoot = root != null,
            favorites = favorites,
            choose = { picker.launch(null) },
            open = { BackStack.open(Screen.Detail(it)) }
        )
        is Screen.Detail -> DetailPager(
            posts = posts,
            startIndex = screen.index,
            favorites = favorites,
            updateFavorites = { favorites = it },
            openImage = { images, index -> BackStack.open(Screen.Fullscreen(images, index)) }
        )
        is Screen.Fullscreen -> FullscreenScreen(
            images = screen.images,
            startIndex = screen.index
        )
        is Screen.FullscreenVideo -> FullscreenVideoScreen(
            uri = screen.uri,
            startPosition = screen.position
        )
    }
}
