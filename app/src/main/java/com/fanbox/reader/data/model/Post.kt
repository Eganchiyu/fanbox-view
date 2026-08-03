package com.fanbox.reader.data.model

import androidx.documentfile.provider.DocumentFile

data class Post(
    val folder: DocumentFile,
    val title: String,
    val date: String,
    val cover: DocumentFile?,
    val blocks: List<Block>
)
