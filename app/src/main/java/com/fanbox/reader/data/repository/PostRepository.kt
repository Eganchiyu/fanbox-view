package com.fanbox.reader.data.repository

import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.fanbox.reader.AppContext
import com.fanbox.reader.data.model.Block
import com.fanbox.reader.data.model.Post
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.util.Locale

object PostRepository {

    fun loadPosts(root: DocumentFile, onLoaded: (List<Post>) -> Unit) {
        Thread {
            val datePattern = Regex("^\\d{4}-\\d{2}-\\d{2}[-_].+")
            val direct = root.listFiles().filter { it.isDirectory }
            val folders = if (direct.any { datePattern.matches(it.name.orEmpty()) }) direct 
                          else direct.flatMap { it.listFiles().filter { child -> child.isDirectory } }
            
            val quick = folders.map { folder ->
                val name = folder.name.orEmpty()
                val match = Regex("^(\\d{4}-\\d{2}-\\d{2})[-_](.+)$").find(name)
                Post(folder, match?.groupValues?.get(2) ?: name, match?.groupValues?.get(1) ?: "未知日期", null, emptyList())
            }.sortedByDescending { it.date }
            
            AppContext.context.mainExecutor.execute { onLoaded(quick) }
            
            val complete = quick.map { post -> withBlocks(post) }
            AppContext.context.mainExecutor.execute { onLoaded(complete) }
        }.start()
    }

    fun withBlocks(post: Post): Post {
        val htmlFile = post.folder.listFiles().firstOrNull { 
            it.isFile && it.name?.substringAfterLast('.', "")?.lowercase(Locale.ROOT) in setOf("html", "htm") 
        }
        val blocks = if (htmlFile != null) {
            val parsed = parseHtmlBlocks(post.folder, htmlFile)
            parsed.ifEmpty { fileBlocks(post) }
        } else {
            fileBlocks(post)
        }
        val cover = blocks.filterIsInstance<Block.Image>().firstOrNull()?.file
            ?: blocks.filterIsInstance<Block.Video>().firstOrNull()?.file
        return post.copy(cover = cover, blocks = blocks)
    }

    private fun fileBlocks(post: Post): List<Block> {
        return post.folder.listFiles().filter { it.isFile }
            .sortedWith(compareBy(nullsLast()) { it.name?.lowercase(Locale.ROOT) })
            .map {
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

    private fun kindOf(name: String?): Kind = when (name?.substringAfterLast('.', "")?.lowercase(Locale.ROOT)) {
        "jpg", "jpeg", "png", "webp", "gif", "bmp" -> Kind.IMAGE
        "mp4", "mkv", "webm", "mov", "avi" -> Kind.VIDEO
        "txt", "md", "html", "htm" -> Kind.TEXT
        else -> Kind.OTHER
    }

    private fun readText(file: DocumentFile): String = runCatching {
        AppContext.context.contentResolver.openInputStream(file.uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
    }.getOrElse { "无法读取文本：${it.message}" }

    fun loadFavorites(): Set<String> = AppContext.context.getSharedPreferences("favorites", 0).getStringSet("posts", emptySet()) ?: emptySet()
    
    fun saveFavorites(value: Set<String>) {
        AppContext.context.getSharedPreferences("favorites", 0).edit().putStringSet("posts", value).apply()
    }
}
