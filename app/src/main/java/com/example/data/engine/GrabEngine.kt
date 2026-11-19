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
import com.example.data.model.MediaHelpers
import com.example.data.model.MediaInfo
import com.example.data.settings.AppSettings
import com.example.service.DownloadService
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
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

/**
 * Native Android yt-dlp & FFmpeg engine powered by youtubedl-android.
 * Ported directly from Asta39/grab's EnginePlugin.kt and RingtonePlugin.kt.
 */
class GrabEngine(private val context: Context) {

    @Volatile
    private var initialized = false

    private val activeJobs = ConcurrentHashMap<String, Pair<String, Float>>()
    private val ffmpegProcesses = ConcurrentHashMap<String, Process>()

    private val speedRegex = Regex("""at\s+([\d.]+\s*[KMG]?i?B/s)""")
    private val networkErrorRegex = Regex(
        "Unable to download|timed out|time out|Connection (reset|refused|aborted)|Network is unreachable|" +
            "No route to host|Temporary failure in name resolution|getaddrinfo|Name or service not known|" +
            "No address associated|Remote end closed|IncompleteRead|Software caused connection abort|HTTP Error 5\\d\\d|SSL|EOF occurred",
        RegexOption.IGNORE_CASE,
    )

    @Synchronized
    private fun ensureReady() {
        if (initialized) return
        YoutubeDL.getInstance().init(context.applicationContext)
        FFmpeg.getInstance().init(context.applicationContext)
        initialized = true
    }

    suspend fun initVersion(): String = withContext(Dispatchers.IO) {
        ensureReady()
        YoutubeDL.getInstance().versionName(context.applicationContext)
            ?: runCatching {
                YoutubeDL.getInstance().execute(
                    YoutubeDLRequest(emptyList()).apply { addOption("--version") },
                ).out.trim()
            }.getOrDefault("bundled")
    }

    fun resolveOutputDir(settings: AppSettings): File {
        val envDir = when (settings.saveBase) {
            "movies" -> Environment.DIRECTORY_MOVIES
            "music" -> Environment.DIRECTORY_MUSIC
            else -> Environment.DIRECTORY_DOWNLOADS
        }
        val publicRoot = Environment.getExternalStoragePublicDirectory(envDir)
        val target = File(publicRoot, settings.saveFolder)
        val writable = runCatching {
            if (!target.exists()) target.mkdirs()
            target.exists() && target.canWrite()
        }.getOrDefault(false)
        if (writable) return target

        // Fallback to app-scoped external storage if public folder is restricted
        val appFallback = File(context.getExternalFilesDir(envDir) ?: context.filesDir, settings.saveFolder)
        if (!appFallback.exists()) appFallback.mkdirs()
        return appFallback
    }

    suspend fun fetch(url: String): MediaInfo = withContext(Dispatchers.IO) {
        ensureReady()
        val req = YoutubeDLRequest(url)
        val jar = File(context.cacheDir, "preview-cookies.txt")
        jar.writeText("# Netscape HTTP Cookie File\n")
        req.addOption("--cookies", jar.absolutePath)
        req.addOption("--dump-single-json")
        req.addOption("--no-warnings")
        req.addOption("--flat-playlist")
        req.addOption("--playlist-items", "1:300")

        val out = YoutubeDL.getInstance().execute(req).out
        val combined = out + "\n\u0000GRAB-COOKIES\u0000\n" + if (jar.exists()) jar.readText() else ""
        MediaHelpers.parseMediaInfoJson(combined, url)
    }

    /** Downloads a lightweight cached MP4 preview when sites refuse direct stream playback (e.g. TikTok). */
    suspend fun previewFile(url: String): String? = withContext(Dispatchers.IO) {
        ensureReady()
        val dir = File(context.cacheDir, "preview").apply {
            deleteRecursively()
            mkdirs()
        }
        val req = YoutubeDLRequest(url)
        req.addOption("-f", "w[vcodec!=none][acodec!=none][ext=mp4]/w[vcodec!=none][acodec!=none]/w")
        req.addOption("-o", File(dir, "preview.%(ext)s").absolutePath)
        req.addOption("--no-playlist")
        req.addOption("--no-part")
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
        onEvent: (EngineEvent) -> Unit,
    ) = withContext(Dispatchers.IO) {
        onEvent(EngineEvent.Starting(jobId))
        activeJobs[jobId] = title to 0f
        refreshForegroundService()
        val startedAt = System.currentTimeMillis() - 1000L
        try {
            ensureReady()
            if (!outputDir.exists()) outputDir.mkdirs()

            val ytTemplate = if (template.isBlank()) "%(title).80B [%(id)s]" else MediaHelpers.ytDlpTemplate(template)
            val req = YoutubeDLRequest(url)
            req.addOption("-o", File(outputDir, "$ytTemplate$suffix.%(ext)s").absolutePath)
            req.addOption("--no-playlist")
            req.addOption("--newline")
            req.addOption("--no-mtime")
            req.addOption("--print", "after_move:filepath")
            req.addOption("--no-simulate")
            req.addOption("--concurrent-fragments", "4")
            req.addOption("--http-chunk-size", "10M")
            args.forEach { req.addOption(it) }

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
                MediaScannerConnection.scanFile(context, paths.toTypedArray(), null, null)
            }
            onEvent(EngineEvent.Done(jobId, paths))
            DownloadService.notifyFinished(context, jobId, title, ok = true)
        } catch (_: YoutubeDL.CanceledException) {
            onEvent(EngineEvent.Canceled(jobId))
        } catch (t: Throwable) {
            val msg = t.message ?: t.toString()
            val isNet = networkErrorRegex.containsMatchIn(msg)
            onEvent(EngineEvent.Error(jobId, msg, isNet))
            if (!isNet) {
                DownloadService.notifyFinished(context, jobId, title, ok = false)
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
            ensureReady()
            if (!input.exists()) throw IllegalStateException("The file is no longer on the phone")
            val output = uniqueFile(input.parentFile ?: context.filesDir, input.nameWithoutExtension, format)
            val codec = when (format.lowercase()) {
                "m4a" -> listOf("-c:a", "aac", "-b:a", "192k")
                "opus" -> listOf("-c:a", "libopus", "-b:a", "160k")
                "flac" -> listOf("-c:a", "flac")
                else -> listOf("-c:a", "libmp3lame", "-q:a", "0")
            }
            val cmd = listOf(
                File(context.applicationInfo.nativeLibraryDir, "libffmpeg.so").absolutePath,
                "-hide_banner", "-nostdin", "-y",
                "-i", input.absolutePath, "-vn", "-map", "0:a:0",
            ) + codec + output.absolutePath

            val packages = File(context.noBackupFilesDir, "youtubedl-android/packages")
            val pb = ProcessBuilder(cmd).redirectErrorStream(true)
            pb.environment()["LD_LIBRARY_PATH"] =
                "${File(packages, "python/usr/lib").absolutePath}:${File(packages, "ffmpeg/usr/lib").absolutePath}"
            val process = pb.start()
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
                    MediaScannerConnection.scanFile(context, arrayOf(output.absolutePath), null, null)
                    onEvent(EngineEvent.Done(jobId, listOf(output.absolutePath)))
                    DownloadService.notifyFinished(context, jobId, title, ok = true)
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

    /** Cuts [startSec, endSec] to AAC with smooth fade-in/out and saves into Ringtones/Grab via MediaStore. */
    suspend fun makeRingtone(input: File, startSec: Double, endSec: Double, title: String): Uri =
        withContext(Dispatchers.IO) {
            if (!input.exists()) throw IllegalStateException("The file is no longer on the phone")
            ensureReady()
            val length = (endSec - startSec).coerceAtLeast(1.0)
            val tmp = File(context.cacheDir, "ringtone-${System.currentTimeMillis()}.m4a")
            val fadeOut = (length - 1.0).coerceAtLeast(0.0)
            val cmd = listOf(
                File(context.applicationInfo.nativeLibraryDir, "libffmpeg.so").absolutePath,
                "-hide_banner", "-nostdin", "-y",
                "-ss", String.format("%.2f", startSec),
                "-t", String.format("%.2f", length),
                "-i", input.absolutePath, "-vn", "-map", "0:a:0",
                "-af", String.format("afade=t=in:st=0:d=0.3,afade=t=out:st=%.2f:d=1", fadeOut),
                "-c:a", "aac", "-b:a", "192k", tmp.absolutePath,
            )
            val packages = File(context.noBackupFilesDir, "youtubedl-android/packages")
            val pb = ProcessBuilder(cmd).redirectErrorStream(true)
            pb.environment()["LD_LIBRARY_PATH"] =
                "${File(packages, "python/usr/lib").absolutePath}:${File(packages, "ffmpeg/usr/lib").absolutePath}"
            val process = pb.start()
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
                .take(60).trim().ifEmpty { "Grab ringtone" }
            try {
                saveRingtoneToMediaStore(tmp, cleanName)
            } finally {
                tmp.delete()
            }
        }

    fun setDefaultRingtone(uri: Uri): Boolean {
        if (Settings.System.canWrite(context)) {
            RingtoneManager.setActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE, uri)
            return true
        }
        val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
            .setData(Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
        return false
    }

    private fun saveRingtoneToMediaStore(file: File, name: String): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "$name.m4a")
            put(MediaStore.Audio.Media.TITLE, name)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
            put(MediaStore.Audio.Media.IS_RINGTONE, true)
            put(MediaStore.Audio.Media.IS_MUSIC, false)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_RINGTONES}/Grab")
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
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_RINGTONES), "Grab")
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
            extra = fresh.filter { it.extension.lowercase() in setOf("srt", "vtt", "ass") }
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
        DownloadService.update(context, activeJobs.values.toList())
    }
}
