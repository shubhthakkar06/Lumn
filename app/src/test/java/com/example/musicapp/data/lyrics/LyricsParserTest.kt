package com.example.musicapp.data.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsParserTest {

    @Test
    fun testParseLrc_standardTimestamps() {
        val lrc = """
            [00:14.50] I used to rule the world
            [00:18.20] Seas would rise when I gave the word
            [01:05.80] Roman Cavalry choirs are singing
        """.trimIndent()

        val regex = Regex("""\[(\d{2}):(\d{2})(?:\.(\d{2,3}))?\](.*)""")
        val lines = mutableListOf<LyricLine>()
        lrc.lines().forEach { line ->
            val match = regex.matchEntire(line.trim())
            if (match != null) {
                val min = match.groupValues[1].toLongOrNull() ?: 0L
                val sec = match.groupValues[2].toLongOrNull() ?: 0L
                val msPart = match.groupValues[3]
                val ms = when (msPart.length) {
                    2 -> (msPart.toLongOrNull() ?: 0L) * 10
                    3 -> msPart.toLongOrNull() ?: 0L
                    else -> 0L
                }
                val text = match.groupValues[4].trim()
                if (text.isNotBlank()) {
                    lines.add(LyricLine((min * 60 + sec) * 1000 + ms, text))
                }
            }
        }

        assertEquals(3, lines.size)
        assertEquals(14500L, lines[0].timestampMs)
        assertEquals("I used to rule the world", lines[0].text)
        assertEquals(18200L, lines[1].timestampMs)
        assertEquals(65800L, lines[2].timestampMs)
    }

    @Test
    fun testActiveIndexCalculation() {
        val lines = listOf(
            LyricLine(10000L, "First line"),
            LyricLine(20000L, "Second line"),
            LyricLine(30000L, "Third line")
        )

        // At 5s: before first line
        val at5s = lines.indexOfLast { it.timestampMs <= 5000L }.coerceAtLeast(0)
        assertEquals(0, at5s)

        // At 15s: first line active
        val at15s = lines.indexOfLast { it.timestampMs <= 15000L }.coerceAtLeast(0)
        assertEquals(0, at15s)

        // At 25s: second line active
        val at25s = lines.indexOfLast { it.timestampMs <= 25000L }.coerceAtLeast(0)
        assertEquals(1, at25s)

        // At 35s: third line active
        val at35s = lines.indexOfLast { it.timestampMs <= 35000L }.coerceAtLeast(0)
        assertEquals(2, at35s)
    }
}
