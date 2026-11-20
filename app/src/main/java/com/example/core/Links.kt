package com.example.core

import android.net.Uri

/**
 * Link parsing, platform detection, and canonical deduplication for Getiy.
 */
object Links {
    val platforms = listOf(
        "YouTube",
        "TikTok",
        "Instagram",
        "Pinterest",
        "X",
        "Facebook",
    )

    private val urlRegex = Regex("""https?://[^\s<>"]+""", RegexOption.IGNORE_CASE)
    private val trailingPunctuation = Regex("""[).,!?]+$""")
    private val youtubePathRegex = Regex("""^/(shorts|live|embed)/([^/]+)""")

    private val noiseParams = setOf(
        "si", "feature", "pp", "igsh", "igshid", "is_from_webapp",
        "sender_device", "sender_web_id", "share_app_id", "share_link_id",
        "source", "ref", "mibextid", "fbclid", "gclid", "s", "t",
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
        "_r", "_t", "invite_link_id", "rdid",
    )

    /** Finds the first web link in shared or pasted text (e.g. TikTok's "Check this out! https://..."). */
    fun extractLink(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val match = urlRegex.find(text) ?: return null
        return match.value.replace(trailingPunctuation, "")
    }

    /** Which known platform a link belongs to, or null. */
    fun platformOf(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val host = runCatching { Uri.parse(url).host?.lowercase() }.getOrNull().orEmpty()
        if (host.isEmpty()) return null
        fun has(domain: String) = host == domain || host.endsWith(".$domain")
        return when {
            has("youtube.com") || has("youtu.be") -> "YouTube"
            has("tiktok.com") -> "TikTok"
            has("instagram.com") -> "Instagram"
            has("pinterest.com") || has("pin.it") || host.contains("pinterest.") -> "Pinterest"
            has("x.com") || has("twitter.com") -> "X"
            has("facebook.com") || has("fb.watch") -> "Facebook"
            else -> null
        }
    }

    /**
     * Stable key for "the same video" so a link copied twice with different tracking parameters
     * is never offered twice.
     */
    fun linkKey(url: String): String {
        val trimmed = url.trim()
        val uri = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return trimmed
        val rawHost = uri.host?.lowercase() ?: return trimmed
        if (rawHost.isEmpty()) return trimmed
        val host = rawHost.replaceFirst(Regex("^(www|m|mobile)\\."), "")
        val path = (uri.path ?: "").replaceFirst(Regex("/+$"), "")

        if (host == "youtu.be" && path.length > 1) return "yt:${path.substring(1)}"
        if (host.endsWith("youtube.com")) {
            uri.getQueryParameter("v")?.let { return "yt:$it" }
            youtubePathRegex.find(path)?.let { return "yt:${it.groupValues[2]}" }
        }

        val params = runCatching {
            uri.queryParameterNames
                .filter { it.lowercase() !in noiseParams }
                .sorted()
                .mapNotNull { k -> uri.getQueryParameter(k)?.let { v -> "$k=$v" } }
        }.getOrDefault(emptyList())

        return if (params.isEmpty()) "$host$path" else "$host$path?${params.joinToString("&")}"
    }

    /**
     * Strips ad/share tracking query parameters (utm_*, fbclid, gclid, si, igsh, etc.)
     * while preserving essential media/playlist query parameters.
     */
    fun stripTrackingParams(url: String): String {
        val trimmed = url.trim()
        val qIdx = trimmed.indexOf('?')
        if (qIdx < 0) return trimmed
        val hashIdx = trimmed.indexOf('#', startIndex = qIdx)
        val base = trimmed.substring(0, qIdx)
        val query = if (hashIdx >= 0) trimmed.substring(qIdx + 1, hashIdx) else trimmed.substring(qIdx + 1)
        val fragment = if (hashIdx >= 0) trimmed.substring(hashIdx) else ""
        if (query.isEmpty()) return base + fragment

        val kept = query.split('&').filter { param ->
            if (param.isEmpty()) return@filter false
            val eqIdx = param.indexOf('=')
            val key = (if (eqIdx >= 0) param.substring(0, eqIdx) else param).lowercase()
            key !in noiseParams && !key.startsWith("utm_")
        }
        return if (kept.isEmpty()) base + fragment else "$base?${kept.joinToString("&")}$fragment"
    }

    fun displayHostAndPath(url: String): String =
        url.replaceFirst(Regex("^https?://(www\\.)?"), "")
}
