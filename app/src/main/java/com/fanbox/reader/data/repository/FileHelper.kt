package com.fanbox.reader.data.repository

import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.fanbox.reader.AppContext
import java.util.Locale

object FileHelper {
    fun isVideoUrl(url: String?): Boolean {
        val path = url?.trim() ?: return false
        if (path.isBlank()) return false
        val ext = path.substringAfterLast('.', "").substringBefore('?').substringBefore('#').lowercase(Locale.ROOT)
        return ext in setOf("mp4", "mkv", "webm", "mov", "avi", "3gp", "m4v", "ts", "flv", "wmv", "mpg", "mpeg", "vob", "ogv")
    }

    fun resolveFile(src: String?, folder: DocumentFile, fileMap: Map<String, DocumentFile>): DocumentFile? {
        val path = src?.trim() ?: return null
        if (path.isBlank() || path.startsWith("http")) return null // Ignore remote URLs for now
        
        val decoded = Uri.decode(path)
        // Extract filename: handles "path/to/file.ext", "./file.ext", etc.
        val name = decoded.substringAfterLast('/').substringAfterLast('\\').substringBefore('?').substringBefore('#').trim()
        if (name.isBlank()) return null
        
        // Exact match in fileMap (case-sensitive then insensitive)
        return fileMap[name]
            ?: fileMap.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
            // Fallback to findFile if not in map (e.g. newly added files)
            ?: folder.findFile(name)
    }

    enum class Kind { IMAGE, VIDEO, TEXT, OTHER }

    fun kindOf(name: String?): Kind = when (name?.substringAfterLast('.', "")?.lowercase(Locale.ROOT)) {
        "jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif" -> Kind.IMAGE
        "mp4", "mkv", "webm", "mov", "avi", "3gp", "m4v", "ts", "flv", "wmv", "mpg", "mpeg", "vob", "ogv" -> Kind.VIDEO
        "txt", "md", "html", "htm" -> Kind.TEXT
        else -> Kind.OTHER
    }

    fun readText(file: DocumentFile): String = runCatching {
        AppContext.context.contentResolver.openInputStream(file.uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
    }.getOrElse { "无法读取文本：${it.message}" }
}
