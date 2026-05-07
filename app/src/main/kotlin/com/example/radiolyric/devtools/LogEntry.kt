package com.example.radiolyric.devtools

/** A single log line captured by [AppLog] for in-app inspection. */
data class LogEntry(
        val timestampMs: Long,
        val level: Level,
        val tag: String,
        val message: String,
        val throwable: String? = null,
) {
    enum class Level { D, I, W, E }
}
