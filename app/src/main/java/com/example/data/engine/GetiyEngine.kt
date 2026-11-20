package com.example.data.engine

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import com.example.core.Links
import com.example.data.model.MediaFormat
import com.example.data.model.MediaHelpers
import com.example.data.model.MediaInfo
import com.example.data.settings.AppSettings
import com.example.service.DownloadService
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

sealed class EngineEvent {
    data class Starting(val jobId: String) : EngineEvent()
    data class Progress(
        val jobId: String,
        val percent: Double,
        val etaSeconds: Long?,
        val speed: String?,
        val line: String?,
    ) : EngineEvent()

    data class Done(val jobId: String, val paths: List<String>) : EngineEvent()
    data class Canceled(val jobId: String) : EngineEvent()
    data class Error(val jobId: String, val message: String, val isNetworkError: Boolean) : EngineEvent()
}

data class EngineStatus(
    val initializing: Boolean = false,
    val standby: Boolean = true,
    val ytDlpReady: Boolean = false,
    val ytDlpVersion: String? = null,
    val ffmpegReady: Boolean = false,
    val ffmpegVersion: String? = null,
    val aria2cReady: Boolean = false,
    val youtubedlAndroidVersion: String = GetiyEngine.YOUTUBEDL_ANDROID_VERSION,
    val error: String? = null,
) {
    val allCoreReady: Boolean
        get() = ytDlpReady && ffmpegReady

    val summaryLabel: String
        get() = when {
            initializing -> "Initializing engines…"
            allCoreReady -> buildString {
                append("yt-dlp ")
                append(ytDlpVersion ?: "bundled")
                append(" · FFmpeg ")
                append(ffmpegVersion ?: "ready")
                if (aria2cReady) append(" · Aria2c")
            }
            standby && error == null -> "On-demand mode · loads only when needed"
            else -> error ?: "Engine initialization incomplete"
        }
}

/**
 * Native Android yt-dlp, FFmpeg, and Aria2c engine powered by youtubedl-android.
 * Uses lazy on-demand initialization so cold startup consumes near-zero CPU/RAM on low-end devices.
 * All native binaries are packaged directly inside the APK (zero dynamic code loading).
 */
class GetiyEngine(private val context: Context) {

    private val appContext = context.applicationContext
    private val ytDlpMutex = Mutex()
    private val ffmpegMutex = Mutex()
    private val aria2cMutex = Mutex()

    @Volatile
    private var ytDlpInitialized = false

    @Volatile
    private var ffmpegInitialized = false

    @Volatile
    private var aria2cInitialized = false

    @Volatile
    private var lastNotificationUpdateMs = 0L

    private val _engineStatus = MutableStateFlow(EngineStatus(initializing = false, standby = true))
    val engineStatus: StateFlow<EngineStatus> = _engineStatus.asStateFlow()

    private val activeJobs = ConcurrentHashMap<String, Pair<String, Float>>()
    private val ffmpegProcesses = ConcurrentHashMap<String, Process>()
    private val httpClient by lazy {
        OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    private val speedRegex = Regex("""at\s+([\d.]+\s*[KMG]?i?B/s)""")
    private val networkErrorRegex = Regex(
        "Unable to download|timed out|time out|Connection (reset|refused|aborted)|Network is unreachable|" +
            "No route to host|Temporary failure in name resolution|getaddrinfo|Name or service not known|" +
            "No address associated|Remote end closed|IncompleteRead|Software caused connection abort|HTTP Error 5\\d\\d|SSL|EOF occurred",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Zero-subprocess startup inspection: reads cached yt-dlp version metadata if available
     * without unpacking FFmpeg/Aria2c or spawning native child processes at app launch.
     */
    suspend fun warmUpLightweight(): EngineStatus = withContext(Dispatchers.IO) {
        val cachedVersion = runCatching {
            YoutubeDL.getInstance().versionName(appContext)?.trim()?.takeIf { it.isNotBlank() }
        }.getOrNull()
        val existingFfmpeg = resolveFfmpegBinary() != null
        val updated = _engineStatus.value.copy(
            initializing = false,
            standby = !ytDlpInitialized,
            ytDlpVersion = cachedVersion ?: _engineStatus.value.ytDlpVersion,
            ffmpegReady = ffmpegInitialized || existingFfmpeg,
        )
        _engineStatus.value = updated
        updated
    }

    /**
     * Full explicit verification (triggered only when user taps "Verify engines" in Settings).
     * Initializes yt-dlp, FFmpeg, and Aria2c and probes their versions.
     */
    suspend fun initializeEngines(force: Boolean = false): EngineStatus = withContext(Dispatchers.IO) {
        _engineStatus.value = _engineStatus.value.copy(initializing = true, standby = false, error = null)

        val errors = mutableListOf<String>()

        runCatching { ensureYtDlpReady(force = force, probeSubprocess = force) }
            .onFailure { errors.add("yt-dlp: ${cleanEngineError(it)}") }

        runCatching { ensureFfmpegReady(force = force, probeVersion = true) }
            .onFailure { errors.add("FFmpeg: ${cleanEngineError(it)}") }

        runCatching { ensureAria2cReady(force = force) }

        val cur = _engineStatus.value
        val finalStatus = cur.copy(
            initializing = false,
            standby = false,
            error = errors.takeIf { it.isNotEmpty() }?.joinToString(" · "),
        )
        _engineStatus.value = finalStatus
        finalStatus
    }

    private suspend fun ensureYtDlpReady(force: Boolean = false, probeSubprocess: Boolean = false) {
        if (ytDlpInitialized && !force) return
        ytDlpMutex.withLock {
            if (ytDlpInitialized && !force) return@withLock
            _engineStatus.value = _engineStatus.value.copy(initializing = true, standby = false, error = null)
            try {
                YoutubeDL.getInstance().init(appContext)
                ytDlpInitialized = true
                val ver = probeYtDlpVersion(allowSubprocess = probeSubprocess)
                _engineStatus.value = _engineStatus.value.copy(
                    initializing = false,
                    standby = false,
                    ytDlpReady = true,
                    ytDlpVersion = ver,
                )
            } catch (err: Throwable) {
                val msg = cleanEngineError(err)
                _engineStatus.value = _engineStatus.value.copy(
                    initializing = false,
                    standby = false,
                    ytDlpReady = false,
                    error = "yt-dlp: $msg",
                )
                throw IllegalStateException(msg, err)
            }
        }
    }

    private suspend fun ensureFfmpegReady(force: Boolean = false, probeVersion: Boolean = false): File {
        if (ffmpegInitialized && !force) {
            resolveFfmpegBinary()?.let { return it }
        }
        return ffmpegMutex.withLock {
            if (ffmpegInitialized && !force) {
                resolveFfmpegBinary()?.let { return@withLock it }
            }
            FFmpeg.getInstance().init(appContext)
            val binary = resolveFfmpegBinary()
            val ffVer = if (probeVersion) probeFfmpegVersion() else _engineStatus.value.ffmpegVersion
            val ok = binary != null
            ffmpegInitialized = ok
            _engineStatus.value = _engineStatus.value.copy(
                ffmpegReady = ok,
                ffmpegVersion = ffVer ?: _engineStatus.value.ffmpegVersion,
            )
            if (!ok || binary == null) {
                throw IllegalStateException(
                    "FFmpeg binary not found. Open Settings → Verify engines to re-initialize.",
                )
            }
            binary
        }
    }

    private suspend fun ensureAria2cReady(force: Boolean = false): Boolean {
        if (aria2cInitialized && !force) return true
        return aria2cMutex.withLock {
            if (aria2cInitialized && !force) return@withLock true
            val ok = runCatching {
                Aria2c.getInstance().init(appContext)
                true
            }.getOrDefault(false)
            aria2cInitialized = ok
            _engineStatus.value = _engineStatus.value.copy(aria2cReady = ok)
            ok
        }
    }

    private suspend fun ensureReady() {
        ensureYtDlpReady(force = false, probeSubprocess = false)
    }

    suspend fun initVersion(): String {
        ensureYtDlpReady(force = false, probeSubprocess = false)
        return _engineStatus.value.ytDlpVersion ?: "bundled"
    }

    private fun probeYtDlpVersion(allowSubprocess: Boolean = false): String {
        val fromLibrary = runCatching {
            YoutubeDL.getInstance().versionName(appContext)?.trim()
        }.getOrNull()
        if (!fromLibrary.isNullOrBlank()) return fromLibrary
        if (!allowSubprocess) return "bundled"

        return runCatching {
            val req = YoutubeDLRequest(emptyList()).apply { addOption("--version") }
            YoutubeDL.getInstance().execute(req).out.lines()
                .map { it.trim() }
                .firstOrNull { it.isNotEmpty() }
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "bundled"
    }

    private fun probeFfmpegVersion(): String? {
        val bin = resolveFfmpegBinary() ?: return null
        return runCatching {
            val pb = buildFfmpegProcess(listOf(bin.absolutePath, "-version"))
            val process = pb.start()
            val firstLine = process.inputStream.bufferedReader().use { it.readLine() }
            process.waitFor()
            parseFfmpegVersionLine(firstLine)
        }.getOrNull()
    }

    private fun resolveFfmpegBinary(): File? {
        val candidates = listOf(
            File(appContext.applicationInfo.nativeLibraryDir, "libffmpeg.so"),
            File(appContext.noBackupFilesDir, "youtubedl-android/packages/ffmpeg/usr/bin/ffmpeg"),
            File(appContext.filesDir, "youtubedl-android/packages/ffmpeg/usr/bin/ffmpeg"),
        )
        return candidates.firstOrNull { it.exists() && it.isFile }
    }

    private fun buildFfmpegProcess(cmd: List<String>): ProcessBuilder {
        val pb = ProcessBuilder(cmd).redirectErrorStream(true)
        val env = pb.environment()

        val packagesNoBackup = File(appContext.noBackupFilesDir, "youtubedl-android/packages")
        val packagesFiles = File(appContext.filesDir, "youtubedl-android/packages")
        val nativeLibDir = appContext.applicationInfo.nativeLibraryDir

        val libPaths = listOfNotNull(
            File(packagesNoBackup, "ffmpeg/usr/lib").absolutePath,
            File(packagesNoBackup, "python/usr/lib").absolutePath,
            File(packagesFiles, "ffmpeg/usr/lib").absolutePath,
            File(packagesFiles, "python/usr/lib").absolutePath,
            nativeLibDir,
            System.getenv("LD_LIBRARY_PATH")?.takeIf { it.isNotBlank() },
        ).distinct().joinToString(":")
        env["LD_LIBRARY_PATH"] = libPaths

        val binPaths = listOfNotNull(
            nativeLibDir,
            File(packagesNoBackup, "ffmpeg/usr/bin").absolutePath,
            File(packagesFiles, "ffmpeg/usr/bin").absolutePath,
            System.getenv("PATH")?.takeIf { it.isNotBlank() },
        ).distinct().joinToString(":")
        env["PATH"] = binPaths

        return pb
    }

    fun resolveOutputDir(settings: AppSettings): File {
        val envDir = when (settings.saveBase) {
            "movies" -> Environment.DIRECTORY_MOVIES
            "music" -> Environment.DIRECTORY_MUSIC
            else -> Environment.DIRECTORY_DOWNLOADS
        }
        @Suppress("DEPRECATION")
        val publicRoot = Environment.getExternalStoragePublicDirectory(envDir)
        val target = File(publicRoot, settings.saveFolder)
        val writable = isDirectoryActuallyWritable(target)
        if (writable) return target

        // Fallback to app-scoped external storage if public folder is restricted by scoped storage
        val appFallback = File(appContext.getExternalFilesDir(envDir) ?: appContext.filesDir, settings.saveFolder)
        if (!appFallback.exists()) appFallback.mkdirs()
        return appFallback
    }

    private fun isDirectoryActuallyWritable(dir: File): Boolean = runCatching {
        if (!dir.exists()) dir.mkdirs()
        if (!dir.exists() || !dir.isDirectory) return@runCatching false
        val probe = File.createTempFile(".getiy_probe_", ".tmp", dir)
        probe.delete()
        true
    }.getOrDefault(false)

    /**
     * Resolves short share links (vm.tiktok.com, pin.it, t.co, redd.it, instagram.com/share/...)
     * to their canonical destination URL so yt-dlp extractors always match cleanly.
     */
    private fun resolveRedirectUrl(rawUrl: String): String {
        val cleaned = Links.stripTrackingParams(rawUrl)
        if (!Links.isShortShareLink(cleaned)) return cleaned
        return runCatching {
            val req = Request.Builder()
                .url(cleaned)
                .header("User-Agent", DESKTOP_USER_AGENT)
                .header("Accept-Language", "en-US,en;q=0.9")
                .get()
                .build()
            httpClient.newCall(req).execute().use { resp ->
                val finalUrl = resp.request.url.toString()
                if (finalUrl.startsWith("http", ignoreCase = true) && !finalUrl.contains("/accounts/login")) {
                    Links.stripTrackingParams(finalUrl)
                } else {
                    cleaned
                }
            }
        }.getOrDefault(cleaned)
    }

    suspend fun fetch(url: String): MediaInfo = withContext(Dispatchers.IO) {
        ensureReady()
        val resolvedUrl = resolveRedirectUrl(url)
        val isPlaylistUrl = Links.looksLikePlaylistUrl(resolvedUrl)
        val platform = Links.platformOf(resolvedUrl)

        var lastError: Throwable? = null

        // Attempt 1: Clean primary yt-dlp extraction (no empty cookie jar, flat-playlist ONLY for playlists)
        val primary = runCatching {
            val req = buildFetchRequest(
                targetUrl = resolvedUrl,
                isPlaylistUrl = isPlaylistUrl,
                platform = platform,
                fallbackMode = false,
            )
            val out = YoutubeDL.getInstance().execute(req).out
            MediaHelpers.parseMediaInfoJson(out, resolvedUrl)
        }
        primary.getOrNull()?.let { return@withContext it }
        lastError = primary.exceptionOrNull()

        // Attempt 2: Resilient retry with IPv4, --no-playlist, and mobile/client fallback
        val retry = runCatching {
            val req = buildFetchRequest(
                targetUrl = resolvedUrl,
                isPlaylistUrl = false,
                platform = platform,
                fallbackMode = true,
            )
            val out = YoutubeDL.getInstance().execute(req).out
            MediaHelpers.parseMediaInfoJson(out, resolvedUrl)
        }
        retry.getOrNull()?.let { return@withContext it }
        lastError = retry.exceptionOrNull() ?: lastError

        // Attempt 3: Direct media URL or OpenGraph (<meta property="og:video">) fallback
        // Covers Pinterest, Threads, Tumblr, blogs, direct MP4/M3U8 links, or temporary extractor blocks
        fetchDirectOrOpenGraphFallback(resolvedUrl)?.let { return@withContext it }

        throw IllegalStateException(
            cleanEngineError(lastError ?: IllegalStateException("Unable to read this link")),
            lastError,
        )
    }

    private fun buildFetchRequest(
        targetUrl: String,
        isPlaylistUrl: Boolean,
        platform: String?,
        fallbackMode: Boolean,
    ): YoutubeDLRequest {
        val req = YoutubeDLRequest(targetUrl)
        req.addOption("--ignore-config")
        req.addOption("--no-cache-dir")
        req.addOption("--dump-single-json")
        req.addOption("--no-warnings")
        req.addOption("--no-check-certificates")
        req.addOption("--geo-bypass")
        req.addOption("--socket-timeout", "30")
        req.addOption("--retries", "3")

        if (isPlaylistUrl) {
            req.addOption("--flat-playlist")
            req.addOption("--playlist-items", "1:300")
        } else {
            req.addOption("--no-playlist")
        }

        if (fallbackMode) {
            req.addOption("--force-ipv4")
            when (platform) {
                "YouTube" -> req.addOption("--extractor-args", "youtube:player_client=android,web")
                "TikTok" -> req.addOption("--user-agent", MOBILE_USER_AGENT)
                else -> {
                    req.addOption("--user-agent", MOBILE_USER_AGENT)
                    req.addOption("--add-header", "Accept-Language:en-US,en;q=0.9")
                }
            }
        } else {
            when (platform) {
                "YouTube", "TikTok" -> {
                    // Let yt-dlp use its native extractor client headers for YouTube & TikTok first
                }
                else -> {
                    req.addOption("--user-agent", DESKTOP_USER_AGENT)
                    req.addOption("--add-header", "Accept-Language:en-US,en;q=0.9")
                }
            }
        }

        return req
    }

    private fun fetchDirectOrOpenGraphFallback(targetUrl: String): MediaInfo? = runCatching {
        val req = Request.Builder()
            .url(targetUrl)
            .header("User-Agent", DESKTOP_USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9")
            .get()
            .build()

        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@runCatching null
            val finalUrl = resp.request.url.toString()
            val contentType = resp.header("Content-Type").orEmpty().lowercase(Locale.US)
            val site = Links.platformOf(finalUrl) ?: Uri.parse(finalUrl).host?.removePrefix("www.")

            // Direct media file or HLS playlist URL
            if (contentType.startsWith("video/") ||
                contentType.startsWith("audio/") ||
                contentType.contains("mpegurl") ||
                DIRECT_MEDIA_URL_REGEX.containsMatchIn(finalUrl)
            ) {
                val isAudio = contentType.startsWith("audio/") ||
                    Regex("""\.(mp3|m4a|opus|ogg|wav|flac)(\?|$)""", RegexOption.IGNORE_CASE).containsMatchIn(finalUrl)
                val ext = inferExtFromUrlOrMime(finalUrl, contentType, isAudio)
                val title = Uri.parse(finalUrl).lastPathSegment
                    ?.substringBeforeLast('.')
                    ?.takeIf { it.isNotBlank() }
                    ?: "Media download"
                val contentLength = resp.header("Content-Length")?.toLongOrNull()?.takeIf { it > 0 }
                return@runCatching MediaInfo(
                    url = finalUrl,
                    title = title,
                    site = site,
                    formats = listOf(
                        MediaFormat(
                            formatId = "direct",
                            ext = ext,
                            height = null,
                            bytes = contentLength,
                            hasVideo = !isAudio,
                            hasAudio = true,
                            url = finalUrl,
                            protocol = if (ext == "m3u8") "m3u8_native" else "https",
                        ),
                    ),
                )
            }

            if (!contentType.contains("html") && !contentType.contains("xhtml")) {
                return@runCatching null
            }

            // Read up to 256 KB of HTML head/body to find embedded video metadata
            val html = resp.body?.source()?.let { source ->
                source.request(256 * 1024L)
                source.buffer.clone().readUtf8(minOf(source.buffer.size, 256 * 1024L))
            }.orEmpty()
            if (html.isBlank()) return@runCatching null

            extractMediaFromHtml(html, finalUrl, site)
        }
    }.getOrNull()

    /** Downloads a lightweight cached MP4 preview when sites refuse direct stream playback (e.g. TikTok). */
    suspend fun previewFile(url: String): String? = withContext(Dispatchers.IO) {
        ensureReady()
        val resolvedUrl = resolveRedirectUrl(url)
        val dir = File(appContext.cacheDir, "preview").apply {
            deleteRecursively()
            mkdirs()
        }
        val req = YoutubeDLRequest(resolvedUrl)
        req.addOption("--ignore-config")
        req.addOption("-f", "w[vcodec!=none][acodec!=none][ext=mp4]/w[vcodec!=none][acodec!=none]/b[ext=mp4]/b/w")
        req.addOption("-o", File(dir, "preview.%(ext)s").absolutePath)
        req.addOption("--no-playlist")
        req.addOption("--no-part")
        req.addOption("--no-cache-dir")
        req.addOption("--no-check-certificates")
        req.addOption("--geo-bypass")
        req.addOption("--socket-timeout", "25")
        req.addOption("--print", "after_move:filepath")
        req.addOption("--no-simulate")
        val out = YoutubeDL.getInstance().execute(req).out
        out.lines().map { it.trim() }.lastOrNull { it.isNotEmpty() && File(it).exists() }
            ?: dir.listFiles()?.firstOrNull()?.absolutePath
    }

    suspend fun runDownload(
        jobId: String,
        url: String,
        args: List<String>,
        suffix: String,
        matchId: String?,
        title: String,
        template: String,
        outputDir: File,
        useAria2c: Boolean = false,
        liteMode: Boolean = false,
        onEvent: (EngineEvent) -> Unit,
    ) = withContext(Dispatchers.IO) {
        onEvent(EngineEvent.Starting(jobId))
        activeJobs[jobId] = title to 0f
        refreshForegroundService(force = true)
        val startedAt = System.currentTimeMillis() - 1000L
        try {
            ensureReady()
            // Initialize FFmpeg lazily right before download so muxing/audio conversion always works
            val ffmpegBin = runCatching { ensureFfmpegReady(force = false, probeVersion = false) }.getOrNull()
            // Initialize Aria2c strictly on-demand only when the user actually enabled it
            val hasTrim = args.any { it == "--download-sections" }
            val ariaReady = if (useAria2c && !hasTrim) ensureAria2cReady(force = false) else false

            if (!outputDir.exists()) outputDir.mkdirs()

            val resolvedUrl = resolveRedirectUrl(url)
            val platform = Links.platformOf(resolvedUrl)
            val ytTemplate = if (template.isBlank()) "%(title).80B [%(id)s]" else MediaHelpers.ytDlpTemplate(template)

            fun buildDownloadRequest(forceIpv4: Boolean): YoutubeDLRequest {
                val req = YoutubeDLRequest(resolvedUrl)
                req.addOption("--ignore-config")
                req.addOption("-o", File(outputDir, "$ytTemplate$suffix.%(ext)s").absolutePath)
                req.addOption("--no-playlist")
                req.addOption("--newline")
                req.addOption("--no-mtime")
                req.addOption("--no-cache-dir")
                req.addOption("--continue")
                req.addOption("--no-check-certificates")
                req.addOption("--geo-bypass")
                req.addOption("--socket-timeout", "30")
                req.addOption("--retries", "5")
                req.addOption("--fragment-retries", "5")
                req.addOption("--print", "after_move:filepath")
                req.addOption("--no-simulate")
                // Use fewer concurrent fragment sockets in Lite Mode to keep RAM & CPU low
                req.addOption("--concurrent-fragments", if (liteMode) "2" else "4")

                // Only apply HTTP Range chunking on YouTube; other CDNs (Instagram, TikTok, X,
                // Pinterest, Reddit) often reject forced Range requests with HTTP 403/416.
                if (platform == "YouTube") {
                    req.addOption("--http-chunk-size", if (liteMode) "4M" else "10M")
                } else if (platform != "TikTok") {
                    req.addOption("--user-agent", if (forceIpv4) MOBILE_USER_AGENT else DESKTOP_USER_AGENT)
                    req.addOption("--add-header", "Accept-Language:en-US,en;q=0.9")
                }

                if (forceIpv4) {
                    req.addOption("--force-ipv4")
                    if (platform == "YouTube") {
                        req.addOption("--extractor-args", "youtube:player_client=android,web")
                    }
                }

                // Point yt-dlp directly at the resolved bundled FFmpeg location when present
                (ffmpegBin ?: resolveFfmpegBinary())?.let { ffBin ->
                    req.addOption("--ffmpeg-location", ffBin.absolutePath)
                }

                // Enable Aria2c external multi-connection downloader when requested, initialized, and not trimming
                if (ariaReady && !hasTrim) {
                    req.addOption("--downloader", "libaria2c.so")
                    val ariaArgs = if (liteMode) "aria2c:-x 4 -s 4 -k 1M" else "aria2c:-x 8 -s 8 -k 1M"
                    req.addOption("--downloader-args", ariaArgs)
                }

                // Properly pair flag + argument values for YoutubeDLRequest
                applyArgsToRequest(req, args)
                return req
            }

            val callback: (Float, Long, String?) -> Unit = { progress, eta, line ->
                val clamped = progress.coerceIn(0f, 100f)
                activeJobs[jobId] = title to clamped
                refreshForegroundService(force = false)
                val speed = speedRegex.find(line ?: "")?.groupValues?.getOrNull(1)
                onEvent(
                    EngineEvent.Progress(
                        jobId = jobId,
                        percent = clamped.toDouble(),
                        etaSeconds = eta.takeIf { it > 0 },
                        speed = speed,
                        line = line,
                    ),
                )
            }

            val resp = try {
                YoutubeDL.getInstance().execute(buildDownloadRequest(forceIpv4 = false), jobId, callback)
            } catch (canceled: YoutubeDL.CanceledException) {
                throw canceled
            } catch (_: Throwable) {
                // Automatic IPv4 + fallback client retry before reporting failure
                YoutubeDL.getInstance().execute(buildDownloadRequest(forceIpv4 = true), jobId, callback)
            }

            val paths = collectOutputs(outputDir, resp.out, matchId, startedAt)
            if (paths.isNotEmpty()) {
                MediaScannerConnection.scanFile(appContext, paths.toTypedArray(), null, null)
            }
            onEvent(EngineEvent.Done(jobId, paths))
            DownloadService.notifyFinished(appContext, jobId, title, ok = true)
        } catch (_: YoutubeDL.CanceledException) {
            onEvent(EngineEvent.Canceled(jobId))
        } catch (t: Throwable) {
            val rawMsg = t.message ?: t.toString()
            val isNet = networkErrorRegex.containsMatchIn(rawMsg)
            val cleanMsg = cleanEngineError(t)
            onEvent(EngineEvent.Error(jobId, cleanMsg, isNet))
            if (!isNet) {
                DownloadService.notifyFinished(appContext, jobId, title, ok = false)
            }
        } finally {
            activeJobs.remove(jobId)
            refreshForegroundService(force = true)
        }
    }

    suspend fun extractAudio(
        jobId: String,
        input: File,
        format: String,
        title: String,
        onEvent: (EngineEvent) -> Unit,
    ) = withContext(Dispatchers.IO) {
        onEvent(EngineEvent.Starting(jobId))
        activeJobs[jobId] = title to 0f
        refreshForegroundService(force = true)
        try {
            val ffmpegBin = ensureFfmpegReady()
            if (!input.exists()) throw IllegalStateException("The file is no longer on the phone")
            val output = uniqueFile(input.parentFile ?: appContext.filesDir, input.nameWithoutExtension, format)
            val codec = when (format.lowercase(Locale.US)) {
                "m4a" -> listOf("-c:a", "aac", "-b:a", "192k")
                "opus" -> listOf("-c:a", "libopus", "-b:a", "160k")
                "flac" -> listOf("-c:a", "flac")
                else -> listOf("-c:a", "libmp3lame", "-q:a", "0")
            }
            val cmd = listOf(
                ffmpegBin.absolutePath,
                "-hide_banner", "-nostdin", "-y",
                "-i", input.absolutePath, "-vn", "-map", "0:a:0",
            ) + codec + output.absolutePath

            val process = buildFfmpegProcess(cmd).start()
            ffmpegProcesses[jobId] = process

            var totalSec = 0.0
            val log = StringBuilder()
            val durRegex = Regex("""Duration: (\d+):(\d+):([\d.]+)""")
            val timeRegex = Regex("""time=(\d+):(\d+):([\d.]+)""")

            process.inputStream.bufferedReader().forEachLine { line ->
                if (log.length < 8000) log.appendLine(line)
                durRegex.find(line)?.let { totalSec = parseSeconds(it) }
                timeRegex.find(line)?.let {
                    if (totalSec > 0.0) {
                        val p = (parseSeconds(it) / totalSec * 100.0).coerceIn(0.0, 100.0)
                        activeJobs[jobId] = title to p.toFloat()
                        refreshForegroundService()
                        onEvent(EngineEvent.Progress(jobId, p, null, null, line.trim()))
                    }
                }
            }

            val code = process.waitFor()
            val canceled = ffmpegProcesses.remove(jobId) == null
            when {
                canceled -> {
                    output.delete()
                    onEvent(EngineEvent.Canceled(jobId))
                }
                code != 0 -> {
                    output.delete()
                    val reason = log.lines().lastOrNull { it.isNotBlank() && !it.startsWith("size=") }
                        ?: "FFmpeg exited with $code"
                    val friendly = if (log.contains("does not contain any stream") || log.contains("matches no streams")) {
                        "This file has no audio track"
                    } else {
                        reason
                    }
                    throw IllegalStateException(friendly)
                }
                else -> {
                    MediaScannerConnection.scanFile(appContext, arrayOf(output.absolutePath), null, null)
                    onEvent(EngineEvent.Done(jobId, listOf(output.absolutePath)))
                    DownloadService.notifyFinished(appContext, jobId, title, ok = true)
                }
            }
        } catch (t: Throwable) {
            onEvent(EngineEvent.Error(jobId, t.message ?: t.toString(), false))
        } finally {
            ffmpegProcesses.remove(jobId)
            activeJobs.remove(jobId)
            refreshForegroundService(force = true)
        }
    }

    fun cancel(jobId: String): Boolean {
        val local = ffmpegProcesses.remove(jobId)
        if (local != null) {
            local.destroy()
            return true
        }
        return runCatching { YoutubeDL.getInstance().destroyProcessById(jobId) }.getOrDefault(false)
    }

    suspend fun mediaDurationSeconds(path: String): Double? = withContext(Dispatchers.IO) {
        runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(path)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()
                    ?.let { it / 1000.0 }
            } finally {
                retriever.release()
            }
        }.getOrNull()
    }

    /** Cuts [startSec, endSec] to AAC with smooth fade-in/out and saves into Ringtones/Getiy via MediaStore. */
    suspend fun makeRingtone(input: File, startSec: Double, endSec: Double, title: String): Uri =
        withContext(Dispatchers.IO) {
            if (!input.exists()) throw IllegalStateException("The file is no longer on the phone")
            val ffmpegBin = ensureFfmpegReady()
            val length = (endSec - startSec).coerceAtLeast(1.0)
            val tmp = File(appContext.cacheDir, "ringtone-${System.currentTimeMillis()}.m4a")
            val fadeOut = (length - 1.0).coerceAtLeast(0.0)
            val cmd = listOf(
                ffmpegBin.absolutePath,
                "-hide_banner", "-nostdin", "-y",
                "-ss", String.format(Locale.US, "%.2f", startSec),
                "-t", String.format(Locale.US, "%.2f", length),
                "-i", input.absolutePath, "-vn", "-map", "0:a:0",
                "-af", String.format(Locale.US, "afade=t=in:st=0:d=0.3,afade=t=out:st=%.2f:d=1", fadeOut),
                "-c:a", "aac", "-b:a", "192k", tmp.absolutePath,
            )
            val process = buildFfmpegProcess(cmd).start()
            val log = process.inputStream.bufferedReader().readText()
            if (process.waitFor() != 0 || !tmp.exists() || tmp.length() == 0L) {
                tmp.delete()
                val msg = if (log.contains("matches no streams") || log.contains("does not contain any stream")) {
                    "This file has no audio track"
                } else {
                    log.lines().lastOrNull { it.isNotBlank() } ?: "FFmpeg failed"
                }
                throw IllegalStateException(msg)
            }
            val cleanName = title.replace(Regex("""[\\/:*?"<>|]"""), " ")
                .take(60).trim().ifEmpty { "Getiy ringtone" }
            try {
                saveRingtoneToMediaStore(tmp, cleanName)
            } finally {
                tmp.delete()
            }
        }

    fun setDefaultRingtone(uri: Uri): Boolean {
        if (Settings.System.canWrite(appContext)) {
            RingtoneManager.setActualDefaultRingtoneUri(appContext, RingtoneManager.TYPE_RINGTONE, uri)
            return true
        }
        val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
            .setData(Uri.parse("package:${appContext.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { appContext.startActivity(intent) }
        return false
    }

    private fun saveRingtoneToMediaStore(file: File, name: String): Uri {
        val resolver = appContext.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "$name.m4a")
            put(MediaStore.Audio.Media.TITLE, name)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
            put(MediaStore.Audio.Media.IS_RINGTONE, true)
            put(MediaStore.Audio.Media.IS_MUSIC, false)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_RINGTONES}/Getiy")
            values.put(MediaStore.Audio.Media.IS_PENDING, 1)
            val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Couldn't save the ringtone")
            resolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { it.copyTo(out) }
            }
            values.clear()
            values.put(MediaStore.Audio.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri
        }
        @Suppress("DEPRECATION")
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_RINGTONES), "Getiy")
        dir.mkdirs()
        val target = File(dir, "$name.m4a")
        file.copyTo(target, overwrite = true)
        @Suppress("DEPRECATION")
        values.put(MediaStore.MediaColumns.DATA, target.absolutePath)
        return resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            ?: Uri.fromFile(target)
    }

    private fun collectOutputs(dir: File, out: String, matchId: String?, since: Long): List<String> {
        val printed = out.lines().map { it.trim() }.filter { it.isNotEmpty() && File(it).exists() }
        val keys = (printed.map { File(it).nameWithoutExtension + "." } + listOfNotNull(matchId))
            .filter { it.isNotEmpty() }
        val fresh = dir.listFiles().orEmpty()
            .filter { it.isFile && it.lastModified() >= since && !it.name.endsWith(".part") }
        var extra = fresh.filter { f -> keys.any { f.name.contains(it) } }.map { it.absolutePath }
        if (printed.isEmpty() && extra.isEmpty()) {
            extra = fresh.filter { it.extension.lowercase(Locale.US) in setOf("srt", "vtt", "ass") }
                .map { it.absolutePath }
        }
        return (printed + extra).distinct()
    }

    private fun parseSeconds(m: MatchResult): Double {
        val (h, min, s) = m.destructured
        return h.toDouble() * 3600.0 + min.toDouble() * 60.0 + s.toDouble()
    }

    private fun uniqueFile(dir: File, base: String, ext: String): File {
        var f = File(dir, "$base.$ext")
        var n = 1
        while (f.exists()) {
            f = File(dir, if (n == 1) "$base (audio).$ext" else "$base (audio $n).$ext")
            n++
        }
        return f
    }

    private fun refreshForegroundService(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && activeJobs.isNotEmpty() && now - lastNotificationUpdateMs < 1000L) {
            return
        }
        lastNotificationUpdateMs = now
        DownloadService.update(appContext, activeJobs.values.toList())
    }

    companion object {
        const val YOUTUBEDL_ANDROID_VERSION = "0.18.1"

        private const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
        private const val MOBILE_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

        private val DIRECT_MEDIA_URL_REGEX = Regex(
            """\.(mp4|mkv|webm|mov|m4v|m3u8|mpd|mp3|m4a|opus|ogg|wav|flac)(\?|$)""",
            RegexOption.IGNORE_CASE,
        )

        private val valueTakingFlags = setOf(
            "-f", "--format",
            "-S", "--format-sort",
            "-o", "--output",
            "--merge-output-format",
            "--audio-format",
            "--audio-quality",
            "--sub-langs",
            "--convert-subs",
            "--download-sections",
            "--downloader",
            "--downloader-args",
            "--cookies",
            "--print",
            "--playlist-items",
            "--concurrent-fragments",
            "--http-chunk-size",
            "--retries",
            "--fragment-retries",
            "--socket-timeout",
            "--ffmpeg-location",
            "--user-agent",
            "--referer",
            "--add-header",
            "--extractor-args",
        )

        /**
         * Extracts playable video metadata from OpenGraph / Twitter Card / HTML5 video tags
         * as a resilient fallback when a site blocks yt-dlp's API extractor.
         */
        internal fun extractMediaFromHtml(html: String, pageUrl: String, site: String?): MediaInfo? {
            fun metaValue(attrName: String): String? {
                val p1 = Regex(
                    """<meta[^>]+(?:property|name)=["']${Regex.escape(attrName)}["'][^>]+content=["']([^"']+)["']""",
                    RegexOption.IGNORE_CASE,
                )
                val p2 = Regex(
                    """<meta[^>]+content=["']([^"']+)["'][^>]+(?:property|name)=["']${Regex.escape(attrName)}["']""",
                    RegexOption.IGNORE_CASE,
                )
                val raw = p1.find(html)?.groupValues?.getOrNull(1)
                    ?: p2.find(html)?.groupValues?.getOrNull(1)
                    ?: return null
                return decodeHtmlEntities(raw).trim().takeIf { it.isNotBlank() }
            }

            val videoUrl = listOfNotNull(
                metaValue("og:video:secure_url"),
                metaValue("og:video:url"),
                metaValue("og:video"),
                metaValue("twitter:player:stream"),
                Regex(""""contentUrl"\s*:\s*"(https?:[^"]+\.(?:mp4|m3u8|webm)[^"]*)"""", RegexOption.IGNORE_CASE)
                    .find(html)?.groupValues?.getOrNull(1)?.replace("\\/", "/"),
                Regex("""<(?:video|source)[^>]+src=["'](https?://[^"']+\.(?:mp4|m3u8|webm)[^"']*)["']""", RegexOption.IGNORE_CASE)
                    .find(html)?.groupValues?.getOrNull(1)?.let(::decodeHtmlEntities),
            ).firstOrNull { it.startsWith("http", ignoreCase = true) } ?: return null

            val title = metaValue("og:title")
                ?: metaValue("twitter:title")
                ?: Regex("""<title[^>]*>([^<]+)</title>""", RegexOption.IGNORE_CASE)
                    .find(html)?.groupValues?.getOrNull(1)?.let(::decodeHtmlEntities)?.trim()
                ?: "Untitled video"
            val thumb = metaValue("og:image:secure_url") ?: metaValue("og:image")
            val height = metaValue("og:video:height")?.toIntOrNull()?.takeIf { it > 0 }
            val ext = inferExtFromUrlOrMime(videoUrl, "", isAudio = false)

            return MediaInfo(
                url = videoUrl,
                title = title,
                thumbnail = thumb,
                site = site,
                formats = listOf(
                    MediaFormat(
                        formatId = "og_video",
                        ext = ext,
                        height = height,
                        bytes = null,
                        hasVideo = true,
                        hasAudio = true,
                        url = videoUrl,
                        protocol = if (ext == "m3u8") "m3u8_native" else "https",
                    ),
                ),
            )
        }

        private fun inferExtFromUrlOrMime(url: String, mime: String, isAudio: Boolean): String {
            val pathExt = Regex("""\.([a-z0-9]{2,5})(?:\?|$)""", RegexOption.IGNORE_CASE)
                .find(url)?.groupValues?.getOrNull(1)?.lowercase(Locale.US)
            if (pathExt in setOf("mp4", "mkv", "webm", "mov", "m4v", "m3u8", "mp3", "m4a", "opus", "ogg", "wav", "flac")) {
                return pathExt!!
            }
            return when {
                mime.contains("mpegurl") -> "m3u8"
                mime.contains("webm") -> "webm"
                mime.contains("matroska") -> "mkv"
                mime.contains("mpeg") && isAudio -> "mp3"
                isAudio -> "m4a"
                else -> "mp4"
            }
        }

        private fun decodeHtmlEntities(s: String): String = s
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")

        /**
         * Pairs flag + argument tokens cleanly onto [YoutubeDLRequest] via addOption(flag, value)
         * instead of passing bare values as standalone option keys.
         */
        internal fun applyArgsToRequest(req: YoutubeDLRequest, args: List<String>) {
            var i = 0
            while (i < args.size) {
                val token = args[i]
                val next = args.getOrNull(i + 1)
                if (token in valueTakingFlags && next != null) {
                    req.addOption(token, next)
                    i += 2
                } else if (token.startsWith("-") && next != null && !next.startsWith("-")) {
                    req.addOption(token, next)
                    i += 2
                } else {
                    req.addOption(token)
                    i += 1
                }
            }
        }

        internal fun parseFfmpegVersionLine(line: String?): String? {
            if (line.isNullOrBlank()) return null
            val match = Regex("""ffmpeg version\s+([^\s]+)""", RegexOption.IGNORE_CASE).find(line)
            return match?.groupValues?.getOrNull(1)?.take(24)
        }

        internal fun cleanEngineError(t: Throwable): String {
            val raw = t.message?.trim().orEmpty()
            if (raw.isEmpty()) return t.javaClass.simpleName
            val errorLine = raw.lines()
                .map { it.trim() }
                .lastOrNull { it.startsWith("ERROR:", ignoreCase = true) }
            if (errorLine != null) {
                return errorLine.removePrefix("ERROR:").removePrefix("error:").trim()
            }
            return raw.lines()
                .map { it.trim() }
                .lastOrNull { it.isNotEmpty() }
                ?: raw
        }
    }
}
