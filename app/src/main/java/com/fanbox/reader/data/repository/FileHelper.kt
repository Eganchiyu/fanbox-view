package com.fanbox.reader.data.repository

import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.fanbox.reader.AppContext
import java.util.Locale

object FileHelper {
    fun isVideoUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val ext = url.substringAfterLast('.', "").substringBefore('?').substringBefore('#').lowercase(Locale.ROOT)
        return ext in setOf("mp4", "mkv", "webm", "mov", "avi", "3gp", "m4v", "ts")
    }

    fun resolveFile(src: String?, folder: DocumentFile, fileMap: Map<String, DocumentFile>): DocumentFile? {
        if (src.isNullOrBlank()) return null
        val decoded = Uri.decode(src)
        val name = decoded.substringAfterLast('/').substringAfterLast('\\').substringBefore('?').substringBefore('#')
        if (name.isBlank()) return null
        return fileMap[name]
            ?: fileMap.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
            ?: folder.findFile(name)
    }

    enum class Kind { IMAGE, VIDEO, TEXT, OTHER }

    fun kindOf(name: String?): Kind = when (name?.substringAfterLast('.', "")?.lowercase(Locale.ROOT)) {
        "jpg", "jpeg", "png", "webp", "gif", "bmp" -> Kind.IMAGE
        "mp4", "mkv", "webm", "mov", "avi" -> Kind.VIDEO
        "txt", "md", "html", "htm" -> Kind.TEXT
        else -> Kind.OTHER
    }

    fun readText(file: DocumentFile): String = runCatching {
        AppContext.context.contentResolver.openInputStream(file.uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
    }.getOrElse { "无法读取文本：${it.message}" }
}
