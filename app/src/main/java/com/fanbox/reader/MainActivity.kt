package com.fanbox.reader

import android.app.Activity
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.text.Html
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.LayoutInflater
import android.view.TextureView
import android.view.View
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.shuyu.gsyvideoplayer.video.StandardGSYVideoPlayer
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.util.Locale

private sealed interface Block {
    data class Text(val content: String, val isHtml: Boolean) : Block
    data class Image(val file: DocumentFile) : Block
    data class Video(val file: DocumentFile) : Block
    data class Unsupported(val name: String?) : Block
}

private data class Post(
    val folder: DocumentFile,
    val title: String,
    val date: String,
    val cover: DocumentFile?,
    val blocks: List<Block>
)

private enum class HomeTab { All, Favorites }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!BackStack.pop()) finish()
            }
        })
        setContent { FanboxReaderApp() }
    }
}

private object BackStack {
    private val states = ArrayDeque<Screen>()
    var current by mutableStateOf<Screen>(Screen.Home)
        private set

    fun open(screen: Screen) {
        states.addLast(current)
        current = screen
    }

    fun replace(screen: Screen) {
        current = screen
    }

    fun pop(): Boolean {
        if (states.isEmpty()) return false
        current = states.removeLast()
        return true
    }
}

private sealed interface Screen {
    data object Home : Screen
    data class Detail(val index: Int) : Screen
    data class Fullscreen(val images: List<Uri>, val index: Int) : Screen
    data class FullscreenVideo(val uri: Uri, val position: Long = 0L) : Screen
}

private object HomeMemory {
    var firstIndex = 0
    var firstOffset = 0
}

@Composable
fun FanboxReaderApp() {
    val context = LocalContext.current
    var root by remember { mutableStateOf<DocumentFile?>(null) }
    var posts by remember { mutableStateOf(emptyList<Post>()) }
    var favorites by remember { mutableStateOf(loadFavorites()) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching { context.contentResolver.takePersistableUriPermission(uri, 3) }
        context.getSharedPreferences("storage", 0).edit().putString("tree_uri", uri.toString()).apply()
        val selectedRoot = DocumentFile.fromTreeUri(context, uri)
        root = selectedRoot
        selectedRoot?.let { loadPosts(it) { posts = it } }
    }
    LaunchedEffect(Unit) {
        val saved = context.getSharedPreferences("storage", 0).getString("tree_uri", null)?.let(Uri::parse)
        if (saved != null && context.contentResolver.persistedUriPermissions.any { it.uri == saved && it.isReadPermission }) {
            root = DocumentFile.fromTreeUri(context, saved)
            root?.let { loadPosts(it) { posts = it } }
        }
    }
    MaterialTheme(colorScheme = lightColorScheme()) {
        when (val screen = BackStack.current) {
            Screen.Home -> HomeScreen(posts, root != null, favorites, { picker.launch(null) }) { BackStack.open(Screen.Detail(it)) }
            is Screen.Detail -> DetailPager(posts, screen.index, favorites, { updated -> favorites = updated }) { images, index -> BackStack.open(Screen.Fullscreen(images, index)) }
            is Screen.Fullscreen -> FullscreenScreen(screen.images, screen.index)
            is Screen.FullscreenVideo -> FullscreenVideoScreen(screen.uri, screen.position)
        }
    }
}

private fun loadPosts(root: DocumentFile, onLoaded: (List<Post>) -> Unit) {
    Thread {
        val datePattern = Regex("^\\d{4}-\\d{2}-\\d{2}[-_].+")
        val direct = root.listFiles().filter { it.isDirectory }
        val folders = if (direct.any { datePattern.matches(it.name.orEmpty()) }) direct else direct.flatMap { it.listFiles().filter { child -> child.isDirectory } }
        val quick = folders.map { folder ->
            val name = folder.name.orEmpty()
            val match = Regex("^(\\d{4}-\\d{2}-\\d{2})[-_](.+)$").find(name)
            Post(folder, match?.groupValues?.get(2) ?: name, match?.groupValues?.get(1) ?: "未知日期", null, emptyList())
        }.sortedByDescending { it.date }
        AppContext.context.mainExecutor.execute { onLoaded(quick) }
        val complete = quick.map { post -> post.withBlocks() }
        AppContext.context.mainExecutor.execute { onLoaded(complete) }
    }.start()
}

private fun Post.withBlocks(): Post {
    val htmlFile = folder.listFiles().firstOrNull { it.isFile && it.name?.substringAfterLast('.', "")?.lowercase(Locale.ROOT) in setOf("html", "htm") }
    val blocks = if (htmlFile != null) {
        val parsed = parseHtmlBlocks(folder, htmlFile)
        parsed.ifEmpty { fileBlocks() }
    } else {
        fileBlocks()
    }
    val cover = blocks.filterIsInstance<Block.Image>().firstOrNull()?.file
        ?: blocks.filterIsInstance<Block.Video>().firstOrNull()?.file
    return copy(cover = cover, blocks = blocks)
}

private fun Post.fileBlocks(): List<Block> {
    return folder.listFiles().filter { it.isFile }.sortedWith(compareBy(nullsLast()) { it.name?.lowercase(Locale.ROOT) }).map {
        when (kindOf(it.name)) {
            Kind.IMAGE -> Block.Image(it)
            Kind.VIDEO -> Block.Video(it)
            Kind.TEXT -> Block.Text(readText(it), isHtml = false)
            Kind.OTHER -> Block.Unsupported(it.name)
        }
    }
}

private fun parseHtmlBlocks(folder: DocumentFile, htmlFile: DocumentFile): List<Block> {
    val html = readText(htmlFile)
    if (html.isBlank()) return emptyList()
    val fileMap = folder.listFiles().filter { it.isFile }.associateBy { it.name ?: "" }
    val doc = Jsoup.parse(html)
    val body = doc.body()
    val blocks = mutableListOf<Block>()

    fun addMedia(element: Element) {
        when (element.tagName()) {
            "img" -> {
                val src = element.attr("src")
                    .ifBlank { element.attr("data-src") }
                    .ifBlank { element.attr("data-original") }
                resolveFile(src, folder, fileMap)?.let { blocks.add(Block.Image(it)) }
            }
            "video" -> {
                val src = element.attr("src").ifBlank { element.selectFirst("source")?.attr("src") ?: "" }
                resolveFile(src, folder, fileMap)?.let { blocks.add(Block.Video(it)) }
            }
        }
    }

    fun flushText(sb: StringBuilder) {
        val text = sb.toString().trim()
        if (text.isNotBlank()) blocks.add(Block.Text(text, isHtml = true))
        sb.clear()
    }

    fun addBlock(element: Element) {
        val tag = element.tagName()
        when {
            tag == "img" || tag == "video" -> addMedia(element)
            tag == "a" && isVideoUrl(element.attr("href")) -> {
                // <a href="video.mp4"> 解析为视频块，同时保留内部缩略图
                resolveFile(element.attr("href"), folder, fileMap)?.let { blocks.add(Block.Video(it)) }
                for (child in element.children()) {
                    if (child.tagName() == "img") addMedia(child)
                }
            }
            tag == "br" -> blocks.add(Block.Text("<br>", isHtml = true))
            tag == "hr" -> blocks.add(Block.Text("<hr>", isHtml = true))
            element.selectFirst("img, video") == null && !isVideoUrl(element.attr("href")) && element.select("a").none { isVideoUrl(it.attr("href")) } -> {
                val snippet = element.outerHtml()
                if (snippet.isNotBlank() && element.text().isNotBlank()) {
                    blocks.add(Block.Text(snippet, isHtml = true))
                }
            }
            else -> {
                // 混合内容：保留文本/嵌套标签，并递归提取其中图片/视频/视频链接
                val sb = StringBuilder()
                for (node in element.childNodes()) {
                    when {
                        node is Element && (node.tagName() == "img" || node.tagName() == "video") -> {
                            flushText(sb)
                            addMedia(node)
                        }
                        node is Element && (node.selectFirst("img, video") != null || (node.tagName() == "a" && isVideoUrl(node.attr("href")))) -> {
                            flushText(sb)
                            addBlock(node)
                        }
                        node is Element -> sb.append(node.outerHtml())
                        else -> sb.append(node.toString())
                    }
                }
                flushText(sb)
            }
        }
    }

    val topText = StringBuilder()
    for (node in body.childNodes()) {
        when {
            node is Element && (node.tagName() == "img" || node.tagName() == "video") -> {
                flushText(topText)
                addMedia(node)
            }
            node is Element && node.tagName() == "a" && isVideoUrl(node.attr("href")) -> {
                flushText(topText)
                addBlock(node)
            }
            node is Element -> {
                flushText(topText)
                addBlock(node)
            }
            else -> topText.append(node.toString())
        }
    }
    flushText(topText)
    return blocks
}

private fun isVideoUrl(url: String?): Boolean {
    if (url.isNullOrBlank()) return false
    val ext = url.substringAfterLast('.', "").substringBefore('?').substringBefore('#').lowercase(Locale.ROOT)
    return ext in setOf("mp4", "mkv", "webm", "mov", "avi", "3gp", "m4v", "ts")
}

private fun resolveFile(src: String?, folder: DocumentFile, fileMap: Map<String, DocumentFile>): DocumentFile? {
    if (src.isNullOrBlank()) return null
    val decoded = Uri.decode(src)
    val name = decoded.substringAfterLast('/').substringAfterLast('\\').substringBefore('?').substringBefore('#')
    if (name.isBlank()) return null
    return fileMap[name]
        ?: fileMap.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
        ?: folder.findFile(name)
}

private enum class Kind { IMAGE, VIDEO, TEXT, OTHER }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(posts: List<Post>, hasRoot: Boolean, favorites: Set<String>, choose: () -> Unit, open: (Int) -> Unit) {
    var tab by remember { mutableStateOf(HomeTab.All) }
    val gridState = rememberLazyGridState(HomeMemory.firstIndex, HomeMemory.firstOffset)
    DisposableEffect(gridState) {
        onDispose {
            HomeMemory.firstIndex = gridState.firstVisibleItemIndex
            HomeMemory.firstOffset = gridState.firstVisibleItemScrollOffset
        }
    }
    val visible = if (tab == HomeTab.All) posts else posts.filter { favorites.contains(it.folder.uri.toString()) }
    Scaffold(topBar = { TopAppBar(title = { Text("app-v2", fontWeight = FontWeight.Bold) }, actions = { TextButton(onClick = choose) { Text(if (hasRoot) "更换目录" else "选择目录") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = if (tab == HomeTab.All) 0 else 1) {
                Tab(tab == HomeTab.All, { tab = HomeTab.All }, text = { Text("全部") })
                Tab(tab == HomeTab.Favorites, { tab = HomeTab.Favorites }, text = { Text("收藏") })
            }
            if (visible.isEmpty()) Box(Modifier.fillMaxSize(), Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(if (hasRoot) "暂无内容" else "选择 fanbox 文件夹开始阅读"); Spacer(Modifier.height(12.dp)); Button(onClick = choose) { Text("选择文件夹") } } }
            else PostGrid(visible, posts, gridState, open)
        }
    }
}

@Composable
private fun PostGrid(visible: List<Post>, all: List<Post>, gridState: LazyGridState, open: (Int) -> Unit) {
    LazyVerticalGrid(GridCells.Adaptive(170.dp), Modifier.padding(10.dp), state = gridState, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        itemsIndexed(visible) { _, post ->
            val index = all.indexOfFirst { it.folder.uri == post.folder.uri }
            Card(Modifier.fillMaxWidth().clickable { if (index >= 0) open(index) }) { Column {
                if (post.cover != null) AsyncImage(post.cover.uri, post.title, Modifier.fillMaxWidth().height(180.dp), contentScale = ContentScale.Crop)
                else Box(Modifier.fillMaxWidth().height(180.dp).background(Color.LightGray), Alignment.Center) { Text("索引中…") }
                Column(Modifier.padding(12.dp)) { Text(post.title, maxLines = 2, fontWeight = FontWeight.SemiBold); Text(post.date, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary); Text(if (post.blocks.isEmpty()) "正在读取文件…" else "${post.blocks.size} 个块", style = MaterialTheme.typography.labelSmall) }
            } }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DetailPager(posts: List<Post>, startIndex: Int, favorites: Set<String>, updateFavorites: (Set<String>) -> Unit, openImage: (List<Uri>, Int) -> Unit) {
    if (posts.isEmpty()) return
    val pagerState = rememberPagerState(initialPage = startIndex.coerceIn(posts.indices)) { posts.size }
    LaunchedEffect(pagerState.currentPage) { BackStack.replace(Screen.Detail(pagerState.currentPage)) }
    HorizontalPager(pagerState, Modifier.fillMaxSize()) { page ->
        val post = posts[page]
        DetailScreen(post, page, posts.size, favorites.contains(post.folder.uri.toString()), {
            val id = post.folder.uri.toString()
            val updated = if (favorites.contains(id)) favorites - id else favorites + id
            saveFavorites(updated)
            updateFavorites(updated)
        }, openImage)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailScreen(post: Post, index: Int, total: Int, favorite: Boolean, toggleFavorite: () -> Unit, openImage: (List<Uri>, Int) -> Unit) {
    var blocks by remember(post.folder.uri) { mutableStateOf(post.blocks) }
    val listState = rememberLazyListState()
    LaunchedEffect(post.folder.uri) { if (blocks.isEmpty()) blocks = post.withBlocks().blocks }
    val images = remember(blocks) { blocks.filterIsInstance<Block.Image>().map { it.file.uri } }
    Scaffold(topBar = { TopAppBar(title = { Text(post.title, maxLines = 1) }, navigationIcon = { TextButton(onClick = { BackStack.pop() }) { Text("返回") } }, actions = { TextButton(onClick = toggleFavorite) { Text(if (favorite) "已收藏" else "收藏") } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), state = listState, verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) { Text(post.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text("${post.date} · ${index + 1}/${total}", color = MaterialTheme.colorScheme.primary) } }
            items(blocks) { block -> ContentBlockItem(block, images, openImage) }
        }
    }
}

@Composable
private fun ContentBlockItem(block: Block, images: List<Uri>, openImage: (List<Uri>, Int) -> Unit) {
    when (block) {
        is Block.Image -> AsyncImage(
            model = block.file.uri,
            contentDescription = block.file.name,
            modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp).clickable { openImage(images, images.indexOf(block.file.uri).coerceAtLeast(0)) },
            contentScale = ContentScale.FillWidth,
            onError = { Log.e("FanboxReader", "加载图片失败: ${block.file.name} / ${block.file.uri}", it.result.throwable) }
        )
        is Block.Text -> {
            if (block.isHtml) {
                AndroidView(
                    factory = { context ->
                        TextView(context).apply {
                            textSize = 16f
                            text = Html.fromHtml(block.content, Html.FROM_HTML_MODE_COMPACT, Html.ImageGetter { null }, null)
                            movementMethod = LinkMovementMethod.getInstance()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                )
            } else {
                Text(block.content, Modifier.fillMaxWidth().padding(20.dp), style = MaterialTheme.typography.bodyLarge)
            }
        }
        is Block.Video -> InlineVideoPlayer(block.file.uri)
        is Block.Unsupported -> Text("暂不支持：${block.name}", Modifier.padding(20.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FullscreenScreen(images: List<Uri>, startIndex: Int) {
    if (images.isEmpty()) return
    val pagerState = rememberPagerState(initialPage = startIndex.coerceIn(images.indices)) { images.size }
    var currentImageZoomed by remember { mutableStateOf(false) }
    var verticalDrag by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(pagerState.currentPage) {
        currentImageZoomed = false
        verticalDrag = 0f
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = verticalDrag
                    alpha = (1f - verticalDrag / size.height.coerceAtLeast(1f)).coerceIn(0.45f, 1f)
                }
                .pointerInput(currentImageZoomed) {
                    if (!currentImageZoomed) {
                        detectVerticalDragGestures(
                            onDragCancel = { verticalDrag = 0f },
                            onDragEnd = {
                                if (verticalDrag > size.height * 0.15f) BackStack.pop()
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
            userScrollEnabled = !currentImageZoomed
        ) { page ->
            ZoomImage(
                uri = images[page],
                active = page == pagerState.currentPage,
                onZoomChanged = { zoomed ->
                    if (page == pagerState.currentPage) currentImageZoomed = zoomed
                }
            )
        }
        TextButton(onClick = { BackStack.pop() }, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) { Text("X", color = Color.White) }
        Text("${pagerState.currentPage + 1}/${images.size}", color = Color.White, modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp))
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

@Composable
private fun InlineVideoPlayer(uri: Uri) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var playerRef by remember(uri) { mutableStateOf<StandardGSYVideoPlayer?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> playerRef?.onVideoPause()
                Lifecycle.Event.ON_RESUME -> playerRef?.onVideoResume()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            playerRef?.release()
        }
    }

    Box(modifier = Modifier.fillMaxWidth().height(260.dp)) {
        AndroidView(
            factory = { ctx ->
                StandardGSYVideoPlayer(ctx).apply {
                    setUp(uri.toString(), false, "")
                    fullscreenButton.visibility = View.GONE
                    setIsTouchWiget(true)
                    setLooping(false)
                }.also { playerRef = it }
            },
            modifier = Modifier.fillMaxSize()
        )
        TextButton(
            onClick = {
                val position = playerRef?.currentPositionWhenPlaying ?: 0
                playerRef?.onVideoPause()
                BackStack.open(Screen.FullscreenVideo(uri, position))
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .background(Color.Black.copy(alpha = 0.5f), MaterialTheme.shapes.small)
        ) { Text("全屏", color = Color.White) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FullscreenVideoScreen(uri: Uri, startPosition: Long) {
    val context = LocalContext.current
    val activity = context as? Activity
    var isPlaying by remember { mutableStateOf(true) }
    var duration by remember { mutableLongStateOf(0L) }
    var sliderPosition by remember { mutableFloatStateOf(startPosition.toFloat()) }
    val sliderInteractionSource = remember { MutableInteractionSource() }
    val isDragging by sliderInteractionSource.collectIsDraggedAsState()
    var showControls by remember { mutableStateOf(true) }
    var isSpeedUp by remember { mutableStateOf(false) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }

    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            seekTo(startPosition)
            prepare()
            playWhenReady = true
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) duration = duration.coerceAtLeast(this@apply.duration)
                }
            })
        }
    }

    DisposableEffect(Unit) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            player.release()
        }
    }

    LaunchedEffect(player, isDragging) {
        while (true) {
            if (!isDragging) sliderPosition = player.currentPosition.toFloat()
            kotlinx.coroutines.delay(500)
        }
    }

    LaunchedEffect(isSpeedUp) {
        player.setPlaybackSpeed(if (isSpeedUp) 2.5f else 1f)
    }

    fun clampOffset(value: Offset, targetScale: Float = scale): Offset {
        val maxX = viewport.width * (targetScale - 1f) / 2f
        val maxY = viewport.height * (targetScale - 1f) / 2f
        return Offset(value.x.coerceIn(-maxX, maxX), value.y.coerceIn(-maxY, maxY))
    }

    val transformState = rememberTransformableState { zoom, pan, _ ->
        val newScale = (scale * zoom).coerceIn(1f, 5f)
        scale = newScale
        offset = if (newScale == 1f) Offset.Zero else clampOffset(offset + pan, newScale)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { showControls = !showControls },
                    onDoubleTap = { tapOffset ->
                        val width = viewport.width.toFloat()
                        when {
                            width <= 0f -> showControls = !showControls
                            tapOffset.x < width * 0.35f -> player.seekTo((player.currentPosition - 10000L).coerceAtLeast(0L))
                            tapOffset.x > width * 0.65f -> player.seekTo((player.currentPosition + 10000L).coerceAtMost(duration.coerceAtLeast(0L)))
                            else -> showControls = !showControls
                        }
                    },
                    onLongPress = { isSpeedUp = true },
                    onPress = {
                        try {
                            awaitRelease()
                        } finally {
                            isSpeedUp = false
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx -> TextureView(ctx) },
            update = { view -> player.setVideoTextureView(view) },
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { viewport = it }
                .transformable(state = transformState, canPan = { scale > 1f })
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
        )

        if (showControls) {
            TextButton(
                onClick = { BackStack.pop() },
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
            ) { Text("X", color = Color.White) }

            IconButton(
                onClick = { player.playWhenReady = !player.playWhenReady },
                modifier = Modifier.align(Alignment.Center).size(64.dp)
            ) {
                Text(
                    text = if (isPlaying) "⏸" else "▶",
                    color = Color.White,
                    style = MaterialTheme.typography.displayMedium
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Slider(
                    value = sliderPosition.coerceIn(0f, duration.toFloat().coerceAtLeast(1f)),
                    onValueChange = { sliderPosition = it },
                    onValueChangeFinished = { player.seekTo(sliderPosition.toLong()) },
                    valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                    modifier = Modifier.fillMaxWidth(),
                    interactionSource = sliderInteractionSource,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.Gray
                    )
                )
                Text(
                    text = "${formatTime(sliderPosition.toLong())} / ${formatTime(duration)}",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }

        if (isSpeedUp) {
            Text(
                "x2.5",
                Modifier.align(Alignment.TopStart).padding(16.dp),
                color = Color.Yellow,
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

private fun loadFavorites(): Set<String> = AppContext.context.getSharedPreferences("favorites", 0).getStringSet("posts", emptySet()) ?: emptySet()
private fun saveFavorites(value: Set<String>) { AppContext.context.getSharedPreferences("favorites", 0).edit().putStringSet("posts", value).apply() }
private fun kindOf(name: String?): Kind = when (name?.substringAfterLast('.', "")?.lowercase(Locale.ROOT)) { "jpg", "jpeg", "png", "webp", "gif", "bmp" -> Kind.IMAGE; "mp4", "mkv", "webm", "mov", "avi" -> Kind.VIDEO; "txt", "md", "html", "htm" -> Kind.TEXT; else -> Kind.OTHER }
private fun readText(file: DocumentFile): String = runCatching { AppContext.context.contentResolver.openInputStream(file.uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: "" }.getOrElse { "无法读取文本：${it.message}" }
