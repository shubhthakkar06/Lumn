package com.example.musicapp.data.provider

import com.example.musicapp.data.network.SongInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchResultDeduplicatorTest {
    private fun track(
        id: String,
        title: String = "A Sky Full of Stars",
        artist: String = "Coldplay",
        duration: Long = 267L,
        popularity: Long = 0L
    ) = SongInfo(id, title, artist, "art-$id", popularityScore = popularity, durationSeconds = duration)

    @Test
    fun collapsesCrossProviderCopiesOnlyWhenRecordingMetadataMatches() {
        val results = SearchResultDeduplicator.deduplicate(
            listOf(track("saavn_1", popularity = 10), track("youtube01", "A Sky Full Of Stars (Official Audio)", duration = 268))
        )

        assertEquals(1, results.size)
        assertEquals("saavn_1", results.single().videoId)
    }

    @Test
    fun keepsResultsWhenDurationIsUnknownOrDiffers() {
        val unknownLength = SearchResultDeduplicator.deduplicate(
            listOf(track("youtube01", duration = 0), track("youtube02", duration = 0))
        )
        val differentEditLength = SearchResultDeduplicator.deduplicate(
            listOf(track("youtube01", duration = 267), track("youtube02", duration = 250))
        )

        assertEquals(2, unknownLength.size)
        assertEquals(2, differentEditLength.size)
    }

    @Test
    fun keepsSameNameAndLengthWhenPaceOrArrangementIsMarkedDifferent() {
        val slow = track("slow001", "Shubharambh Slow Garba Version", artist = "Aishwarya Majmudar", duration = 300)
        val fast = track("fast001", "Shubharambh Fast Garba Version", artist = "Aishwarya Majmudar", duration = 300)
        val results = SearchResultDeduplicator.deduplicate(listOf(slow, fast))

        assertEquals(2, results.size)
        assertFalse(SearchResultDeduplicator.sameRecording(slow, fast))
    }

    @Test
    fun keepsGujaratiSlowAndFastGarbaVariantsWithIdenticalMetadataLength() {
        val slow = track("slow001", "શુભારંભ ધીમું ગરબા", artist = "કિર્તિદાન ગઢવી", duration = 300)
        val fast = track("fast001", "શુભારંભ ઝડપી ગરબા", artist = "કિર્તિદાન ગઢવી", duration = 300)

        assertTrue(SearchResultDeduplicator.deduplicate(listOf(slow, fast)).size == 2)
    }
}
