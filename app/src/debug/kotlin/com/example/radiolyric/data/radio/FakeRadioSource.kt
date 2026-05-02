package com.example.radiolyric.data.radio

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Debug-only [RadioSource] that emits a scripted timeline against bundled fixtures.
 *
 * - `tune()` cancels any in-flight script, then walks [SAMPLE_TIMELINE] (or a JSONL file loaded via
 * [JsonlScriptedSource]) emitting one [NowPlaying] per entry on its own scope.
 * - `audio` loops bytes from `assets/fixtures/silence-2s.pcm` so the downstream `AudioTrack`
 * pipeline (Phase 5) can be exercised without real DAB hardware.
 */
@Singleton
class FakeRadioSource
@Inject
constructor(
        @ApplicationContext private val context: Context,
) : RealRadioSourceProvider {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow<RadioState>(RadioState.Idle)
    override val state: StateFlow<RadioState> = _state.asStateFlow()

    private val _nowPlaying = MutableStateFlow(NowPlaying.Empty)
    override val nowPlaying: StateFlow<NowPlaying> = _nowPlaying.asStateFlow()

    private var scriptJob: Job? = null
    private var script: List<ScriptEntry> = SAMPLE_TIMELINE

    override val audio: Flow<ByteArray> = flow {
        val bytes = context.assets.open("fixtures/silence-2s.pcm").use { it.readBytes() }
        // Loop forever; cancellation propagates through the suspending `emit` as
        // CancellationException.
        while (true) {
            emit(bytes)
        }
    }

    /** Replace the default [SAMPLE_TIMELINE] with a JSONL-driven script. */
    fun useScript(entries: List<ScriptEntry>) {
        script = entries
    }

    override suspend fun open(): Result<Unit> {
        _state.value = RadioState.Idle
        return Result.success(Unit)
    }

    override suspend fun tune(station: Station): Result<Unit> {
        scriptJob?.cancel()
        _state.value = RadioState.Tuning
        _state.value = RadioState.Playing(station)
        scriptJob =
                scope.launch {
                    for (entry in script) {
                        if (!isActive) break
                        _nowPlaying.value =
                                NowPlaying(
                                        artist = entry.artist,
                                        title = entry.title,
                                        rawDls = "${entry.artist} - ${entry.title}",
                                        source = NowPlaying.Source.FAKE,
                                        timestamp = Instant.now(),
                                )
                        delay(entry.holdMillis)
                    }
                }
        return Result.success(Unit)
    }

    override suspend fun close() {
        scriptJob?.cancel()
        scope.cancel()
        _state.value = RadioState.Idle
    }

    /** One scripted now-playing event. */
    data class ScriptEntry(
            val artist: String,
            val title: String,
            val holdMillis: Long = 60_000L,
    )

    companion object {
        /** Default timeline used when no JSONL script is loaded. ~4 minute rotation. */
        val SAMPLE_TIMELINE: List<ScriptEntry> =
                listOf(
                        ScriptEntry("Harry Styles", "As It Was"),
                        ScriptEntry("Dua Lipa", "Houdini"),
                        ScriptEntry("Sam Smith", "Unholy"),
                        ScriptEntry("Olivia Rodrigo", "Vampire"),
                )
    }

    /**
     * Companion factory for replay from captured DL+ fixtures.
     *
     * Each line is a JSON object with `artist`, `title`, and (optional) `ts` (epoch millis).
     * `holdMillis` between entries is derived from `ts` deltas, defaulting to 60 s.
     */
    object JsonlScriptedSource {
        fun load(file: File): List<ScriptEntry> {
            val raw = file.readLines().filter { it.isNotBlank() }
            return parse(raw)
        }

        fun parse(lines: List<String>): List<ScriptEntry> {
            val parsed =
                    lines.map { line ->
                        val obj = JSONObject(line)
                        Triple(
                                obj.optString("artist", ""),
                                obj.optString("title", ""),
                                obj.optLong("ts", -1L),
                        )
                    }
            return parsed.mapIndexed { i, (artist, title, ts) ->
                val nextTs = parsed.getOrNull(i + 1)?.third ?: -1L
                val hold =
                        if (ts >= 0 && nextTs >= 0) (nextTs - ts).coerceAtLeast(1_000L) else 60_000L
                ScriptEntry(artist = artist, title = title, holdMillis = hold)
            }
        }
    }
}
