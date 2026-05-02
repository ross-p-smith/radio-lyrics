package com.example.radiolyric.data.lyrics

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LrcParserTest {

    private val parser = LrcParser()

    @Test fun emptyInputYieldsEmptyList() {
        assertThat(parser.parse("")).isEmpty()
        assertThat(parser.parse("   \n   ")).isEmpty()
    }

    @Test fun singleTimestampLine() {
        val input = "[01:23.45]Hello world"
        val lines = parser.parse(input)
        assertThat(lines).hasSize(1)
        assertThat(lines[0]).isEqualTo(LyricLine(83_450L, "Hello world"))
    }

    @Test fun multiTimestampLineFansOut() {
        val input = "[00:10.00][00:20.00][00:30.00]Repeating chorus"
        val lines = parser.parse(input)
        assertThat(lines).hasSize(3)
        assertThat(lines.map { it.timeMs }).containsExactly(10_000L, 20_000L, 30_000L).inOrder()
        assertThat(lines.map { it.text }).containsExactly(
                "Repeating chorus",
                "Repeating chorus",
                "Repeating chorus",
        )
    }

    @Test fun threeDigitFraction() {
        val lines = parser.parse("[00:01.234]Foo")
        assertThat(lines.first().timeMs).isEqualTo(1_234L)
    }

    @Test fun twoDigitFraction() {
        val lines = parser.parse("[00:01.23]Foo")
        assertThat(lines.first().timeMs).isEqualTo(1_230L)
    }

    @Test fun colonSeparatorFraction() {
        val lines = parser.parse("[00:01:23]Foo")
        assertThat(lines.first().timeMs).isEqualTo(1_230L)
    }

    @Test fun metadataTagsAreIgnored() {
        val input =
                """
            [ar:Harry Styles]
            [ti:As It Was]
            [00:01.00]Holding me back
            """.trimIndent()
        val lines = parser.parse(input)
        assertThat(lines).hasSize(1)
        assertThat(lines[0].text).isEqualTo("Holding me back")
    }

    @Test fun blankLyricBodyEmitsEmptyText() {
        val lines = parser.parse("[00:05.00]")
        assertThat(lines).hasSize(1)
        assertThat(lines[0]).isEqualTo(LyricLine(5_000L, ""))
    }

    @Test fun outOfOrderInputIsSortedAscending() {
        val input =
                """
            [00:30.00]Third
            [00:10.00]First
            [00:20.00]Second
            """.trimIndent()
        val lines = parser.parse(input)
        assertThat(lines.map { it.text }).containsExactly("First", "Second", "Third").inOrder()
    }

    @Test fun realisticLrcSnippetParses() {
        val input =
                """
            [ar:Test Artist]
            [ti:Test Title]
            [length:03:21]
            [00:00.50]Line one
            [00:03.20]Line two
            [00:06.10][00:14.20]Repeated hook
            """.trimIndent()
        val lines = parser.parse(input)
        assertThat(lines.map { it.timeMs }).containsExactly(500L, 3_200L, 6_100L, 14_200L).inOrder()
    }
}
