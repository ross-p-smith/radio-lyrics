package com.example.radiolyric.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.radiolyric.data.lyrics.LyricLine
import com.example.radiolyric.data.lyrics.Lyrics
import com.example.radiolyric.data.lyrics.LyricsRepository
import com.example.radiolyric.data.radio.RadioSource
import com.example.radiolyric.devtools.AppLog as Log
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.min
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch

sealed interface LyricsUiState {
    data object Idle : LyricsUiState
    data object Loading : LyricsUiState
    data class Synced(val lyrics: Lyrics.Synced) : LyricsUiState
    data class Plain(val text: String) : LyricsUiState
    data object None : LyricsUiState
}

data class SongHeading(val title: String, val artist: String?)

@HiltViewModel
class LyricsViewModel
@Inject
constructor(
        private val radioSource: RadioSource,
        private val lyricsRepo: LyricsRepository,
) : ViewModel() {

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<LyricsUiState> =
            radioSource.nowPlaying
                    .distinctUntilChangedBy { it.artist to it.title }
                    .transformLatest { np ->
                        val artist = np.artist
                        val title = np.title
                        if (artist.isNullOrBlank() || title.isNullOrBlank()) {
                            emit(LyricsUiState.Idle)
                            return@transformLatest
                        }
                        emit(LyricsUiState.Loading)
                        emit(
                                when (val lyrics = lyricsRepo.lookup(artist, title)) {
                                    is Lyrics.Synced -> LyricsUiState.Synced(lyrics)
                                    is Lyrics.Plain -> LyricsUiState.Plain(lyrics.text)
                                    Lyrics.None -> LyricsUiState.None
                                },
                        )
                    }
                    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LyricsUiState.Idle)

    val songHeading: StateFlow<SongHeading?> =
            radioSource.nowPlaying
                    .map { np ->
                        val title = np.title?.trim().orEmpty()
                        if (title.isBlank()) {
                            null
                        } else {
                            SongHeading(
                                    title = title,
                                    artist = np.artist?.trim()?.takeIf { it.isNotBlank() },
                            )
                        }
                    }
                    .distinctUntilChanged()
                    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _activeLineIndex = MutableStateFlow(0)
    val activeLineIndex: StateFlow<Int> = _activeLineIndex.asStateFlow()

    private var ticker = viewModelScope.launch { tickPositionForever() }

    private suspend fun tickPositionForever() {
        // Track time from synced-lyrics arrival, then keep forcing progress if the same lyric
        // line stays active for too long.
        uiState.collectLatest { state ->
            if (state !is LyricsUiState.Synced) {
                _currentPositionMs.value = 0L
                _activeLineIndex.value = 0
                return@collectLatest
            }

            val lines = state.lyrics.lines
            if (lines.isEmpty()) {
                _currentPositionMs.value = 0L
                _activeLineIndex.value = 0
                return@collectLatest
            }

            _currentPositionMs.value = 0L
            _activeLineIndex.value = 0
            val start = System.currentTimeMillis()
            var lastLineChangedAt = start

            while (true) {
                val now = System.currentTimeMillis()
                val positionMs = now - start
                _currentPositionMs.value = positionMs

                val candidate = activeLineIndex(lines, positionMs)
                val current = _activeLineIndex.value
                if (candidate != current) {
                    _activeLineIndex.value = candidate
                    lastLineChangedAt = now
                } else if (now - lastLineChangedAt >= LINE_STALL_ADVANCE_MS &&
                                current < lines.lastIndex) {
                    val forced = min(current + 1, lines.lastIndex)
                    _activeLineIndex.value = forced
                    Log.d(
                            TAG,
                            "Forced lyric line advance after ${LINE_STALL_ADVANCE_MS}ms: $current -> $forced",
                    )
                    lastLineChangedAt = now
                }

                delay(POSITION_TICK_MS)
            }
        }
    }

    /** Binary-search the largest i such that lines[i].timeMs <= positionMs. */
    private fun activeLineIndex(lines: List<LyricLine>, positionMs: Long): Int {
        var lo = 0
        var hi = lines.size - 1
        var ans = 0
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (lines[mid].timeMs <= positionMs) {
                ans = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return ans
    }

    private companion object {
        private const val TAG = "LyricsViewModel"
        private const val POSITION_TICK_MS = 200L
        private const val LINE_STALL_ADVANCE_MS = 30_000L
    }
}
