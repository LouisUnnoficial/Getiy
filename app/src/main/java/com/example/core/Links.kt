package com.example.core

import android.net.Uri

/**
 * Link parsing, platform detection, safe parameter cleaning, and canonical deduplication for Getiy.
 */
object Links {
    val platforms = listOf(
        "YouTube",
        "TikTok",
        "Instagram",
        "Pinterest",
        "X",
        "Facebook",
        "Reddit",
        "Vimeo",
        "Twitch",
        "SoundCloud",
    )

    private val urlRegex = Regex("""https?://[^\s<>"]+""", RegexOption.IGNORE_CASE)
    private val bareDomainRegex = Regex(
        """(?<![@\w])(?:www\.|m\.|vm\.|vt\.)?(?:youtube\.com|youtu\.be|tiktok\.com|instagram\.com|instagr\.am|pinterest\.com|pin\.it|x\.com|twitter\.com|t\.co|facebook\.com|fb\.watch|fb\.com|reddit\.com|redd\.it|vimeo\.com|twitch\.tv|soundcloud\.com|dailymotion\.com|dai\.ly|threads\.net|threads\.com|streamable\.com|bilibili\.com|b23\.tv|bsky\.app|rumble\.com|kick\.com|imgur\.com|snapchat\.com|tumblr\.com|linkedin\.com)/[^\s<>"]+""",
        RegexOption.IGNORE_CASE,
    )
    private val trailingPunctuation = Regex("""[).,!?;:'"\]]+$""")
    private val youtubePathRegex = Regex("""^/(shorts|live|embed)/([^/]+)""")

    // Used for deduplication keys only
    private val dedupNoiseParams = setOf(
        "si", "feature", "pp", "igsh", "igshid", "is_from_webapp",
        "sender_device", "sender_web_id", "share_app_id", "share_link_id",
        "source", "ref", "mibextid", "fbclid", "gclid", "s", "t",
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
        "_r", "_t", "invite_link_id", "rdid",
    )

    // Strictly pure analytics params that are safe to remove before passing URLs to yt-dlp.
    // Never strip functional tokens like `s`, `t`, `source`, `ref`, `_r`, `_t`, or share IDs.
    private val safeStripParams = setOf(
        "si", "feature", "pp", "igsh", "igshid", "mibextid", "fbclid", "gclid", "rdid",
    )

    private val shortenerHosts = setOf(
        "vm.tiktok.com",
        "vt.tiktok.com",
        "pin.it",
        "t.co",
        "redd.it",
        "fb.watch",
        "dai.ly",
        "instagr.am",
        "ig.me",
        "b23.tv",
        "on.soundcloud.com",
        "youtu.be",
    )

    /**
     * Finds the first web link in shared or pasted text (including bare links without https://
     * or share text like "Check this out! https://vm.tiktok.com/...").
     */
    fun extractLink(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val match = urlRegex.find(text)
        if (match != null) {
            return match.value.replace(trailingPunctuation, "")
        }
        val bare = bareDomainRegex.find(text) ?: return null
        return "https://" + bare.value.replace(trailingPunctuation, "")
    }

    /** Which known platform a link belongs to, or null (yt-dlp still supports 1,800+ other sites). */
    fun platformOf(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val host = runCatching { Uri.parse(url).host?.lowercase() }.getOrNull().orEmpty()
        if (host.isEmpty()) return null
        fun has(domain: String) = host == domain || host.endsWith(".$domain")
        return when {
            has("youtube.com") || has("youtu.be") -> "YouTube"
            has("tiktok.com") -> "TikTok"
            has("instagram.com") || has("instagr.am") || has("ig.me") -> "Instagram"
            has("pinterest.com") || has("pin.it") || host.contains("pinterest.") -> "Pinterest"
            has("x.com") || has("twitter.com") || has("t.co") -> "X"
            has("facebook.com") || has("fb.watch") || has("fb.com") || has("fb.gg") -> "Facebook"
            has("reddit.com") || has("redd.it") -> "Reddit"
            has("vimeo.com") -> "Vimeo"
            has("twitch.tv") -> "Twitch"
            has("soundcloud.com") -> "SoundCloud"
            has("dailymotion.com") || has("dai.ly") -> "Dailymotion"
            has("threads.net") || has("threads.com") -> "Threads"
            has("snapchat.com") -> "Snapchat"
            has("streamable.com") -> "Streamable"
            has("bilibili.com") || has("b23.tv") -> "Bilibili"
            has("bsky.app") -> "Bluesky"
            has("rumble.com") -> "Rumble"
            has("kick.com") -> "Kick"
            has("imgur.com") -> "Imgur"
            has("tumblr.com") -> "Tumblr"
            has("linkedin.com") -> "LinkedIn"
            else -> null
        }
    }

    /**
     * Returns true for short redirect links or share wrappers (vm.tiktok.com, pin.it, /share/...)
     * that benefit from resolving the canonical destination URL before extraction.
     */
    fun isShortShareLink(url: String): Boolean {
        val uri = runCatching { Uri.parse(url.trim()) }.getOrNull() ?: return false
        val host = uri.host?.lowercase()?.removePrefix("www.") ?: return false
        val path = uri.path?.lowercase().orEmpty()
        if (host in shortenerHosts && host != "youtu.be") return true
        if (host.endsWith("tiktok.com") && path.startsWith("/t/")) return true
        if (host.endsWith("instagram.com") && path.startsWith("/share/")) return true
        if (host.endsWith("facebook.com") && path.startsWith("/share/")) return true
        if (host.endsWith("reddit.com") && Regex("""/s/[A-Za-z0-9]+""").containsMatchIn(path)) return true
        if ((host == "x.com" || host == "twitter.com") && path.startsWith("/i/")) return true
        return false
    }

    /**
     * Returns true only when [url] clearly points to a multi-item playlist, album, or channel feed.
     * Single posts/reels/tweets must NOT use `--flat-playlist`, otherwise yt-dlp omits format metadata.
     */
    fun looksLikePlaylistUrl(url: String): Boolean {
        val uri = runCatching { Uri.parse(url.trim()) }.getOrNull() ?: return false
        val host = uri.host?.lowercase()?.removePrefix("www.") ?: return false
        val path = uri.path?.lowercase().orEmpty()
        if (host.endsWith("youtube.com") || host == "youtu.be") {
            val hasList = !uri.getQueryParameter("list").isNullOrBlank()
            val hasVideo = !uri.getQueryParameter("v").isNullOrBlank() ||
                host == "youtu.be" ||
                path.startsWith("/shorts/") ||
                path.startsWith("/live/")
            if (path.startsWith("/playlist")) return true
            if (hasList && !hasVideo) return true
            if (path.endsWith("/videos") || path.endsWith("/shorts") || path.endsWith("/streams")) return true
            return false
        }
        if (host.endsWith("soundcloud.com") && path.contains("/sets/")) return true
        if (path.contains("/playlist/") || path.contains("/playlists/") || path.contains("/album/")) return true
        return false
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
                .filter { it.lowercase() !in dedupNoiseParams }
                .sorted()
                .mapNotNull { k -> uri.getQueryParameter(k)?.let { v -> "$k=$v" } }
        }.getOrDefault(emptyList())

        return if (params.isEmpty()) "$host$path" else "$host$path?${params.joinToString("&")}"
    }

    /**
     * Strips strictly safe analytics query parameters (utm_*, fbclid, gclid, si, igsh, etc.)
     * while preserving functional parameters and shortlink tokens needed by TikTok, X, Instagram,
     * Pinterest, Reddit, Vimeo, and other extractors.
     */
    fun stripTrackingParams(url: String): String {
        val trimmed = url.trim()
        if (isShortShareLink(trimmed)) return trimmed
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
            key !in safeStripParams && !key.startsWith("utm_")
        }
        return if (kept.isEmpty()) base + fragment else "$base?${kept.joinToString("&")}$fragment"
    }

    fun displayHostAndPath(url: String): String =
        url.replaceFirst(Regex("^https?://(www\\.)?"), "")
}
