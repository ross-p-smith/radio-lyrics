package com.example.radiolyric.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.radiolyric.data.radio.NowPlaying
import com.example.radiolyric.data.radio.RadioSource
import com.example.radiolyric.data.radio.Station
import com.example.radiolyric.data.radio.Stations
import com.example.radiolyric.playback.MediaControllerProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * UI state for the Now Playing screen.
 *
 * - [stationLabel] is always present (defaulting to "Heart UK") so the screen renders even
 *   before the first DL+ frame arrives.
 * - [artist] / [title] are nullable: render placeholders when they are.
 */
data class NowPlayingUiState(
        val stationLabel: String = Stations.HeartUK.label,
        val artist: String? = null,
        val title: String? = null,
        val source: NowPlaying.Source = NowPlaying.Source.NONE,
)

@HiltViewModel
class NowPlayingViewModel
@Inject
constructor(
        private val radioSource: RadioSource,
        private val controllerProvider: MediaControllerProvider,
) : ViewModel() {

    val uiState: StateFlow<NowPlayingUiState> =
            radioSource.nowPlaying
                    .map { np ->
                        NowPlayingUiState(
                                stationLabel = Stations.HeartUK.label,
                                artist = np.artist,
                                title = np.title,
                                source = np.source,
                        )
                    }
                    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NowPlayingUiState())

    fun tune(station: Station) {
        viewModelScope.launch { runCatching { radioSource.tune(station) } }
    }

    fun play() {
        viewModelScope.launch { runCatching { controllerProvider.controller().play() } }
    }

    fun pause() {
        viewModelScope.launch { runCatching { controllerProvider.controller().pause() } }
    }
}
