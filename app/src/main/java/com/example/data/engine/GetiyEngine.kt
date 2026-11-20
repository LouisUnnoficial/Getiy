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
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

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
    val initializing: Boolean = true,
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
            else -> error ?: "Engine initialization incomplete"
        }
}

/**
 * Native Android yt-dlp, FFmpeg, and Aria2c engine powered by youtubedl-android.
 * All native binaries are packaged directly inside the APK (zero dynamic code loading).
 */
class GetiyEngine(private val context: Context) {

    private val appContext = context.applicationContext
    private val initMutex = Mutex()

    @Volatile
    private var initialized = false

    private val _engineStatus = MutableStateFlow(EngineStatus(initializing = true))
    val engineStatus: StateFlow<EngineStatus> = _engineStatus.asStateFlow()

    private val activeJobs = ConcurrentHashMap<String, Pair<String, Float>>()
    private val ffmpegProcesses = ConcurrentHashMap<String, Process>()

    private val speedRegex = Regex("""at\s+([\d.]+\s*[KMG]?i?B/s)""")
    private val networkErrorRegex = Regex(
        "Unable to download|timed out|time out|Connection (reset|refused|aborted)|Network is unreachable|" +
            "No route to host|Temporary failure in name resolution|getaddrinfo|Name or service not known|" +
            "No address associated|Remote end closed|IncompleteRead|Software caused connection abort|HTTP Error 5\\d\\d|SSL|EOF occurred",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Initializes youtubedl-android (yt-dlp + Python), FFmpeg, and Aria2c and probes their binaries.
     * Safe to call repeatedly; retries cleanly if a previous attempt failed or [force] is true.
     */
    suspend fun initializeEngines(force: Boolean = false): EngineStatus = withContext(Dispatchers.IO) {
        initMutex.withLock {
            if (initialized && !force && _engineStatus.value.allCoreReady) {
                return@withLock _engineStatus.value
            }
            _engineStatus.value = _engineStatus.value.copy(initializing = true, error = null)

            var ytReady = false
            var ytVersion: String? = null
            var ffReady = false
            var ffVersion: String? = null
            var ariaReady = false
            val errors = mutableListOf<String>()

            // 1. Initialize core yt-dlp + Python runtime (youtubedl-android:library)
            runCatching {
                YoutubeDL.getInstance().init(appContext)
                ytReady = true
                ytVersion = probeYtDlpVersion()
            }.onFailure { err ->
                errors.add("yt-dlp: ${cleanEngineError(err)}")
            }

            // 2. Initialize bundled FFmpeg runtime (youtubedl-android:ffmpeg)
            runCatching {
                FFmpeg.getInstance().init(appContext)
                ffVersion = probeFfmpegVersion()
                ffReady = resolveFfmpegBinary() != null || ffVersion != null
                if (!ffReady) {
                    errors.add("FFmpeg binary could not be located after initialization")
                }
            }.onFailure { err ->
                errors.add("FFmpeg: ${cleanEngineError(err)}")
            }

            // 3. Initialize optional Aria2c multi-connection engine (youtubedl-android:aria2c)
            ariaReady = runCatching {
                Aria2c.getInstance().init(appContext)
                true
            }.getOrDefault(false)

            initialized = ytReady && ffReady
            val status = EngineStatus(
                initializing = false,
                ytDlpReady = ytReady,
                ytDlpVersion = ytVersion,
                ffmpegReady = ffReady,
                ffmpegVersion = ffVersion,
                aria2cReady = ariaReady,
                error = errors.takeIf { it.isNotEmpty() }?.joinToString(" · "),
            )
            _engineStatus.value = status
            status
        }
    }

    private suspend fun ensureReady() {
        val status = initializeEngines(force = false)
        if (!status.ytDlpReady) {
            throw IllegalStateException(status.error ?: "yt-dlp engine failed to initialize")
        }
    }

    private suspend fun ensureFfmpegReady(): File {
        val status = initializeEngines(force = false)
        val binary = resolveFfmpegBinary()
        if (!status.ffmpegReady || binary == null) {
            throw IllegalStateException(
                status.error ?: "FFmpeg binary not found. Open Settings → Verify engines to re-initialize.",
            )
        }
        return binary
    }

    suspend fun initVersion(): String {
        val status = initializeEngines(force = false)
        return status.ytDlpVersion ?: "bundled"
    }

    private fun probeYtDlpVersion(): String {
        val fromLibrary = runCatching {
            YoutubeDL.getInstance().versionName(appContext)?.trim()
        }.getOrNull()
        if (!fromLibrary.isNullOrBlank()) return fromLibrary

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

    suspend fun fetch(url: String): MediaInfo = withContext(Dispatchers.IO) {
        ensureReady()
        val cleanUrl = Links.stripTrackingParams(url)
        val jar = File(appContext.cacheDir, "cookies-${UUID.randomUUID()}.txt")
        try {
            jar.writeText("# Netscape HTTP Cookie File\n")
            val req = YoutubeDLRequest(cleanUrl)
            req.addOption("--cookies", jar.absolutePath)
            req.addOption("--no-cache-dir")
            req.addOption("--dump-single-json")
            req.addOption("--no-warnings")
            req.addOption("--no-check-certificates")
            req.addOption("--socket-timeout", "30")
            req.addOption("--retries", "3")
            req.addOption("--flat-playlist")
            req.addOption("--playlist-items", "1:300")

            val out = YoutubeDL.getInstance().execute(req).out
            val cookiesText = if (jar.exists()) jar.readText() else ""
            val combined = out + "\n\u0000GETIY-COOKIES\u0000\n" + cookiesText
            MediaHelpers.parseMediaInfoJson(combined, cleanUrl)
        } catch (t: Throwable) {
            throw IllegalStateException(cleanEngineError(t), t)
        } finally {
            runCatching { jar.delete() }
        }
    }

    /** Downloads a lightweight cached MP4 preview when sites refuse direct stream playback (e.g. TikTok). */
    suspend fun previewFile(url: String): String? = withContext(Dispatchers.IO) {
        ensureReady()
        val cleanUrl = Links.stripTrackingParams(url)
        val dir = File(appContext.cacheDir, "preview").apply {
            deleteRecursively()
            mkdirs()
        }
        val req = YoutubeDLRequest(cleanUrl)
        req.addOption("-f", "w[vcodec!=none][acodec!=none][ext=mp4]/w[vcodec!=none][acodec!=none]/w")
        req.addOption("-o", File(dir, "preview.%(ext)s").absolutePath)
        req.addOption("--no-playlist")
        req.addOption("--no-part")
        req.addOption("--no-cache-dir")
        req.addOption("--no-check-certificates")
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
        onEvent: (EngineEvent) -> Unit,
    ) = withContext(Dispatchers.IO) {
        onEvent(EngineEvent.Starting(jobId))
        activeJobs[jobId] = title to 0f
        refreshForegroundService()
        val startedAt = System.currentTimeMillis() - 1000L
        try {
            ensureReady()
            if (!outputDir.exists()) outputDir.mkdirs()

            val cleanUrl = Links.stripTrackingParams(url)
            val ytTemplate = if (template.isBlank()) "%(title).80B [%(id)s]" else MediaHelpers.ytDlpTemplate(template)
            val req = YoutubeDLRequest(cleanUrl)
            req.addOption("-o", File(outputDir, "$ytTemplate$suffix.%(ext)s").absolutePath)
            req.addOption("--no-playlist")
            req.addOption("--newline")
            req.addOption("--no-mtime")
            req.addOption("--no-cache-dir")
            req.addOption("--continue")
            req.addOption("--no-check-certificates")
            req.addOption("--socket-timeout", "30")
            req.addOption("--retries", "5")
            req.addOption("--fragment-retries", "5")
            req.addOption("--print", "after_move:filepath")
            req.addOption("--no-simulate")
            req.addOption("--concurrent-fragments", "4")
            req.addOption("--http-chunk-size", "10M")

            // Point yt-dlp directly at the resolved bundled FFmpeg location when present
            resolveFfmpegBinary()?.let { ffBin ->
                req.addOption("--ffmpeg-location", ffBin.absolutePath)
            }

            // Enable Aria2c external multi-connection downloader when requested, initialized, and not trimming
            val hasTrim = args.any { it == "--download-sections" }
            if (useAria2c && _engineStatus.value.aria2cReady && !hasTrim) {
                req.addOption("--downloader", "libaria2c.so")
                req.addOption("--downloader-args", "aria2c:-x 8 -s 8 -k 1M")
            }

            // Properly pair flag + argument values for YoutubeDLRequest
            applyArgsToRequest(req, args)

            val resp = YoutubeDL.getInstance().execute(req, jobId) { progress, eta, line ->
                val clamped = progress.coerceIn(0f, 100f)
                activeJobs[jobId] = title to clamped
                refreshForegroundService()
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
            refreshForegroundService()
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
        refreshForegroundService()
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
            refreshForegroundService()
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

    private fun refreshForegroundService() {
        DownloadService.update(appContext, activeJobs.values.toList())
    }

    companion object {
        const val YOUTUBEDL_ANDROID_VERSION = "0.18.1"

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
        )

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
