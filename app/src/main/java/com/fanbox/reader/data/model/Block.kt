package com.fanbox.reader.data.model

import androidx.documentfile.provider.DocumentFile

sealed interface Block {
    data class Text(val content: String, val isHtml: Boolean) : Block
    data class Image(val file: DocumentFile) : Block
    data class Video(val file: DocumentFile) : Block
    data class Unsupported(val name: String?) : Block
}
