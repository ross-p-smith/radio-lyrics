package com.example.radiolyric.bridge

import com.example.radiolyric.data.radio.NowPlaying
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import org.junit.Test

class DabzMetadataMapperTest {

    private val fixedNow: () -> Instant = { Instant.parse("2026-05-07T12:00:00Z") }

    @Test
    fun `DLPLUS fast path when artist and title both present`() {
        val r =
                mapToNowPlaying(
                        artist = "Adele",
                        title = "Hello",
                        displaySubtitle = null,
                        now = fixedNow
                )
        assertThat(r.path).isEqualTo(DabzMappingPath.DLPLUS)
        assertThat(r.nowPlaying.artist).isEqualTo("Adele")
        assertThat(r.nowPlaying.title).isEqualTo("Hello")
        assertThat(r.nowPlaying.source).isEqualTo(NowPlaying.Source.DLPLUS)
    }

    @Test
    fun `DLS fallback when only title present and contains separator`() {
        val r =
                mapToNowPlaying(
                        artist = null,
                        title = "Adele - Hello",
                        displaySubtitle = null,
                        now = fixedNow
                )
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
        val r =
                mapToNowPlaying(
                        artist = "  ",
                        title = "JustATitle",
                        displaySubtitle = null,
                        now = fixedNow
                )
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

    @Test
    fun `DAB-Z On Air Now on title pattern overrides station-name ARTIST field`() {
        // Live capture from Mekede DUDU7 (May 2026): DAB-Z packs the real artist+title inside
        // the TITLE field while ARTIST holds the station name. Must ignore ARTIST and parse TITLE.
        val r =
                mapToNowPlaying(
                        artist = "Heart UK",
                        title = "On Air Now on Heart UK: Noah Kahan with Stick Season",
                        displaySubtitle = null,
                        now = fixedNow,
                )
        assertThat(r.path).isEqualTo(DabzMappingPath.DLPLUS)
        assertThat(r.nowPlaying.artist).isEqualTo("Noah Kahan")
        assertThat(r.nowPlaying.title).isEqualTo("Stick Season")
    }

    @Test
    fun `DAB-Z On Air Now on title preserves multi-artist 'and' separator inside artist part`() {
        val r =
                mapToNowPlaying(
                        artist = "Heart UK",
                        title = "On Air Now on Heart UK: Alex Warren and Meghan Trainor with Ordinary",
                        displaySubtitle = null,
                        now = fixedNow,
                )
        assertThat(r.path).isEqualTo(DabzMappingPath.DLPLUS)
        assertThat(r.nowPlaying.artist).isEqualTo("Alex Warren and Meghan Trainor")
        assertThat(r.nowPlaying.title).isEqualTo("Ordinary")
    }

    @Test
    fun `DAB-Z DJ-only payload (no 'with') yields EMPTY`() {
        // Between songs DAB-Z publishes the DJ name only — no `with` separator. Must not be
        // surfaced as a song or downstream lyrics queries will be polluted.
        val r =
                mapToNowPlaying(
                        artist = "Heart UK",
                        title = "On Air Now on Heart UK: Ben Atkinson",
                        displaySubtitle = null,
                        now = fixedNow,
                )
        assertThat(r.path).isEqualTo(DabzMappingPath.EMPTY)
    }

    @Test
    fun `DAB-Z station slogan title yields EMPTY (not a song)`() {
        // Live capture: "Heart UK - turn up the feel good!" is a station slogan, not a song.
        // Detect by `<ARTIST> -` prefix where ARTIST is the station name.
        val r =
                mapToNowPlaying(
                        artist = "Heart UK",
                        title = "Heart UK - turn up the feel good!",
                        displaySubtitle = null,
                        now = fixedNow,
                )
        assertThat(r.path).isEqualTo(DabzMappingPath.EMPTY)
    }

    @Test
    fun `DAB-Z bare 'Now on' prefix without payload yields EMPTY`() {
        val r =
                mapToNowPlaying(
                        artist = "Heart UK",
                        title = "Now on Heart UK:",
                        displaySubtitle = null,
                        now = fixedNow,
                )
        assertThat(r.path).isEqualTo(DabzMappingPath.EMPTY)
    }
}
