package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.AudioPlaybackManager
import com.example.core.AudioPlaybackState
import com.example.core.FileShareHelper
import com.example.core.GrabHaptics
import com.example.core.Links
import com.example.data.db.AppDatabase
import com.example.data.db.DownloadJobEntity
import com.example.data.engine.GrabEngine
import com.example.data.model.DownloadMode
import com.example.data.model.JobStatus
import com.example.data.model.LibraryItem
import com.example.data.model.LibraryKind
import com.example.data.model.LibrarySort
import com.example.data.model.MediaHelpers
import com.example.data.model.MediaInfo
import com.example.data.model.QualityOption
import com.example.data.model.SubtitleChoice
import com.example.data.model.TrimRange
import com.example.data.repository.DownloadRepository
import com.example.data.repository.ExtractionProgress
import com.example.data.settings.AppSettings
import com.example.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File

sealed class FetchResult {
    data object Idle : FetchResult()
    data class Loading(val url: String) : FetchResult()
    data class Ready(val info: MediaInfo) : FetchResult()
    data class Error(val url: String, val message: String) : FetchResult()
}

data class FetchUiState(
    val urlText: String = "",
    val result: FetchResult = FetchResult.Idle,
    val mode: DownloadMode = DownloadMode.VIDEO,
    val optionIndex: Int = 0,
    val subtitles: SubtitleChoice = SubtitleChoice.None,
    val trim: TrimRange? = null,
    val showFormatSheet: Boolean = false,
    val showPlaylistSheet: Boolean = false,
) {
    val currentInfo: MediaInfo?
        get() = (result as? FetchResult.Ready)?.info

    val options: List<QualityOption>
        get() = currentInfo?.optionsFor(mode) ?: emptyList()

    val selectedOption: QualityOption?
        get() = options.getOrNull(optionIndex.coerceIn(0, (options.size - 1).coerceAtLeast(0)))
}

data class ClipboardOfferState(
    val link: String,
    val platform: String,
)

data class PopToastMessage(
    val id: Long = System.currentTimeMillis(),
    val title: String,
    val detail: String? = null,
    val isError: Boolean = false,
)

sealed class ReleaseCheckState {
    data object Idle : ReleaseCheckState()
    data object Checking : ReleaseCheckState()
    data class UpToDate(val latestTag: String?) : ReleaseCheckState()
    data class Available(
        val tag: String,
        val name: String,
        val notes: String,
        val htmlUrl: String,
    ) : ReleaseCheckState()

    data class Error(val message: String) : ReleaseCheckState()
}

class GrabViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    val settingsRepository = SettingsRepository(appContext)
    val engine = GrabEngine(appContext)
    private val db = AppDatabase.getInstance(appContext)
    val repository = DownloadRepository(appContext, db.downloadJobDao(), engine, settingsRepository)
    private val httpClient = OkHttpClient()

    val settings: StateFlow<AppSettings> = settingsRepository.settingsFlow.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        AppSettings(),
    )

    val jobs: StateFlow<List<DownloadJobEntity>> = repository.jobsFlow.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        emptyList(),
    )

    val speeds: StateFlow<Map<String, String>> = repository.speeds
    val extractions: StateFlow<Map<String, ExtractionProgress>> = repository.extractions
    val audioState: StateFlow<AudioPlaybackState> = AudioPlaybackManager.state

    val activeDownloadCount: StateFlow<Int> = jobs.combine(speeds) { list, _ ->
        list.count { it.jobStatus.isActive }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    private val _libraryRefreshNonce = MutableStateFlow(0)
    val libraryItems: StateFlow<List<LibraryItem>> = combine(jobs, _libraryRefreshNonce) { list, _ ->
        buildLibraryItems(list)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _engineVersion = MutableStateFlow<String?>(null)
    val engineVersion: StateFlow<String?> = _engineVersion.asStateFlow()

    private val _fetchState = MutableStateFlow(FetchUiState())
    val fetchState: StateFlow<FetchUiState> = _fetchState.asStateFlow()

    private val _clipboardOffer = MutableStateFlow<ClipboardOfferState?>(null)
    val clipboardOffer: StateFlow<ClipboardOfferState?> = _clipboardOffer.asStateFlow()

    private val _toast = MutableStateFlow<PopToastMessage?>(null)
    val toast: StateFlow<PopToastMessage?> = _toast.asStateFlow()

    private val _confettiCounter = MutableStateFlow(0)
    val confettiCounter: StateFlow<Int> = _confettiCounter.asStateFlow()

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _releaseCheck = MutableStateFlow<ReleaseCheckState>(ReleaseCheckState.Idle)
    val releaseCheck: StateFlow<ReleaseCheckState> = _releaseCheck.asStateFlow()

    private val seenClipboardKeys = mutableSetOf<String>()
    private var offerDismissJob: Job? = null
    private var toastDismissJob: Job? = null
    private var fetchJob: Job? = null

    init {
        GrabHaptics.init(appContext)
        viewModelScope.launch {
            _engineVersion.value = runCatching { engine.initVersion() }.getOrElse { "bundled" }
        }
        viewModelScope.launch {
            repository.completionEvents.collect { event ->
                if (event.success) {
                    GrabHaptics.success()
                    _confettiCounter.update { it + 1 }
                    showToast("Saved", detail = event.title)
                    refreshLibrary()
                } else {
                    GrabHaptics.error()
                    showToast("Download failed", detail = event.title, isError = true)
                }
            }
        }
    }

    fun selectTab(index: Int) {
        if (_selectedTab.value != index) {
            GrabHaptics.selection()
            _selectedTab.value = index
        }
    }

    fun updateUrlText(text: String) {
        _fetchState.update { it.copy(urlText = text) }
    }

    fun completeWelcome() {
        viewModelScope.launch {
            settingsRepository.update { it.copy(seenWelcome = true) }
        }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            settingsRepository.update(transform)
        }
    }

    fun checkClipboardForOffer() {
        val raw = FileShareHelper.readClipboardText(appContext) ?: return
        val link = Links.extractLink(raw) ?: return
        val platform = Links.platformOf(link) ?: return
        val key = Links.linkKey(link)
        if (key in seenClipboardKeys) return
        seenClipboardKeys.add(key)

        _clipboardOffer.value = ClipboardOfferState(link = link, platform = platform)
        offerDismissJob?.cancel()
        offerDismissJob = viewModelScope.launch {
            delay(10_000)
            _clipboardOffer.value = null
        }
    }

    fun dismissClipboardOffer() {
        offerDismissJob?.cancel()
        _clipboardOffer.value = null
    }

    fun grabClipboardOffer() {
        val offer = _clipboardOffer.value ?: return
        dismissClipboardOffer()
        selectTab(0)
        fetchLink(offer.link)
    }

    fun pasteAndFetch() {
        val raw = FileShareHelper.readClipboardText(appContext)
        val link = Links.extractLink(raw) ?: raw?.trim()
        if (link.isNullOrBlank()) {
            showToast("Clipboard is empty", detail = "Copy a video link first")
            return
        }
        fetchLink(link)
    }

    fun fetchLink(rawInput: String = _fetchState.value.urlText) {
        val link = Links.extractLink(rawInput) ?: rawInput.trim()
        if (link.isBlank()) return
        seenClipboardKeys.add(Links.linkKey(link))
        dismissClipboardOffer()

        fetchJob?.cancel()
        _fetchState.update {
            it.copy(
                urlText = link,
                result = FetchResult.Loading(link),
                showFormatSheet = false,
                showPlaylistSheet = false,
                trim = null,
                subtitles = SubtitleChoice.None,
            )
        }

        fetchJob = viewModelScope.launch {
            runCatching { engine.fetch(link) }
                .onSuccess { info ->
                    val s = settings.value
                    val defaultMode = if (s.defaultMode == DownloadMode.SUBTITLES_ONLY && info.subtitles.isEmpty()) {
                        DownloadMode.VIDEO
                    } else {
                        s.defaultMode
                    }
                    val opts = info.optionsFor(defaultMode)
                    val defaultIdx = pickDefaultQualityIndex(opts, s.maxHeight)
                    GrabHaptics.light()
                    val isPlaylist = info.isPlaylist && info.entries.isNotEmpty()
                    _fetchState.update {
                        it.copy(
                            result = FetchResult.Ready(info),
                            mode = defaultMode,
                            optionIndex = defaultIdx,
                            trim = null,
                            subtitles = SubtitleChoice.None,
                            showFormatSheet = !isPlaylist,
                            showPlaylistSheet = isPlaylist,
                        )
                    }
                }
                .onFailure { err ->
                    GrabHaptics.error()
                    _fetchState.update {
                        it.copy(
                            result = FetchResult.Error(
                                url = link,
                                message = err.message ?: "Unable to read this link.",
                            ),
                        )
                    }
                }
        }
    }

    fun reopenFetchedSheet() {
        val info = _fetchState.value.currentInfo ?: return
        val isPlaylist = info.isPlaylist && info.entries.isNotEmpty()
        _fetchState.update {
            it.copy(
                showFormatSheet = !isPlaylist,
                showPlaylistSheet = isPlaylist,
            )
        }
    }

    fun closeSheets() {
        _fetchState.update { it.copy(showFormatSheet = false, showPlaylistSheet = false) }
    }

    fun setFetchMode(mode: DownloadMode) {
        val info = _fetchState.value.currentInfo ?: return
        val opts = info.optionsFor(mode)
        val idx = pickDefaultQualityIndex(opts, settings.value.maxHeight)
        _fetchState.update {
            it.copy(
                mode = mode,
                optionIndex = idx,
                trim = if (mode == DownloadMode.SUBTITLES_ONLY) null else it.trim,
            )
        }
    }

    fun setFetchOptionIndex(index: Int) {
        _fetchState.update { it.copy(optionIndex = index) }
    }

    fun setTrimEnabled(enabled: Boolean) {
        val dur = _fetchState.value.currentInfo?.duration ?: return
        if (dur < 2.0) return
        _fetchState.update {
            it.copy(trim = if (enabled) TrimRange(0.0, dur) else null)
        }
    }

    fun updateTrimRange(start: Double, end: Double) {
        val dur = _fetchState.value.currentInfo?.duration ?: return
        val s = start.coerceIn(0.0, (dur - 1.0).coerceAtLeast(0.0))
        val e = end.coerceIn((s + 1.0).coerceAtMost(dur), dur)
        _fetchState.update { it.copy(trim = TrimRange(s, e)) }
    }

    fun toggleSubtitleLang(lang: String) {
        _fetchState.update { state ->
            val current = state.subtitles.langs
            val next = if (lang in current) current - lang else current + lang
            state.copy(subtitles = state.subtitles.copy(langs = next))
        }
    }

    fun setSubtitleIncludeAuto(include: Boolean) {
        _fetchState.update { it.copy(subtitles = it.subtitles.copy(includeAuto = include)) }
    }

    fun setSubtitleEmbed(embed: Boolean) {
        _fetchState.update { it.copy(subtitles = it.subtitles.copy(embed = embed)) }
    }

    fun enqueueCurrentMedia() {
        val state = _fetchState.value
        val info = state.currentInfo ?: return
        val s = settings.value
        val opt = state.selectedOption
        viewModelScope.launch {
            repository.enqueue(
                info = info,
                mode = state.mode,
                maxHeight = if (state.mode == DownloadMode.AUDIO_ONLY || state.mode == DownloadMode.SUBTITLES_ONLY) null else opt?.height,
                container = s.container,
                audioFormat = s.audioFormat,
                subtitles = state.subtitles,
                trim = state.trim,
            )
            GrabHaptics.medium()
            showToast("Added to downloads", detail = info.title)
            _fetchState.update { it.copy(showFormatSheet = false) }
            _selectedTab.value = 1
        }
    }

    fun enqueuePlaylistItems(
        info: MediaInfo,
        selectedIndices: Set<Int>,
        mode: DownloadMode,
        maxHeight: Int?,
    ) {
        if (selectedIndices.isEmpty()) return
        val s = settings.value
        val sorted = selectedIndices.sorted()
        viewModelScope.launch {
            for (i in sorted) {
                val entry = info.entries.getOrNull(i) ?: continue
                repository.enqueue(
                    info = MediaInfo(
                        url = entry.url,
                        title = entry.title,
                        id = entry.id,
                        thumbnail = entry.thumbnail,
                        duration = entry.duration,
                        site = info.site,
                    ),
                    mode = mode,
                    maxHeight = if (mode == DownloadMode.AUDIO_ONLY) null else maxHeight,
                    container = s.container,
                    audioFormat = s.audioFormat,
                )
            }
            GrabHaptics.medium()
            val label = if (sorted.size == 1) "Added 1 download" else "Added ${sorted.size} downloads"
            showToast(label, detail = info.title)
            _fetchState.update { it.copy(showPlaylistSheet = false) }
            _selectedTab.value = 1
        }
    }

    fun pauseJob(jobId: String) = viewModelScope.launch { repository.pause(jobId) }
    fun resumeJob(jobId: String) = viewModelScope.launch { repository.resume(jobId) }
    fun cancelJob(jobId: String) = viewModelScope.launch { repository.cancel(jobId) }
    fun retryJob(jobId: String) = viewModelScope.launch { repository.retry(jobId) }
    fun removeJob(jobId: String) = viewModelScope.launch { repository.remove(jobId) }
    fun clearFinishedJobs() = viewModelScope.launch {
        repository.clearFinished()
        showToast("Cleared finished downloads")
    }

    fun clearFailedJobs() = viewModelScope.launch {
        repository.clearFailed()
        showToast("Cleared failed downloads")
    }

    fun extractAudio(job: DownloadJobEntity, inputPath: String, format: String) {
        repository.extractAudioFromJob(job.id, inputPath, format, job.title)
        showToast("Extracting ${format.uppercase()}", detail = job.title)
    }

    fun deleteFile(jobId: String?, path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            AudioPlaybackManager.forgetIfPlaying(appContext, path)
            runCatching { File(path).delete() }
            if (jobId != null) {
                repository.removeFileFromJob(jobId, path)
            }
            refreshLibrary()
        }
    }

    fun deleteFiles(paths: Set<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            var failed = 0
            for (path in paths) {
                AudioPlaybackManager.forgetIfPlaying(appContext, path)
                val deleted = runCatching { File(path).delete() }.getOrDefault(false)
                if (!deleted && File(path).exists()) failed++
            }
            refreshLibrary()
            withContext(Dispatchers.Main) {
                GrabHaptics.success()
                val n = paths.size
                showToast(
                    title = if (failed == 0) "Deleted $n" else "Deleted ${n - failed}, $failed failed",
                    isError = failed > 0,
                )
            }
        }
    }

    fun refreshLibrary() {
        _libraryRefreshNonce.update { it + 1 }
    }

    fun totalStorageBytes(): Long = libraryItems.value.sumOf { it.sizeBytes }

    fun resolveCurrentOutputPath(): String = engine.resolveOutputDir(settings.value).absolutePath

    fun playAudio(item: LibraryItem) {
        AudioPlaybackManager.play(appContext, item)
    }

    fun toggleAudioPlayPause() {
        AudioPlaybackManager.togglePlayPause(appContext)
    }

    fun stopAudio() {
        AudioPlaybackManager.stop(appContext)
    }

    fun seekAudio(ms: Long) = AudioPlaybackManager.seekTo(ms)
    fun skipAudio(deltaMs: Long) = AudioPlaybackManager.skipBy(deltaMs)
    fun cycleAudioSpeed() = AudioPlaybackManager.cycleSpeed()

    suspend fun makeRingtone(inputPath: String, start: Double, end: Double, title: String): Result<Uri> =
        runCatching { engine.makeRingtone(File(inputPath), start, end, title) }

    fun setRingtone(uri: Uri): Boolean = engine.setDefaultRingtone(uri)

    fun checkForGithubUpdates(currentVersion: String) {
        _releaseCheck.value = ReleaseCheckState.Checking
        viewModelScope.launch(Dispatchers.IO) {
            val res = runCatching {
                val req = Request.Builder()
                    .url("https://api.github.com/repos/Asta39/grab/releases/latest")
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "Grab-Android/$currentVersion")
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.code == 404) return@use ReleaseCheckState.UpToDate(null)
                    if (!resp.isSuccessful) throw IllegalStateException("GitHub returned HTTP ${resp.code}")
                    val body = resp.body?.string().orEmpty()
                    val obj = JSONObject(body)
                    val tag = obj.optString("tag_name")
                    val name = obj.optString("name")
                    val notes = obj.optString("body")
                    val htmlUrl = obj.optString("html_url", "https://github.com/Asta39/grab/releases/latest")
                    val cleanTag = tag.removePrefix("v").trim()
                    if (cleanTag.isBlank() || cleanTag == currentVersion.trim()) {
                        ReleaseCheckState.UpToDate(tag.ifBlank { null })
                    } else {
                        ReleaseCheckState.Available(tag, name, notes, htmlUrl)
                    }
                }
            }.getOrElse { ReleaseCheckState.Error(it.message ?: "Couldn't reach GitHub") }
            _releaseCheck.value = res
        }
    }

    fun showToast(title: String, detail: String? = null, isError: Boolean = false) {
        val msg = PopToastMessage(title = title, detail = detail, isError = isError)
        _toast.value = msg
        toastDismissJob?.cancel()
        toastDismissJob = viewModelScope.launch {
            delay(3200)
            if (_toast.value?.id == msg.id) _toast.value = null
        }
    }

    fun dismissToast() {
        toastDismissJob?.cancel()
        _toast.value = null
    }

    private fun pickDefaultQualityIndex(options: List<QualityOption>, maxHeight: Int?): Int {
        if (options.isEmpty()) return 0
        if (maxHeight == null) return 0
        val idx = options.indexOfFirst { it.height != null && it.height <= maxHeight }
        return if (idx >= 0) idx else 0
    }

    private fun buildLibraryItems(jobs: List<DownloadJobEntity>): List<LibraryItem> {
        val items = mutableListOf<LibraryItem>()
        val seen = mutableSetOf<String>()
        for (job in jobs) {
            if (job.jobStatus != JobStatus.DONE) continue
            for (path in job.pathList) {
                if (!seen.add(path)) continue
                val f = File(path)
                if (!f.exists() || !f.isFile) continue
                items.add(
                    LibraryItem(
                        jobId = job.id,
                        path = path,
                        title = job.title,
                        site = job.site,
                        thumbnail = job.thumbnail,
                        kind = MediaHelpers.kindOf(path),
                        finishedAt = job.finishedAt ?: job.createdAt,
                        sizeBytes = runCatching { f.length() }.getOrDefault(0L),
                    ),
                )
            }
        }
        return items
    }
}
