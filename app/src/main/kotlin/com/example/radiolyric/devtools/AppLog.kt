package com.example.radiolyric.devtools

import android.util.Log
import kotlinx.coroutines.flow.StateFlow

/**
 * Drop-in replacement for [android.util.Log] that ALSO mirrors every emission into a bounded
 * ring buffer for the in-app `DevLogScreen` (debug builds only).
 *
 * Signature parity with `android.util.Log` is intentional so call-site migration is a pure
 * import swap. Behaviour:
 * - Always forwards to `android.util.Log.{d,i,w,e}` (so `adb logcat` still works).
 * - Always appends a [LogEntry] to the ring buffer (release builds keep the buffer too — it's
 *   small and harmless; the screen consuming it is debug-only).
 *
 * Use [recent] from Compose with `collectAsStateWithLifecycle()`.
 */
object AppLog {

    private const val DEFAULT_CAPACITY = 500

    private val buffer = LogRingBuffer(DEFAULT_CAPACITY)

    /** Snapshot of the most recent log entries (oldest first), capped at 500. */
    val recent: StateFlow<List<LogEntry>> = buffer.snapshot

    fun d(tag: String, msg: String): Int {
        record(LogEntry.Level.D, tag, msg, null)
        return Log.d(tag, msg)
    }

    fun d(tag: String, msg: String, tr: Throwable?): Int {
        record(LogEntry.Level.D, tag, msg, tr)
        return if (tr == null) Log.d(tag, msg) else Log.d(tag, msg, tr)
    }

    fun i(tag: String, msg: String): Int {
        record(LogEntry.Level.I, tag, msg, null)
        return Log.i(tag, msg)
    }

    fun i(tag: String, msg: String, tr: Throwable?): Int {
        record(LogEntry.Level.I, tag, msg, tr)
        return if (tr == null) Log.i(tag, msg) else Log.i(tag, msg, tr)
    }

    fun w(tag: String, msg: String): Int {
        record(LogEntry.Level.W, tag, msg, null)
        return Log.w(tag, msg)
    }

    fun w(tag: String, msg: String, tr: Throwable?): Int {
        record(LogEntry.Level.W, tag, msg, tr)
        return if (tr == null) Log.w(tag, msg) else Log.w(tag, msg, tr)
    }

    fun e(tag: String, msg: String): Int {
        record(LogEntry.Level.E, tag, msg, null)
        return Log.e(tag, msg)
    }

    fun e(tag: String, msg: String, tr: Throwable?): Int {
        record(LogEntry.Level.E, tag, msg, tr)
        return if (tr == null) Log.e(tag, msg) else Log.e(tag, msg, tr)
    }

    fun clear() = buffer.clear()

    private fun record(level: LogEntry.Level, tag: String, msg: String, tr: Throwable?) {
        buffer.add(
                LogEntry(
                        timestampMs = System.currentTimeMillis(),
                        level = level,
                        tag = tag,
                        message = msg,
                        throwable = tr?.stackTraceToString(),
                ),
        )
    }
}
