package com.example.musicapp.data.provider

/** Audio quality tiers that map onto what each provider actually offers. */
enum class StreamQuality(val kbps: Int, val saavnSuffix: String) {
    LOW(96, "_96.mp4"),
    NORMAL(160, "_160.mp4"),
    HIGH(320, "_320.mp4");

    /** Rewrites a JioSaavn CDN URL to this tier (the CDN hosts 96/160/320 variants). */
    fun applyToSaavnUrl(url: String): String =
        url.replace(Regex("_(48|96|160|320)\\.mp4"), saavnSuffix)

    companion object {
        /** Maps the labels stored by the settings screen (including older ones) to a tier. */
        fun fromLabel(label: String?): StreamQuality = when (label?.trim()?.lowercase()) {
            "low" -> LOW
            "normal", "automatic" -> NORMAL
            else -> HIGH // "High", "Very high", "Ultra 320kbps"
        }
    }
}
