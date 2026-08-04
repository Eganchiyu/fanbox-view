package com.fanbox.reader.data.repository

import androidx.documentfile.provider.DocumentFile
import com.fanbox.reader.AppContext
import com.fanbox.reader.data.model.Block
import com.fanbox.reader.data.model.Post
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.util.Locale

object PostParser {
    internal val bannerPrefs = AppContext.context.getSharedPreferences("banners", 0)
    private val blocksCache = java.util.concurrent.ConcurrentHashMap<String, List<Block>>()

    fun getCachedBlocks(uri: String): List<Block>? = blocksCache[uri]

    fun scanForCover(folder: DocumentFile): DocumentFile? {
        val cached = blocksCache[folder.uri.toString()]
        if (cached != null) return cached.filterIsInstance<Block.Image>().firstOrNull()?.file ?: cached.filterIsInstance<Block.Video>().firstOrNull()?.file

        val htmlFile = folder.listFiles().firstOrNull { 
            it.isFile && it.name?.substringAfterLast('.', "")?.lowercase(Locale.ROOT) in setOf("html", "htm") 
        }
        
        if (htmlFile != null) {
            val html = FileHelper.readText(htmlFile)
            if (html.isNotBlank()) {
                val fileMap = folder.listFiles().filter { it.isFile }.associateBy { it.name ?: "" }
                val doc = Jsoup.parse(html)
                // Fast scan for the first image or video in HTML
                doc.select("img, video, a").forEach { element ->
                    when (element.tagName()) {
                        "img" -> {
                            val src = element.attr("src").ifBlank { element.attr("data-src") }.ifBlank { element.attr("data-original") }
                            FileHelper.resolveFile(src, folder, fileMap)?.let { return it }
                        }
                        "video" -> {
                            val src = element.attr("src").ifBlank { element.selectFirst("source")?.attr("src") ?: "" }
                            FileHelper.resolveFile(src, folder, fileMap)?.let { return it }
                        }
                        "a" -> {
                            val href = element.attr("href")
                            if (FileHelper.isVideoUrl(href)) {
                                FileHelper.resolveFile(href, folder, fileMap)?.let { return it }
                            }
                        }
                    }
                }
            }
        }
        
        return fileBlocks(folder).let { blocks ->
            blocks.filterIsInstance<Block.Image>().firstOrNull()?.file
                ?: blocks.filterIsInstance<Block.Video>().firstOrNull()?.file
        }
    }

    fun withBlocks(post: Post): Post {
        val uriStr = post.folder.uri.toString()
        val cached = blocksCache[uriStr]
        if (cached != null) return post.copy(blocks = cached)

        val htmlFile = post.folder.listFiles().firstOrNull { 
            it.isFile && it.name?.substringAfterLast('.', "")?.lowercase(Locale.ROOT) in setOf("html", "htm") 
        }
        val blocks = if (htmlFile != null) {
            val parsed = parseHtmlBlocks(post.folder, htmlFile)
            parsed.ifEmpty { fileBlocks(post.folder) }
        } else {
            fileBlocks(post.folder)
        }
        
        blocksCache[uriStr] = blocks
        
        val cover = blocks.filterIsInstance<Block.Image>().firstOrNull()?.file
            ?: blocks.filterIsInstance<Block.Video>().firstOrNull()?.file
        
        // Update index if we found a cover during full block scan
        if (cover != null) {
            bannerPrefs.edit().putString(post.folder.uri.toString(), cover.uri.toString()).apply()
        }
        
        return post.copy(cover = cover, blocks = blocks)
    }

    private fun fileBlocks(folder: DocumentFile): List<Block> {
        return folder.listFiles().filter { it.isFile }
            .sortedWith(compareBy(nullsLast()) { it.name?.lowercase(Locale.ROOT) })
            .map {
                when (FileHelper.kindOf(it.name)) {
                    FileHelper.Kind.IMAGE -> Block.Image(it)
                    FileHelper.Kind.VIDEO -> Block.Video(it)
                    FileHelper.Kind.TEXT -> Block.Text(FileHelper.readText(it), isHtml = false)
                    FileHelper.Kind.OTHER -> Block.Unsupported(it.name)
                }
            }
    }

    private fun parseHtmlBlocks(folder: DocumentFile, htmlFile: DocumentFile): List<Block> {
        val html = FileHelper.readText(htmlFile)
        if (html.isBlank()) return emptyList()
        val fileMap = folder.listFiles().filter { it.isFile }.associateBy { it.name ?: "" }
        val doc = Jsoup.parse(html)
        val body = doc.body()
        val blocks = mutableListOf<Block>()
        val textCollector = StringBuilder()

        fun flushText() {
            val text = textCollector.toString().trim()
            if (text.isNotBlank()) blocks.add(Block.Text(text, isHtml = true))
            textCollector.clear()
        }

        fun processNode(node: org.jsoup.nodes.Node) {
            if (node is Element) {
                val tag = node.tagName()
                val href = node.attr("href")

                // Try to resolve as media first
                val file = when {
                    tag == "img" -> {
                        val src = node.attr("src").ifBlank { node.attr("data-src") }.ifBlank { node.attr("data-original") }
                        FileHelper.resolveFile(src, folder, fileMap)
                    }
                    tag == "video" -> {
                        val src = node.attr("src").ifBlank { node.selectFirst("source")?.attr("src") ?: "" }
                        FileHelper.resolveFile(src, folder, fileMap)
                    }
                    tag == "a" && FileHelper.isVideoUrl(href) -> {
                        FileHelper.resolveFile(href, folder, fileMap)
                    }
                    else -> null
                }

                if (file != null) {
                    flushText()
                    if (FileHelper.kindOf(file.name) == FileHelper.Kind.VIDEO) {
                        blocks.add(Block.Video(file))
                    } else {
                        blocks.add(Block.Image(file))
                    }
                    return
                }

                // If not media, check if it's a container for media or just text
                if (node.selectFirst("img, video") != null || node.select("a").any { FileHelper.isVideoUrl(it.attr("href")) }) {
                    // Container with media, recurse into children
                    for (child in node.childNodes()) {
                        processNode(child)
                    }
                } else {
                    // Pure text or simple formatting element
                    if (tag == "br") textCollector.append("<br>")
                    else if (tag == "hr") {
                        flushText()
                        blocks.add(Block.Text("<hr>", isHtml = true))
                    } else {
                        textCollector.append(node.outerHtml())
                    }
                }
            } else {
                // Text node or other node type
                textCollector.append(node.toString())
            }
        }

        for (node in body.childNodes()) {
            processNode(node)
        }
        flushText()
        return blocks
    }
}
