package com.example.radiolyric.data.lyrics

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class LrcLibTrack(
        val id: Long,
        val trackName: String,
        val artistName: String,
        val albumName: String? = null,
        val duration: Double? = null,
        val instrumental: Boolean = false,
        val plainLyrics: String? = null,
        val syncedLyrics: String? = null,
)
