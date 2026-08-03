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

    fun scanForCover(folder: DocumentFile): DocumentFile? {
        val htmlFile = folder.listFiles().firstOrNull { 
            it.isFile && it.name?.substringAfterLast('.', "")?.lowercase(Locale.ROOT) in setOf("html", "htm") 
        }
        val blocks = if (htmlFile != null) {
            parseHtmlBlocks(folder, htmlFile)
        } else {
            fileBlocks(folder)
        }
        return blocks.filterIsInstance<Block.Image>().firstOrNull()?.file
            ?: blocks.filterIsInstance<Block.Video>().firstOrNull()?.file
    }

    fun withBlocks(post: Post): Post {
        val htmlFile = post.folder.listFiles().firstOrNull { 
            it.isFile && it.name?.substringAfterLast('.', "")?.lowercase(Locale.ROOT) in setOf("html", "htm") 
        }
        val blocks = if (htmlFile != null) {
            val parsed = parseHtmlBlocks(post.folder, htmlFile)
            parsed.ifEmpty { fileBlocks(post.folder) }
        } else {
            fileBlocks(post.folder)
        }
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

        fun addMedia(element: Element) {
            when (element.tagName()) {
                "img" -> {
                    val src = element.attr("src")
                        .ifBlank { element.attr("data-src") }
                        .ifBlank { element.attr("data-original") }
                    FileHelper.resolveFile(src, folder, fileMap)?.let { blocks.add(Block.Image(it)) }
                }
                "video" -> {
                    val src = element.attr("src").ifBlank { element.selectFirst("source")?.attr("src") ?: "" }
                    FileHelper.resolveFile(src, folder, fileMap)?.let { blocks.add(Block.Video(it)) }
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
                tag == "a" && FileHelper.isVideoUrl(element.attr("href")) -> {
                    FileHelper.resolveFile(element.attr("href"), folder, fileMap)?.let { blocks.add(Block.Video(it)) }
                    for (child in element.children()) {
                        if (child.tagName() == "img") addMedia(child)
                    }
                }
                tag == "br" -> blocks.add(Block.Text("<br>", isHtml = true))
                tag == "hr" -> blocks.add(Block.Text("<hr>", isHtml = true))
                element.selectFirst("img, video") == null && !FileHelper.isVideoUrl(element.attr("href")) && element.select("a").none { FileHelper.isVideoUrl(it.attr("href")) } -> {
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
                            node is Element && (node.selectFirst("img, video") != null || (node.tagName() == "a" && FileHelper.isVideoUrl(node.attr("href")))) -> {
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
                node is Element && node.tagName() == "a" && FileHelper.isVideoUrl(node.attr("href")) -> {
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
}
