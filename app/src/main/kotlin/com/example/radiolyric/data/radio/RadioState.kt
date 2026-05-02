package com.example.radiolyric.data.radio

/** Lifecycle state of a [RadioSource]. */
sealed interface RadioState {
    data object Idle : RadioState
    data object Tuning : RadioState
    data class Playing(val station: Station) : RadioState
    data class Error(val message: String) : RadioState
}
