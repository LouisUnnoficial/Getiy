package com.example.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.example.data.db.DownloadJobDao
import com.example.data.db.DownloadJobEntity
import com.example.data.engine.EngineEvent
import com.example.data.engine.GrabEngine
import com.example.data.model.DownloadMode
import com.example.data.model.JobStatus
import com.example.data.model.MediaHelpers
import com.example.data.model.MediaInfo
import com.example.data.model.SubtitleChoice
import com.example.data.model.TrimRange
import com.example.data.settings.AppSettings
import com.example.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class ExtractionProgress(
    val format: String,
    val progress: Double = 0.0,
    val running: Boolean = true,
    val error: String? = null,
)

data class DownloadCompletionEvent(
    val jobId: String,
    val title: String,
    val success: Boolean,
)

/**
 * Manages the Room-backed download queue, concurrency, pause/resume, automatic network-drop
 * recovery, and post-download audio extraction.
 */
class DownloadRepository(
    private val context: Context,
    private val dao: DownloadJobDao,
    val engine: GrabEngine,
    private val settingsRepository: SettingsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val jobsFlow: Flow<List<DownloadJobEntity>> = dao.observeAll()

    private val _speeds = MutableStateFlow<Map<String, String>>(emptyMap())
    val speeds: StateFlow<Map<String, String>> = _speeds.asStateFlow()

    private val _extractions = MutableStateFlow<Map<String, ExtractionProgress>>(emptyMap())
    val extractions: StateFlow<Map<String, ExtractionProgress>> = _extractions.asStateFlow()

    private val _completionEvents = MutableSharedFlow<DownloadCompletionEvent>(extraBufferCapacity = 16)
    val completionEvents: SharedFlow<DownloadCompletionEvent> = _completionEvents.asSharedFlow()

    private val runningIds = ConcurrentHashMap.newKeySet<String>()
    private val pausingIds = ConcurrentHashMap.newKeySet<String>()

    init {
        registerNetworkRecovery()
        scope.launch {
            recoverInterruptedJobs()
            pumpQueue()
        }
    }

    private suspend fun recoverInterruptedJobs() {
        val all = dao.getAllAsc()
        all.filter { it.jobStatus == JobStatus.RUNNING }.forEach { job ->
            dao.update(job.copy(status = JobStatus.QUEUED.name))
        }
    }

    private fun registerNetworkRecovery() {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        runCatching {
            cm.registerNetworkCallback(
                request,
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        scope.launch { resumeWaitingJobs() }
                    }
                },
            )
        }
    }

    private suspend fun resumeWaitingJobs() {
        val all = dao.getAllAsc()
        var resumedAny = false
        all.filter { it.jobStatus == JobStatus.WAITING }.forEach { job ->
            dao.update(job.copy(status = JobStatus.QUEUED.name, error = null))
            resumedAny = true
        }
        if (resumedAny) pumpQueue()
    }

    suspend fun enqueue(
        info: MediaInfo,
        mode: DownloadMode,
        maxHeight: Int?,
        container: String,
        audioFormat: String,
        subtitles: SubtitleChoice = SubtitleChoice.None,
        trim: TrimRange? = null,
    ): String {
        val settings = settingsRepository.settingsFlow.first()
        val id = UUID.randomUUID().toString()
        val args = MediaHelpers.buildDownloadArgs(
            mode = mode,
            maxHeight = maxHeight,
            container = container,
            audioFormat = audioFormat,
            subtitles = subtitles,
            trimStart = trim?.start,
            trimEnd = trim?.end,
        )
        val suffix = MediaHelpers.trimSuffix(trim?.start, trim?.end)
        val argsJson = JSONArray(args).toString()

        val entity = DownloadJobEntity(
            id = id,
            url = info.url,
            title = info.title,
            site = info.site,
            thumbnail = info.thumbnail,
            mediaId = info.id,
            mode = mode.name,
            maxHeight = maxHeight,
            container = container,
            audioFormat = audioFormat,
            argsJson = argsJson,
            suffix = suffix,
            template = settings.fileTemplate,
            status = JobStatus.QUEUED.name,
            progress = 0.0,
            eta = null,
            paths = "",
            error = null,
            attempts = 0,
            createdAt = System.currentTimeMillis(),
            finishedAt = null,
        )
        dao.insert(entity)
        pumpQueue()
        return id
    }

    suspend fun pause(jobId: String) {
        val job = dao.getById(jobId) ?: return
        pausingIds.add(jobId)
        dao.update(job.copy(status = JobStatus.PAUSED.name))
        if (runningIds.contains(jobId)) {
            engine.cancel(jobId)
        }
        _speeds.update { it - jobId }
    }

    suspend fun resume(jobId: String) {
        val job = dao.getById(jobId) ?: return
        pausingIds.remove(jobId)
        dao.update(job.copy(status = JobStatus.QUEUED.name, error = null))
        pumpQueue()
    }

    suspend fun cancel(jobId: String) {
        pausingIds.remove(jobId)
        val job = dao.getById(jobId) ?: return
        dao.update(job.copy(status = JobStatus.CANCELED.name, eta = null))
        engine.cancel(jobId)
        _speeds.update { it - jobId }
        pumpQueue()
    }

    suspend fun retry(jobId: String) {
        pausingIds.remove(jobId)
        val job = dao.getById(jobId) ?: return
        dao.update(
            job.copy(
                status = JobStatus.QUEUED.name,
                progress = 0.0,
                eta = null,
                error = null,
                attempts = 0,
            ),
        )
        pumpQueue()
    }

    suspend fun remove(jobId: String) {
        pausingIds.remove(jobId)
        if (runningIds.contains(jobId)) {
            engine.cancel(jobId)
        }
        dao.deleteById(jobId)
        _speeds.update { it - jobId }
    }

    suspend fun clearFinished() = dao.clearFinished()

    suspend fun clearFailed() = dao.clearFailed()

    suspend fun removeFileFromJob(jobId: String, filePath: String) {
        val job = dao.getById(jobId) ?: return
        val remaining = job.pathList.filterNot { it == filePath }
        dao.update(job.copy(paths = remaining.joinToString("\n")))
    }

    fun extractAudioFromJob(jobId: String, inputPath: String, format: String, title: String) {
        val extractId = "extract-$jobId-$format"
        if (_extractions.value[jobId]?.running == true) return
        _extractions.update { it + (jobId to ExtractionProgress(format = format, progress = 0.0, running = true)) }

        scope.launch {
            engine.extractAudio(
                jobId = extractId,
                input = File(inputPath),
                format = format,
                title = "$title ($format)",
            ) { event ->
                when (event) {
                    is EngineEvent.Starting -> Unit
                    is EngineEvent.Progress -> {
                        _extractions.update {
                            it + (jobId to ExtractionProgress(format = format, progress = event.percent, running = true))
                        }
                    }
                    is EngineEvent.Done -> {
                        scope.launch {
                            val current = dao.getById(jobId)
                            if (current != null && event.paths.isNotEmpty()) {
                                val merged = (current.pathList + event.paths).distinct()
                                dao.update(current.copy(paths = merged.joinToString("\n")))
                            }
                            _extractions.update { it - jobId }
                            _completionEvents.tryEmit(
                                DownloadCompletionEvent(jobId, "$title (${format.uppercase()})", true),
                            )
                        }
                    }
                    is EngineEvent.Canceled -> {
                        _extractions.update { it - jobId }
                    }
                    is EngineEvent.Error -> {
                        _extractions.update {
                            it + (jobId to ExtractionProgress(format = format, running = false, error = event.message))
                        }
                        _completionEvents.tryEmit(
                            DownloadCompletionEvent(jobId, title, false),
                        )
                    }
                }
            }
        }
    }

    private fun pumpQueue() {
        scope.launch {
            val settings: AppSettings = settingsRepository.settingsFlow.first()
            val maxConcurrent = settings.concurrency.coerceIn(1, 3)
            val queued = dao.getAllAsc().filter { it.jobStatus == JobStatus.QUEUED }

            for (job in queued) {
                if (runningIds.size >= maxConcurrent) break
                if (runningIds.add(job.id)) {
                    launchJob(job, settings)
                }
            }
        }
    }

    private fun launchJob(job: DownloadJobEntity, settings: AppSettings) {
        scope.launch {
            try {
                dao.update(job.copy(status = JobStatus.RUNNING.name, error = null))
                val args = parseArgs(job.argsJson)
                val outputDir = engine.resolveOutputDir(settings)

                engine.runDownload(
                    jobId = job.id,
                    url = job.url,
                    args = args,
                    suffix = job.suffix,
                    matchId = job.mediaId,
                    title = job.title,
                    template = job.template,
                    outputDir = outputDir,
                ) { event ->
                    handleEngineEvent(job.id, job.title, event)
                }
            } finally {
                runningIds.remove(job.id)
                _speeds.update { it - job.id }
                pumpQueue()
            }
        }
    }

    private fun handleEngineEvent(jobId: String, title: String, event: EngineEvent) {
        when (event) {
            is EngineEvent.Starting -> Unit
            is EngineEvent.Progress -> {
                if (event.speed != null) {
                    _speeds.update { it + (jobId to event.speed) }
                }
                scope.launch {
                    val current = dao.getById(jobId) ?: return@launch
                    if (current.jobStatus == JobStatus.RUNNING) {
                        dao.update(
                            current.copy(
                                progress = event.percent,
                                eta = event.etaSeconds,
                            ),
                        )
                    }
                }
            }
            is EngineEvent.Done -> {
                pausingIds.remove(jobId)
                _speeds.update { it - jobId }
                scope.launch {
                    val current = dao.getById(jobId) ?: return@launch
                    val merged = (current.pathList + event.paths).distinct()
                    dao.update(
                        current.copy(
                            status = JobStatus.DONE.name,
                            progress = 100.0,
                            eta = null,
                            paths = merged.joinToString("\n"),
                            error = null,
                            finishedAt = System.currentTimeMillis(),
                        ),
                    )
                    _completionEvents.tryEmit(DownloadCompletionEvent(jobId, title, true))
                }
            }
            is EngineEvent.Canceled -> {
                _speeds.update { it - jobId }
                val wasPause = pausingIds.remove(jobId)
                scope.launch {
                    val current = dao.getById(jobId) ?: return@launch
                    val nextStatus = if (wasPause) JobStatus.PAUSED else JobStatus.CANCELED
                    dao.update(current.copy(status = nextStatus.name, eta = null))
                }
            }
            is EngineEvent.Error -> {
                _speeds.update { it - jobId }
                if (pausingIds.remove(jobId)) return
                scope.launch {
                    val current = dao.getById(jobId) ?: return@launch
                    if (event.isNetworkError && current.attempts < MAX_AUTO_RETRIES) {
                        val nextAttempt = current.attempts + 1
                        dao.update(
                            current.copy(
                                status = JobStatus.WAITING.name,
                                attempts = nextAttempt,
                                eta = null,
                                error = event.message,
                            ),
                        )
                        // Schedule a polite backoff check in case network flickered briefly
                        delay(5000L * nextAttempt)
                        val refreshed = dao.getById(jobId)
                        if (refreshed != null && refreshed.jobStatus == JobStatus.WAITING) {
                            dao.update(refreshed.copy(status = JobStatus.QUEUED.name))
                            pumpQueue()
                        }
                    } else {
                        dao.update(
                            current.copy(
                                status = JobStatus.FAILED.name,
                                eta = null,
                                error = event.message,
                            ),
                        )
                        _completionEvents.tryEmit(DownloadCompletionEvent(jobId, title, false))
                    }
                }
            }
        }
    }

    private fun parseArgs(argsJson: String): List<String> = runCatching {
        val arr = JSONArray(argsJson)
        List(arr.length()) { arr.getString(it) }
    }.getOrDefault(emptyList())

    companion object {
        const val MAX_AUTO_RETRIES = 3
    }
}
