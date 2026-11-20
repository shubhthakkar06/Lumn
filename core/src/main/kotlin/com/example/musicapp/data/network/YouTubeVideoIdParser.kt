package com.example.musicapp.data.network

object YouTubeVideoIdParser {
    private val patterns = listOf(
        Regex("[?&]v=([A-Za-z0-9_-]{11})(?:[&#/?]|$)"),
        Regex("youtu\\.be/([A-Za-z0-9_-]{11})(?:[&#/?]|$)", RegexOption.IGNORE_CASE),
        Regex("/(?:shorts|embed|live)/([A-Za-z0-9_-]{11})(?:[&#/?]|$)", RegexOption.IGNORE_CASE)
    )

    fun parse(url: String): String? = patterns.firstNotNullOfOrNull { it.find(url)?.groupValues?.getOrNull(1) }
}
