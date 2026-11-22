package com.example.musicapp.data.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryMergeTest {
    private fun snap(vararg liked: Pair<String, String>, playlists: Map<String, String> = emptyMap()) =
        LibrarySnapshot.of(CloudPaths.LIKED to liked.toMap(), CloudPaths.PLAYLISTS to playlists)

    private fun remote(vararg liked: Pair<String, String>, playlists: Map<String, String> = emptyMap()) = mapOf(
        CloudPaths.LIKED to liked.associate { it.first to RemoteItem(it.second, 1) },
        CloudPaths.PLAYLISTS to playlists.mapValues { RemoteItem(it.value, 1) }
    )

    @Test
    fun likeOnAnotherDeviceArrivesHere() {
        val r = LibraryMerge.merge(snap("a" to "A"), snap("a" to "A"), remote("a" to "A", "b" to "B"), 10)
        assertEquals(setOf("a", "b"), r.local.section(CloudPaths.LIKED).keys)
        assertTrue(r.writes.isEmpty())
        assertTrue(r.localChanged)
    }

    @Test
    fun unlikeHereIsDeletedInCloud() {
        val r = LibraryMerge.merge(snap("a" to "A", "b" to "B"), snap("a" to "A"), remote("a" to "A", "b" to "B"), 10)
        assertTrue(r.writes.containsKey("liked/b"))
        assertNull(r.writes["liked/b"])
        assertFalse(r.localChanged)
    }

    @Test
    fun unlikeElsewhereRemovesItHere() {
        val r = LibraryMerge.merge(snap("a" to "A", "b" to "B"), snap("a" to "A", "b" to "B"), remote("a" to "A"), 10)
        assertEquals(setOf("a"), r.local.section(CloudPaths.LIKED).keys)
        assertTrue(r.writes.isEmpty())
    }

    @Test
    fun firstSyncOfANewDeviceKeepsBothLibraries() {
        val r = LibraryMerge.merge(LibrarySnapshot(), snap("a" to "A"), remote("b" to "B"), 10)
        assertEquals(setOf("a", "b"), r.local.section(CloudPaths.LIKED).keys)
        assertEquals(RemoteItem("A", 10), r.writes["liked/a"])
    }

    @Test
    fun playlistEditedHereAndDeletedElsewhereSurvives() {
        val base = snap(playlists = mapOf("p" to "v1"))
        val r = LibraryMerge.merge(base, snap(playlists = mapOf("p" to "v2")), remote(), 10)
        assertEquals("v2", r.local.section(CloudPaths.PLAYLISTS)["p"])
        assertEquals(RemoteItem("v2", 10), r.writes["playlists/p"])
    }

    @Test
    fun reorderElsewhereIsApplied() {
        val base = snap(playlists = mapOf("p" to "order1"))
        val r = LibraryMerge.merge(base, base, remote(playlists = mapOf("p" to "order2")), 10)
        assertEquals("order2", r.local.section(CloudPaths.PLAYLISTS)["p"])
        assertTrue(r.writes.isEmpty())
    }
}
