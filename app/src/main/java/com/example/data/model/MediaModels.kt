package com.example.data.model

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

enum class DownloadMode(val label: String, val shortLabel: String) {
    VIDEO_AND_AUDIO("Video + audio", "Video + audio"),
    VIDEO("Video", "Video"),
    AUDIO_ONLY("Audio only", "Audio"),
    SUBTITLES_ONLY("Subtitles only", "Subs");

    companion object {
        fun fromName(name: String?): DownloadMode =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: VIDEO
    }
}

enum class JobStatus {
    QUEUED,
    RUNNING,
    PAUSED,
    WAITING,
    DONE,
    FAILED,
    CANCELED;

    val isActive: Boolean
        get() = this == QUEUED || this == RUNNING || this == PAUSED || this == WAITING

    companion object {
        fun fromName(name: String?): JobStatus =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: QUEUED
    }
}

enum class AppThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark");

    companion object {
        fun fromName(name: String?): AppThemeMode =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: DARK
    }
}

enum class LibraryKind(val label: String) {
    VIDEO("Video"),
    AUDIO("Audio"),
    SUBTITLES("Subtitles");
}

enum class LibrarySort(val label: String) {
    NEWEST("Newest"),
    OLDEST("Oldest"),
    LARGEST("Largest"),
    BY_SITE("By site");

    companion object {
        fun fromName(name: String?): LibrarySort =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: NEWEST
    }
}

data class TrimRange(
    val start: Double,
    val end: Double,
)

data class MediaFormat(
    val formatId: String,
    val ext: String,
    val height: Int?,
    val bytes: Long?,
    val hasVideo: Boolean,
    val hasAudio: Boolean,
    val url: String?,
    val protocol: String?,
    val headers: Map<String, String> = emptyMap(),
)

data class SubtitleTrack(
    val lang: String,
    val name: String,
    val isAuto: Boolean,
)

data class SubtitleChoice(
    val langs: Set<String> = emptySet(),
    val includeAuto: Boolean = false,
    val embed: Boolean = true,
) {
    val isEmpty: Boolean get() = langs.isEmpty()

    companion object {
        val None = SubtitleChoice()
    }
}

data class QualityOption(
    val label: String,
    val height: Int? = null,
    val bytes: Long? = null,
)

data class PlaylistEntry(
    val id: String?,
    val title: String,
    val url: String,
    val thumbnail: String?,
    val duration: Double?,
)

data class MediaInfo(
    val url: String,
    val title: String,
    val id: String? = null,
    val thumbnail: String? = null,
    val uploader: String? = null,
    val site: String? = null,
    val duration: Double? = null,
    val isPlaylist: Boolean = false,
    val entryCount: Int = 0,
    val formats: List<MediaFormat> = emptyList(),
    val subtitles: List<SubtitleTrack> = emptyList(),
    val entries: List<PlaylistEntry> = emptyList(),
) {
    /** Best lightweight muxed MP4 stream for inline preview playback. */
    val previewFormat: MediaFormat?
        get() {
            val playable = formats.filter {
                !it.url.isNullOrBlank() && it.hasVideo && it.hasAudio
            }
            if (playable.isEmpty()) return null
            fun score(f: MediaFormat): Int {
                val h = f.height ?: 480
                val dist = kotlin.math.abs(h - 480)
                val extPenalty = if (f.ext.equals("mp4", ignoreCase = true)) 0 else 200
                val hlsPenalty = if (f.protocol?.startsWith("m3u8") == true) 150 else 0
                return dist + extPenalty + hlsPenalty
            }
            return playable.minByOrNull(::score)
        }

    fun optionsFor(mode: DownloadMode): List<QualityOption> {
        if (mode == DownloadMode.SUBTITLES_ONLY) {
            return listOf(QualityOption(label = "Subtitles (.srt)"))
        }
        val bestAudio = largest(formats.filter { it.hasAudio && !it.hasVideo })
        if (mode == DownloadMode.AUDIO_ONLY) {
            val src = bestAudio ?: largest(formats.filter { it.hasAudio })
            return listOf(QualityOption(label = "Best audio", bytes = src?.bytes))
        }
        val heights = formats
            .filter { it.hasVideo && it.height != null && it.height > 0 }
            .mapNotNull { it.height }
            .distinct()
            .sortedDescending()
        if (heights.isEmpty()) {
            return listOf(QualityOption(label = "Best available"))
        }
        return heights.map { h ->
            QualityOption(
                label = "${h}p",
                height = h,
                bytes = sizeAt(h, mode, bestAudio),
            )
        }
    }

    private fun sizeAt(height: Int, mode: DownloadMode, bestAudio: MediaFormat?): Long? {
        val atHeight = formats.filter { it.hasVideo && it.height == height }
        val muxed = largest(atHeight.filter { it.hasAudio })
        val silent = largest(atHeight.filter { !it.hasAudio })
        val withSound = if (silent?.bytes != null && bestAudio?.bytes != null) {
            silent.bytes + bestAudio.bytes
        } else {
            muxed?.bytes ?: silent?.bytes
        }
        if (mode == DownloadMode.VIDEO_AND_AUDIO && withSound != null) {
            return withSound + (bestAudio?.bytes ?: 0L)
        }
        return withSound
    }

    private fun largest(list: List<MediaFormat>): MediaFormat? =
        list.maxByOrNull { it.bytes ?: 0L }
}

data class LibraryItem(
    val jobId: String,
    val path: String,
    val title: String,
    val site: String?,
    val thumbnail: String?,
    val kind: LibraryKind,
    val finishedAt: Long,
    val sizeBytes: Long,
)

object MediaHelpers {
    val fileNameTokens = linkedMapOf(
        "{title}" to "%(title).80B",
        "{id}" to "%(id)s",
        "{site}" to "%(extractor_key)s",
        "{uploader}" to "%(uploader,channel|Unknown)s",
        "{date}" to "%(upload_date>%Y-%m-%d|undated)s",
    )

    const val DEFAULT_FILE_TEMPLATE = "{title} [{id}]"

    private val videoExts = setOf("mp4", "mkv", "webm", "mov", "avi", "m4v")
    private val audioExts = setOf("mp3", "m4a", "opus", "flac", "ogg", "wav", "aac")
    private val subExts = setOf("srt", "vtt", "ass", "lrc")

    fun kindOf(path: String): LibraryKind {
        val ext = File(path).extension.lowercase()
        return when {
            ext in audioExts -> LibraryKind.AUDIO
            ext in subExts -> LibraryKind.SUBTITLES
            ext in videoExts -> LibraryKind.VIDEO
            else -> LibraryKind.VIDEO
        }
    }

    fun formatBytes(bytes: Long?): String? {
        if (bytes == null || bytes <= 0L) return null
        val mb = 1024L * 1024L
        return when {
            bytes >= 1024L * mb -> String.format(Locale.US, "%.1f GB", bytes.toDouble() / (1024.0 * mb))
            bytes >= mb -> "${(bytes.toDouble() / mb).roundToLong()} MB"
            else -> "${(bytes.toDouble() / 1024.0).roundToLong().coerceAtLeast(1)} KB"
        }
    }

    fun clockLabel(seconds: Double): String {
        val total = seconds.coerceAtLeast(0.0).roundToInt()
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) {
            String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        } else {
            String.format(Locale.US, "%d:%02d", m, s)
        }
    }

    fun etaLabel(seconds: Long): String =
        if (seconds >= 60) "${seconds / 60}m ${seconds % 60}s" else "${seconds}s"

    fun trimSuffix(start: Double?, end: Double?): String {
        if (start == null && end == null) return ""
        fun t(s: Double): String {
            val totalSec = s.coerceAtLeast(0.0).roundToInt()
            val m = totalSec / 60
            val sec = totalSec % 60
            return "${m}m${sec.toString().padStart(2, '0')}s"
        }
        return " (${t(start ?: 0.0)}-${if (end == null) "end" else t(end)})"
    }

    fun buildDownloadArgs(
        mode: DownloadMode,
        maxHeight: Int?,
        container: String = "mp4",
        audioFormat: String = "mp3",
        subtitles: SubtitleChoice = SubtitleChoice.None,
        trimStart: Double? = null,
        trimEnd: Double? = null,
    ): List<String> {
        val args = mutableListOf<String>()

        when (mode) {
            DownloadMode.SUBTITLES_ONLY -> args.add("--skip-download")
            DownloadMode.VIDEO -> args.addAll(videoArgs(maxHeight, container))
            DownloadMode.VIDEO_AND_AUDIO -> {
                args.addAll(videoArgs(maxHeight, container))
                args.addAll(extractArgs(audioFormat))
                args.add("-k")
            }
            DownloadMode.AUDIO_ONLY -> {
                args.add("-f")
                args.add("ba/b")
                args.addAll(extractArgs(audioFormat))
            }
        }

        if (!subtitles.isEmpty && mode != DownloadMode.AUDIO_ONLY) {
            args.add("--write-subs")
            if (subtitles.includeAuto) args.add("--write-auto-subs")
            args.add("--sub-langs")
            args.add(subtitles.langs.joinToString(","))
            args.add("--convert-subs")
            args.add("srt")
            if (subtitles.embed && mode != DownloadMode.SUBTITLES_ONLY) {
                args.add("--embed-subs")
            }
        }

        val trim = mode != DownloadMode.SUBTITLES_ONLY && (trimStart != null || trimEnd != null)
        if (trim) {
            fun secs(v: Double): String =
                if (v == v.roundToInt().toDouble()) v.roundToInt().toString()
                else String.format(Locale.US, "%.1f", v)
            val startStr = secs(trimStart ?: 0.0)
            val endStr = if (trimEnd == null) "inf" else secs(trimEnd)
            args.add("--download-sections")
            args.add("*$startStr-$endStr")
            args.add("--force-keyframes-at-cuts")
        }
        return args
    }

    private fun videoArgs(maxHeight: Int?, container: String): List<String> = buildList {
        add("-f")
        if (maxHeight == null) {
            add("bv*+ba/b")
        } else {
            val h = "[height<=$maxHeight]"
            add("bv*$h+ba/b$h/bv*+ba/b")
        }
        if (container == "mp4") {
            add("-S")
            if (maxHeight != null && maxHeight <= 1080) {
                // Keep resolution priority first so 1080p isn't downgraded, then prefer H.264 + AAC
                add("res,vcodec:h264,acodec:aac")
            } else {
                // For 1440p/4K/8K, YouTube & modern sites use VP9/AV1/HEVC (no H.264 above 1080p).
                // Sort by resolution first, then prefer AAC audio and MP4-friendly video codecs.
                add("res,acodec:aac,ext:mp4:m4a")
            }
        }
        add("--merge-output-format")
        add(container)
    }

    private fun extractArgs(audioFormat: String): List<String> = listOf(
        "-x",
        "--audio-format",
        audioFormat,
        "--audio-quality",
        "0",
    )

    fun cleanTemplate(template: String): String {
        var t = Regex("""\{[^}]*\}""").replace(template) { m ->
            if (fileNameTokens.containsKey(m.value)) m.value else ""
        }
        t = t.replace(Regex("""[\\/:*?"<>|]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
        if (fileNameTokens.keys.none { t.contains(it) }) {
            t = DEFAULT_FILE_TEMPLATE
        }
        return t
    }

    fun ytDlpTemplate(template: String): String {
        var t = cleanTemplate(template).replace("%", "%%")
        fileNameTokens.forEach { (k, v) -> t = t.replace(k, v) }
        return t
    }

    fun previewFileName(template: String, ext: String = "mp4"): String {
        var t = cleanTemplate(template)
        val sample = mapOf(
            "{title}" to "Sunset timelapse",
            "{id}" to "dQw4w9WgXcQ",
            "{site}" to "Youtube",
            "{uploader}" to "Nature Clips",
            "{date}" to "2026-11-17",
        )
        sample.forEach { (k, v) -> t = t.replace(k, v) }
        return "$t.$ext"
    }

    fun cookiesForUrl(jar: String, url: String): String? {
        val host = runCatching { Uri.parse(url).host?.lowercase() }.getOrNull().orEmpty()
        if (host.isEmpty()) return null
        val pairs = mutableListOf<String>()
        for (rawLine in jar.lineSequence()) {
            var line = rawLine
            if (line.startsWith("#HttpOnly_")) line = line.substring(10)
            if (line.isEmpty() || line.startsWith("#")) continue
            val fields = line.split('\t')
            if (fields.size < 7) continue
            val domain = fields[0].lowercase().removePrefix(".")
            if (host == domain || host.endsWith(".$domain")) {
                pairs.add("${fields[5]}=${fields[6].trim()}")
            }
        }
        return if (pairs.isEmpty()) null else pairs.joinToString("; ")
    }

    fun parseMediaInfoJson(rawOutput: String, requestedUrl: String): MediaInfo {
        val parts = rawOutput.split("\n\u0000GETIY-COOKIES\u0000\n")
        val jsonText = parts.firstOrNull().orEmpty().trim()
        val cookieJar = parts.getOrNull(1).orEmpty()

        val jsonStart = jsonText.indexOf('{')
        val cleanJson = if (jsonStart >= 0) jsonText.substring(jsonStart) else jsonText
        val rootObj = JSONObject(cleanJson)

        val rootEntriesArr = rootObj.optJSONArray("entries")
        val nonNullEntries = buildList {
            if (rootEntriesArr != null) {
                for (i in 0 until rootEntriesArr.length()) {
                    rootEntriesArr.optJSONObject(i)?.let { add(it) }
                }
            }
        }

        // Many extractors (Instagram, X/Twitter, Reddit, Pinterest, TikTok) wrap a single video post
        // inside a 1-item `entries` array. Unwrap it so formats, title, and preview work seamlessly.
        val singleEntry = if (nonNullEntries.size == 1) nonNullEntries.first() else null
        val obj = singleEntry ?: rootObj
        val isPlaylist = nonNullEntries.size > 1
        val site = obj.optString("extractor_key").takeIf { it.isNotBlank() }
            ?: rootObj.optString("extractor_key").takeIf { it.isNotBlank() }
            ?: obj.optString("extractor").takeIf { it.isNotBlank() }
            ?: rootObj.optString("extractor").takeIf { it.isNotBlank() }

        val entries = mutableListOf<PlaylistEntry>()
        if (isPlaylist) {
            nonNullEntries.forEachIndexed { i, e ->
                val entryUrl = e.optString("webpage_url").takeIf { it.isNotBlank() }
                    ?: e.optString("url").takeIf { it.isNotBlank() }
                    ?: return@forEachIndexed
                val resolvedUrl = if (entryUrl.startsWith("http")) {
                    entryUrl
                } else if (site.equals("Youtube", ignoreCase = true) || requestedUrl.contains("youtu")) {
                    "https://www.youtube.com/watch?v=$entryUrl"
                } else {
                    entryUrl
                }
                entries.add(
                    PlaylistEntry(
                        id = e.optString("id").takeIf { it.isNotBlank() },
                        title = e.optString("title").takeIf { it.isNotBlank() } ?: "Item ${i + 1}",
                        url = resolvedUrl,
                        thumbnail = e.optString("thumbnail").takeIf { it.isNotBlank() }
                            ?: firstThumbUrl(e.optJSONArray("thumbnails")),
                        duration = e.optDouble("duration").takeIf { !it.isNaN() && it > 0 },
                    ),
                )
            }
        }

        fun parseFormatObject(f: JSONObject): MediaFormat {
            val ext = f.optString("ext", "mp4").ifBlank { "mp4" }
            val extLower = ext.lowercase(Locale.US)
            val isKnownAudioExt = extLower in audioExts
            val isKnownImageExt = extLower in setOf("jpg", "jpeg", "png", "webp", "gif", "mhtml")

            // yt-dlp often omits vcodec/acodec (null or missing) for TikTok, Instagram, X, Pinterest,
            // Reddit, and generic muxed MP4 streams. Treat explicit "none" as absent, but infer
            // video/audio presence when the field is simply omitted.
            val vcodecPresent = !f.isNull("vcodec") && f.optString("vcodec").isNotBlank()
            val acodecPresent = !f.isNull("acodec") && f.optString("acodec").isNotBlank()
            val vcodec = if (vcodecPresent) f.optString("vcodec") else ""
            val acodec = if (acodecPresent) f.optString("acodec") else ""
            val height = f.optInt("height", -1).takeIf { it > 0 }
            val width = f.optInt("width", -1).takeIf { it > 0 }
            val hasVideo = when {
                isKnownImageExt -> false
                vcodecPresent -> !vcodec.equals("none", ignoreCase = true)
                height != null || width != null -> true
                else -> !isKnownAudioExt
            }
            val hasAudio = when {
                isKnownImageExt -> false
                acodecPresent -> !acodec.equals("none", ignoreCase = true)
                isKnownAudioExt -> true
                else -> !vcodecPresent // muxed streams with omitted codecs usually carry audio
            }

            val fUrl = f.optString("url").takeIf { it.isNotBlank() }
            val bytes = f.optLong("filesize", -1L).takeIf { it > 0 }
                ?: f.optLong("filesize_approx", -1L).takeIf { it > 0 }
            val headers = mutableMapOf<String, String>()
            f.optJSONObject("http_headers")?.let { hObj ->
                hObj.keys().forEach { k ->
                    val v = hObj.optString(k)
                    if (v.isNotBlank()) headers[k] = v
                }
            }
            if (fUrl != null && !headers.containsKey("Cookie")) {
                cookiesForUrl(cookieJar, fUrl)?.let { headers["Cookie"] = it }
            }
            return MediaFormat(
                formatId = f.optString("format_id").ifBlank { "default" },
                ext = ext,
                height = height,
                bytes = bytes,
                hasVideo = hasVideo,
                hasAudio = hasAudio,
                url = fUrl,
                protocol = f.optString("protocol").takeIf { it.isNotBlank() },
                headers = headers,
            )
        }

        val formats = mutableListOf<MediaFormat>()
        val formatsArr = obj.optJSONArray("formats") ?: rootObj.optJSONArray("formats")
        if (formatsArr != null) {
            for (i in 0 until formatsArr.length()) {
                val f = formatsArr.optJSONObject(i) ?: continue
                val parsed = parseFormatObject(f)
                if (parsed.hasVideo || parsed.hasAudio) {
                    formats.add(parsed)
                }
            }
        }

        // Some extractors (Pinterest, Imgur, direct MP4/HLS, generic HTML5) expose a direct `url`
        // on the root object with no `formats` array. Synthesize a format so options & preview work.
        if (formats.isEmpty()) {
            val directUrl = obj.optString("url").takeIf { it.startsWith("http", ignoreCase = true) }
            if (directUrl != null) {
                formats.add(parseFormatObject(obj))
            }
        }

        val subtitles = mutableListOf<SubtitleTrack>()
        val seenLangs = mutableSetOf<String>()
        fun collectSubs(subObj: JSONObject?, isAuto: Boolean) {
            if (subObj == null) return
            val keys = subObj.keys()
            while (keys.hasNext()) {
                val lang = keys.next()
                if (lang == "live_chat" || !seenLangs.add("$lang:$isAuto")) continue
                val list = subObj.optJSONArray(lang)
                val label = list?.optJSONObject(0)?.optString("name")?.takeIf { it.isNotBlank() } ?: lang
                subtitles.add(SubtitleTrack(lang = lang, name = label, isAuto = isAuto))
            }
        }
        collectSubs(obj.optJSONObject("subtitles") ?: rootObj.optJSONObject("subtitles"), isAuto = false)
        collectSubs(
            obj.optJSONObject("automatic_captions") ?: rootObj.optJSONObject("automatic_captions"),
            isAuto = true,
        )

        val resolvedPageUrl = obj.optString("webpage_url").takeIf { it.isNotBlank() }
            ?: rootObj.optString("webpage_url").takeIf { it.isNotBlank() }
            ?: obj.optString("original_url").takeIf { it.isNotBlank() }
            ?: requestedUrl

        return MediaInfo(
            url = resolvedPageUrl,
            title = obj.optString("title").takeIf { it.isNotBlank() }
                ?: rootObj.optString("title").takeIf { it.isNotBlank() }
                ?: "Untitled media",
            id = obj.optString("id").takeIf { it.isNotBlank() }
                ?: rootObj.optString("id").takeIf { it.isNotBlank() },
            thumbnail = obj.optString("thumbnail").takeIf { it.isNotBlank() }
                ?: firstThumbUrl(obj.optJSONArray("thumbnails"))
                ?: rootObj.optString("thumbnail").takeIf { it.isNotBlank() }
                ?: firstThumbUrl(rootObj.optJSONArray("thumbnails"))
                ?: entries.firstOrNull()?.thumbnail,
            uploader = obj.optString("uploader").takeIf { it.isNotBlank() }
                ?: obj.optString("channel").takeIf { it.isNotBlank() }
                ?: rootObj.optString("uploader").takeIf { it.isNotBlank() }
                ?: rootObj.optString("channel").takeIf { it.isNotBlank() },
            site = site,
            duration = obj.optDouble("duration").takeIf { !it.isNaN() && it > 0 }
                ?: rootObj.optDouble("duration").takeIf { !it.isNaN() && it > 0 },
            isPlaylist = isPlaylist,
            entryCount = entries.size,
            formats = formats,
            subtitles = subtitles,
            entries = entries,
        )
    }

    private fun firstThumbUrl(arr: JSONArray?): String? {
        if (arr == null || arr.length() == 0) return null
        return arr.optJSONObject(arr.length() - 1)?.optString("url")?.takeIf { it.isNotBlank() }
    }
}
