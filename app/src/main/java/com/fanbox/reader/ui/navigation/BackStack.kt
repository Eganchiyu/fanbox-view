package com.fanbox.reader.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object BackStack {
    private val states = ArrayDeque<Screen>()
    var current by mutableStateOf<Screen>(Screen.Home)
        private set

    val stack: List<Screen> get() = states + current

    fun open(screen: Screen) {
        states.addLast(current)
        current = screen
    }

    fun replace(screen: Screen) {
        current = screen
    }

    fun pop(): Boolean {
        if (states.isEmpty()) return false
        current = states.removeLast()
        return true
    }
}
