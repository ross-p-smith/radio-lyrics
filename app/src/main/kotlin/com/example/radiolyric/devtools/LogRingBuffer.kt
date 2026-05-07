package com.example.radiolyric.devtools

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Bounded ring buffer of [LogEntry] with a [StateFlow] view for Compose consumers.
 *
 * Thread-safe via a single [ReentrantLock]. Capacity is fixed at construction time. When the
 * buffer is full, the oldest entry is evicted to make room for the newest.
 *
 * This class is JVM-only on purpose so it can be unit-tested without Robolectric.
 */
internal class LogRingBuffer(private val capacity: Int) {

    init {
        require(capacity > 0) { "capacity must be > 0 (got $capacity)" }
    }

    private val lock = ReentrantLock()
    private val entries: ArrayDeque<LogEntry> = ArrayDeque(capacity)
    private val _snapshot = MutableStateFlow<List<LogEntry>>(emptyList())

    /** Snapshot of the buffer in insertion order (oldest first). Updated on every [add]. */
    val snapshot: StateFlow<List<LogEntry>> = _snapshot.asStateFlow()

    fun add(entry: LogEntry) {
        lock.withLock {
            if (entries.size >= capacity) entries.removeFirst()
            entries.addLast(entry)
            _snapshot.value = entries.toList()
        }
    }

    fun clear() {
        lock.withLock {
            entries.clear()
            _snapshot.value = emptyList()
        }
    }

    /** Test-only inspection. */
    internal fun size(): Int = lock.withLock { entries.size }
}
