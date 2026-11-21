package com.codejam.taptation.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** In-memory list of the most recent taps, newest first. Lost when the process dies. */
object TapLog {
    const val CAPACITY = 20

    private val _entries = MutableStateFlow<List<TappedNotification>>(emptyList())
    val entries: StateFlow<List<TappedNotification>> = _entries.asStateFlow()

    fun record(notification: TappedNotification) {
        _entries.update { current -> (listOf(notification) + current).take(CAPACITY) }
    }
}
