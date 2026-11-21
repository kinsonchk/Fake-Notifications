package com.codejam.taptation.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class TapEntry(val packageName: String, val timestampMillis: Long)

/** In-memory list of the most recent taps, newest first. Lost when the process dies. */
object TapLog {
    const val CAPACITY = 20

    private val _entries = MutableStateFlow<List<TapEntry>>(emptyList())
    val entries: StateFlow<List<TapEntry>> = _entries.asStateFlow()

    fun record(packageName: String, timestampMillis: Long = System.currentTimeMillis()) {
        _entries.update { current ->
            (listOf(TapEntry(packageName, timestampMillis)) + current).take(CAPACITY)
        }
    }
}
