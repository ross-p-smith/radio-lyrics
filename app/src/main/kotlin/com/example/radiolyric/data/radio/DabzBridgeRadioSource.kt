package com.example.radiolyric.data.radio

import android.content.Context
import android.support.v4.media.session.PlaybackStateCompat
import com.example.radiolyric.bridge.DabzMappingPath
import com.example.radiolyric.bridge.DabzMediaBrowserClient
import com.example.radiolyric.bridge.DabzSnapshot
import com.example.radiolyric.bridge.toNowPlaying
import com.example.radiolyric.devtools.AppLog as Log
import dagger.hilt.android.qualifiers.ApplicationContext
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
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * [RadioSource] implementation that consumes DAB-Z's exported `MediaBrowserService` via
 * [DabzMediaBrowserClient] and projects its [DabzSnapshot] stream onto our existing
 * [NowPlaying] / [RadioState] surface.
 *
 * This source is **read-only**: DAB-Z owns the tuner and audio render, so:
 * - [audio] is `emptyFlow()` — the Compose UI does not render PCM in DAB-Z mode.
 * - [tune] returns `Result.failure(UnsupportedOperationException)` — station selection happens
 *   in DAB-Z's own UI (see WI-01 in the planning log for the picker UX follow-up).
 * - We never call `requestAudioFocus`; `PlaybackService` is hard-gated to skip session/player
 *   construction in DAB-Z mode (DD-06 in the planning log).
 *
 * Station-change reset (DR-04): when `playbackState.activeQueueItemId` changes we emit
 * `NowPlaying.Empty` first so downstream `LyricsRepository` collectors observe a clean reset and
 * discard the previous lyrics. Additionally, if no non-blank artist has been seen for >30 s we
 * also emit `NowPlaying.Empty` to cover DAB-Z fallbacks where DL+ disappears across a station
 * hop without a queue-id change.
 */
@Singleton
class DabzBridgeRadioSource
@Inject
constructor(
        @ApplicationContext private val ctx: Context,
) : RealRadioSourceProvider {

    private val _state = MutableStateFlow<RadioState>(RadioState.Idle)
    override val state: StateFlow<RadioState> = _state.asStateFlow()

    private val _nowPlaying = MutableStateFlow(NowPlaying.Empty)
    override val nowPlaying: StateFlow<NowPlaying> = _nowPlaying.asStateFlow()

    /** DAB-Z owns the audio path; we do not surface PCM in this mode. */
    override val audio: Flow<ByteArray> = emptyFlow()

    private val openMutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val client = DabzMediaBrowserClient(ctx)

    private var collectorJob: Job? = null
    private var staleArtistJob: Job? = null
    private var lastQueueItemId: Long = PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN
    private var hasSeenAnyMetadata = false

    override suspend fun open(): Result<Unit> =
            openMutex.withLock {
                if (collectorJob?.isActive == true) return Result.success(Unit)
                Log.i(TAG, "open(): starting DAB-Z MediaBrowser bridge collector")
                collectorJob =
                        scope.launch {
                            client.observe().collect { snapshot -> handleSnapshot(snapshot) }
                        }
                Result.success(Unit)
            }

    override suspend fun tune(station: Station): Result<Unit> {
        Log.i(TAG, "tune(${station.label}) ignored \u2014 DAB-Z owns tuner; switch stations in DAB-Z UI")
        return Result.failure(
                UnsupportedOperationException(
                        "DAB-Z owns tuner; switch stations in DAB-Z UI",
                ),
        )
    }

    override suspend fun close() {
        openMutex.withLock {
            Log.i(TAG, "close(): cancelling DAB-Z bridge collector")
            collectorJob?.cancel()
            collectorJob = null
            staleArtistJob?.cancel()
            staleArtistJob = null
            scope.cancel()
        }
    }

    private fun handleSnapshot(snapshot: DabzSnapshot) {
        // 1. Update RadioState from playback state.
        _state.value =
                when (snapshot.playback?.state) {
                    PlaybackStateCompat.STATE_PLAYING -> RadioState.Playing(DabzExternalStation)
                    PlaybackStateCompat.STATE_BUFFERING,
                    PlaybackStateCompat.STATE_CONNECTING -> RadioState.Tuning
                    null -> _state.value
                    else -> RadioState.Idle
                }

        // 2. Detect station change via activeQueueItemId discontinuity.
        val newQueueItemId =
                snapshot.playback?.activeQueueItemId
                        ?: PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN
        if (
                hasSeenAnyMetadata &&
                        newQueueItemId != lastQueueItemId &&
                        newQueueItemId != PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN
        ) {
            if (_nowPlaying.value != NowPlaying.Empty) {
                Log.i(
                        TAG,
                        "Station change detected (queueItemId $lastQueueItemId \u2192 $newQueueItemId); resetting NowPlaying",
                )
                _nowPlaying.value = NowPlaying.Empty
            }
        }
        lastQueueItemId = newQueueItemId
        hasSeenAnyMetadata = true

        // 3. Map metadata and emit.
        val result = snapshot.metadata.toNowPlaying()
        _nowPlaying.value = result.nowPlaying

        // 4. Reset the 30 s stale-artist watchdog whenever we see a non-empty mapping.
        if (result.path != DabzMappingPath.EMPTY) {
            staleArtistJob?.cancel()
            staleArtistJob =
                    scope.launch {
                        delay(STALE_ARTIST_TIMEOUT_MS)
                        if (_nowPlaying.value.artist == result.nowPlaying.artist) {
                            // No newer metadata arrived; treat as cross-station drift.
                            Log.i(
                                    TAG,
                                    "Stale-artist watchdog fired (no new metadata in ${STALE_ARTIST_TIMEOUT_MS}ms); resetting",
                            )
                            _nowPlaying.value = NowPlaying.Empty
                        }
                    }
        }
    }

    private companion object {
        private const val TAG = "DabzBridgeRadioSource"
        private const val STALE_ARTIST_TIMEOUT_MS = 30_000L
    }
}

/**
 * Sentinel station used to satisfy [RadioState.Playing] when the upstream is DAB-Z and we have no
 * tuner / station id of our own. WI-07 tracks lifting this restriction once we wire a `station:
 * String?` field through `NowPlaying` from `MediaController.queueTitle` / `playbackState.extras`.
 */
private val DabzExternalStation: Station =
        Station(sid = 0, eid = 0, frequencyKhz = 0, label = "DAB-Z")
