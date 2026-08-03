package com.fanbox.reader.ui.navigation

import android.net.Uri

sealed interface Screen {
    data object Home : Screen
    data class Detail(val index: Int) : Screen
    data class Fullscreen(val images: List<Uri>, val index: Int) : Screen
    data class FullscreenVideo(val uri: Uri, val position: Long = 0L) : Screen
}
