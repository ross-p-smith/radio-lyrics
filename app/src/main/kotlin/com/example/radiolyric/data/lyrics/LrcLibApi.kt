package com.example.radiolyric.data.lyrics

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface LrcLibApi {

    @GET("api/search")
    suspend fun search(
            @Query("track_name") track: String,
            @Query("artist_name") artist: String,
    ): List<LrcLibTrack>

    @GET("api/get/{id}") suspend fun getById(@Path("id") id: Long): LrcLibTrack
}
