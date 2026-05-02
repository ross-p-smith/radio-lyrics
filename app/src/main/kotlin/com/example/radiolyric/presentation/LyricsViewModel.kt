package com.example.radiolyric.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.radiolyric.data.lyrics.Lyrics
import com.example.radiolyric.data.lyrics.LyricsRepository
import com.example.radiolyric.data.radio.RadioSource
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flow
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

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private var ticker = viewModelScope.launch { tickPositionForever() }

    private suspend fun tickPositionForever() {
        // Approximate "playback clock" tied to the moment the current track entered playing
        // state; resets each time the artist+title changes. This is an MVP — replace with the
        // MediaController position once the audio path reports timestamps.
        radioSource.nowPlaying.distinctUntilChangedBy { it.artist to it.title }.collect {
            _currentPositionMs.value = 0L
            val start = System.currentTimeMillis()
            while (true) {
                _currentPositionMs.value = System.currentTimeMillis() - start
                delay(POSITION_TICK_MS)
            }
        }
    }

    private companion object {
        private const val POSITION_TICK_MS = 200L
    }
}
