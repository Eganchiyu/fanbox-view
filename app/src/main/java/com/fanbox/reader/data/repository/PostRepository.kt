package com.fanbox.reader.data.repository

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.fanbox.reader.AppContext
import com.fanbox.reader.data.model.Post
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

object PostRepository {

    private val DATE_PATTERN = Regex("^\\d{4}-\\d{2}-\\d{2}[-_].+")
    private val NAME_PARSER = Regex("^(\\d{4}-\\d{2}-\\d{2})[-_](.+)$")

    fun loadPosts(root: DocumentFile): Flow<List<Post>> = flow {
        val direct = root.listFiles().filter { it.isDirectory }
        val folders = if (direct.any { DATE_PATTERN.matches(it.name.orEmpty()) }) direct
        else direct.flatMap { it.listFiles().filter { child -> child.isDirectory } }

        val allPosts = mutableListOf<Post>()

        folders.forEach { folder ->
            val name = folder.name.orEmpty()
            val match = NAME_PARSER.find(name)
            val cachedUri = PostParser.bannerPrefs.getString(folder.uri.toString(), null)
            val cover = cachedUri?.let { DocumentFile.fromSingleUri(AppContext.context, Uri.parse(it)) }
            val post = Post(
                folder = folder,
                title = match?.groupValues?.get(2) ?: name,
                date = match?.groupValues?.get(1) ?: "未知日期",
                cover = cover,
                blocks = emptyList()
            )
            allPosts.add(post)

            // 每发现 20 篇或加载完最后几篇，更新一次 UI
            if (allPosts.size % 20 == 0 || allPosts.size == folders.size) {
                emit(allPosts.sortedByDescending { it.date })
            }
        }
    }.flowOn(Dispatchers.IO)

    suspend fun loadCover(post: Post): DocumentFile? = withContext(Dispatchers.IO) {
        val cover = PostParser.scanForCover(post.folder)
        if (cover != null) {
            PostParser.bannerPrefs.edit().putString(post.folder.uri.toString(), cover.uri.toString()).apply()
        }
        cover
    }

    suspend fun withBlocks(post: Post): Post = withContext(Dispatchers.IO) {
        PostParser.withBlocks(post)
    }

    fun loadFavorites(): Set<String> =
        AppContext.context.getSharedPreferences("favorites", Context.MODE_PRIVATE)
            .getStringSet("posts", emptySet()) ?: emptySet()

    fun saveFavorites(value: Set<String>) {
        AppContext.context.getSharedPreferences("favorites", Context.MODE_PRIVATE)
            .edit().putStringSet("posts", value).apply()
    }
}
