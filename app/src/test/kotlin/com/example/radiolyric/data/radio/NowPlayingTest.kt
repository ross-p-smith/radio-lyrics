package com.example.radiolyric.data.radio

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NowPlayingTest {

    @Test
    fun `bare Artist - Title parses both fields`() {
        val np = NowPlaying.fromDls("Harry Styles - As It Was")
        assertThat(np).isNotNull()
        assertThat(np!!.artist).isEqualTo("Harry Styles")
        assertThat(np.title).isEqualTo("As It Was")
        assertThat(np.source).isEqualTo(NowPlaying.Source.DLS)
        assertThat(np.rawDls).isEqualTo("Harry Styles - As It Was")
    }

    @Test
    fun `Heart prefix and suffix are stripped`() {
        val np = NowPlaying.fromDls("Now playing: Dua Lipa - Houdini on Heart")
        assertThat(np).isNotNull()
        assertThat(np!!.artist).isEqualTo("Dua Lipa")
        assertThat(np.title).isEqualTo("Houdini")
    }

    @Test
    fun `station label without separator yields null`() {
        assertThat(NowPlaying.fromDls("Heart UK")).isNull()
    }

    @Test
    fun `only first dash separator is used`() {
        val np = NowPlaying.fromDls("Foo - Bar - Baz")
        assertThat(np).isNotNull()
        assertThat(np!!.artist).isEqualTo("Foo")
        assertThat(np.title).isEqualTo("Bar - Baz")
    }

    @Test
    fun `empty string yields null`() {
        assertThat(NowPlaying.fromDls("")).isNull()
        assertThat(NowPlaying.fromDls("   ")).isNull()
    }

    @Test
    fun `UTF-8 emoji and non-ASCII characters survive`() {
        val np = NowPlaying.fromDls("Beyoncé - TEXAS HOLD 'EM 🤠")
        assertThat(np).isNotNull()
        assertThat(np!!.artist).isEqualTo("Beyoncé")
        assertThat(np.title).isEqualTo("TEXAS HOLD 'EM 🤠")
    }

    @Test
    fun `case-insensitive Now Playing prefix is stripped`() {
        val np = NowPlaying.fromDls("NOW PLAYING: Sam Smith - Unholy")
        assertThat(np).isNotNull()
        assertThat(np!!.artist).isEqualTo("Sam Smith")
        assertThat(np.title).isEqualTo("Unholy")
    }

    @Test
    fun `empty artist or title yields null`() {
        assertThat(NowPlaying.fromDls(" - Houdini")).isNull()
        assertThat(NowPlaying.fromDls("Dua Lipa - ")).isNull()
    }

    @Test
    fun `Empty sentinel has NONE source`() {
        assertThat(NowPlaying.Empty.source).isEqualTo(NowPlaying.Source.NONE)
        assertThat(NowPlaying.Empty.artist).isNull()
        assertThat(NowPlaying.Empty.title).isNull()
    }
}
