package com.example.radiolyric.bridge

import com.example.radiolyric.data.radio.NowPlaying
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import org.junit.Test

class DabzMetadataMapperTest {

    private val fixedNow: () -> Instant = { Instant.parse("2026-05-07T12:00:00Z") }

    @Test
    fun `DLPLUS fast path when artist and title both present`() {
        val r = mapToNowPlaying(artist = "Adele", title = "Hello", displaySubtitle = null, now = fixedNow)
        assertThat(r.path).isEqualTo(DabzMappingPath.DLPLUS)
        assertThat(r.nowPlaying.artist).isEqualTo("Adele")
        assertThat(r.nowPlaying.title).isEqualTo("Hello")
        assertThat(r.nowPlaying.source).isEqualTo(NowPlaying.Source.DLPLUS)
    }

    @Test
    fun `DLS fallback when only title present and contains separator`() {
        val r = mapToNowPlaying(artist = null, title = "Adele - Hello", displaySubtitle = null, now = fixedNow)
        assertThat(r.path).isEqualTo(DabzMappingPath.DLS)
        assertThat(r.nowPlaying.artist).isEqualTo("Adele")
        assertThat(r.nowPlaying.title).isEqualTo("Hello")
        assertThat(r.nowPlaying.source).isEqualTo(NowPlaying.Source.DLS)
    }

    @Test
    fun `DLS prefix Now Playing on Heart parses through fromDls`() {
        val r =
                mapToNowPlaying(
                        artist = null,
                        title = null,
                        displaySubtitle = "Now playing: Adele - Hello on Heart",
                        now = fixedNow,
                )
        assertThat(r.path).isEqualTo(DabzMappingPath.DLS)
        assertThat(r.nowPlaying.artist).isEqualTo("Adele")
        assertThat(r.nowPlaying.title).isEqualTo("Hello")
    }

    @Test
    fun `blank artist with non-separated title yields EMPTY`() {
        val r = mapToNowPlaying(artist = "  ", title = "JustATitle", displaySubtitle = null, now = fixedNow)
        assertThat(r.path).isEqualTo(DabzMappingPath.EMPTY)
        assertThat(r.nowPlaying).isEqualTo(NowPlaying.Empty)
    }

    @Test
    fun `both blank yields EMPTY`() {
        val r = mapToNowPlaying(artist = "", title = "  ", displaySubtitle = null, now = fixedNow)
        assertThat(r.path).isEqualTo(DabzMappingPath.EMPTY)
        assertThat(r.nowPlaying).isEqualTo(NowPlaying.Empty)
    }

    @Test
    fun `null inputs yield EMPTY`() {
        val r = mapToNowPlaying(artist = null, title = null, displaySubtitle = null, now = fixedNow)
        assertThat(r.path).isEqualTo(DabzMappingPath.EMPTY)
        assertThat(r.nowPlaying).isEqualTo(NowPlaying.Empty)
    }

    @Test
    fun `display subtitle with separator parses through fromDls into split fields`() {
        val r =
                mapToNowPlaying(
                        artist = null,
                        title = null,
                        displaySubtitle = "Sam Smith - Unholy",
                        now = fixedNow,
                )
        assertThat(r.path).isEqualTo(DabzMappingPath.DLS)
        assertThat(r.nowPlaying.artist).isEqualTo("Sam Smith")
        assertThat(r.nowPlaying.title).isEqualTo("Unholy")
    }
}
