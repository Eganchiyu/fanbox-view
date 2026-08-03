package com.fanbox.reader.data.repository

import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import com.fanbox.reader.AppContext
import com.fanbox.reader.data.model.Post
import java.util.concurrent.Executors

object PostRepository {

    private val executor = Executors.newFixedThreadPool(4)

    fun loadPosts(root: DocumentFile, onLoaded: (List<Post>) -> Unit) {
        Thread {
            val datePattern = Regex("^\\d{4}-\\d{2}-\\d{2}[-_].+")
            val direct = root.listFiles().filter { it.isDirectory }
            val folders = if (direct.any { datePattern.matches(it.name.orEmpty()) }) direct 
                          else direct.flatMap { it.listFiles().filter { child -> child.isDirectory } }
            
            val quick = folders.map { folder ->
                val name = folder.name.orEmpty()
                val match = Regex("^(\\d{4}-\\d{2}-\\d{2})[-_](.+)$").find(name)
                val cachedUri = PostParser.bannerPrefs.getString(folder.uri.toString(), null)
                val cover = cachedUri?.let { DocumentFile.fromSingleUri(AppContext.context, Uri.parse(it)) }
                Post(folder, match?.groupValues?.get(2) ?: name, match?.groupValues?.get(1) ?: "未知日期", cover, emptyList())
            }.sortedByDescending { it.date }
            
            ContextCompat.getMainExecutor(AppContext.context).execute { onLoaded(quick) }
        }.start()
    }

    fun loadCover(post: Post, onLoaded: (DocumentFile?) -> Unit) {
        executor.execute {
            val cover = PostParser.scanForCover(post.folder)
            if (cover != null) {
                PostParser.bannerPrefs.edit().putString(post.folder.uri.toString(), cover.uri.toString()).apply()
            }
            ContextCompat.getMainExecutor(AppContext.context).execute { onLoaded(cover) }
        }
    }

    fun withBlocks(post: Post): Post = PostParser.withBlocks(post)

    fun loadFavorites(): Set<String> = AppContext.context.getSharedPreferences("favorites", 0).getStringSet("posts", emptySet()) ?: emptySet()
    
    fun saveFavorites(value: Set<String>) {
        AppContext.context.getSharedPreferences("favorites", 0).edit().putStringSet("posts", value).apply()
    }
}
