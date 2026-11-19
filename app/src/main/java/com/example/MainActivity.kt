package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.core.Links
import com.example.data.model.AppThemeMode
import com.example.data.model.JobStatus
import com.example.data.model.LibraryItem
import com.example.service.GrabTileService
import com.example.ui.components.ClipboardOfferCard
import com.example.ui.components.PopConfettiOverlay
import com.example.ui.components.PopTabBar
import com.example.ui.components.PopTabItem
import com.example.ui.components.PopToastHost
import com.example.ui.screens.AboutScreen
import com.example.ui.screens.AudioPlayerScreen
import com.example.ui.screens.DownloadDetailScreen
import com.example.ui.screens.DownloadsScreen
import com.example.ui.screens.FormatBottomSheet
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.MiniPlayerBar
import com.example.ui.screens.PlaylistBottomSheet
import com.example.ui.screens.RingtoneBottomSheet
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.VideoPlayerScreen
import com.example.ui.screens.WelcomeScreen
import com.example.ui.theme.GrabTheme
import com.example.ui.viewmodel.GrabViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: GrabViewModel by viewModels()
    private var pendingClipboardGrab = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingIntent(intent)

        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (settings.themeMode) {
                AppThemeMode.SYSTEM -> systemDark
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
            }

            var detailJobId by remember { mutableStateOf<String?>(null) }
            var videoPlayerItem by remember { mutableStateOf<LibraryItem?>(null) }
            var showAudioPlayer by remember { mutableStateOf(false) }
            var showAbout by remember { mutableStateOf(false) }
            var ringtoneItem by remember { mutableStateOf<LibraryItem?>(null) }

            val yellowTopBar = !settings.seenWelcome || showAbout
            LaunchedEffect(darkTheme, yellowTopBar, videoPlayerItem) {
                val useDarkIcons = when {
                    videoPlayerItem != null -> false
                    yellowTopBar -> true
                    else -> !darkTheme
                }
                val transparent = Color.Transparent.toArgb()
                enableEdgeToEdge(
                    statusBarStyle = if (useDarkIcons) {
                        SystemBarStyle.light(transparent, transparent)
                    } else {
                        SystemBarStyle.dark(transparent)
                    },
                    navigationBarStyle = if (darkTheme) {
                        SystemBarStyle.dark(transparent)
                    } else {
                        SystemBarStyle.light(transparent, transparent)
                    },
                )
            }

            GrabTheme(themeMode = settings.themeMode) {
                GrabAppRoot(
                    viewModel = viewModel,
                    detailJobId = detailJobId,
                    videoPlayerItem = videoPlayerItem,
                    showAudioPlayer = showAudioPlayer,
                    showAbout = showAbout,
                    ringtoneItem = ringtoneItem,
                    onOpenDetail = { detailJobId = it },
                    onCloseDetail = { detailJobId = null },
                    onOpenVideo = { videoPlayerItem = it },
                    onCloseVideo = { videoPlayerItem = null },
                    onOpenAudio = { showAudioPlayer = true },
                    onCloseAudio = { showAudioPlayer = false },
                    onOpenAbout = { showAbout = true },
                    onCloseAbout = { showAbout = false },
                    onOpenRingtone = { ringtoneItem = it },
                    onCloseRingtone = { ringtoneItem = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) return
        if (pendingClipboardGrab) {
            pendingClipboardGrab = false
            lifecycleScope.launch {
                delay(220)
                viewModel.selectTab(0)
                viewModel.pasteAndFetch()
            }
        } else {
            viewModel.checkClipboardForOffer()
        }
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val shared = intent.getStringExtra(Intent.EXTRA_TEXT)
                val link = Links.extractLink(shared)
                if (!link.isNullOrBlank()) {
                    viewModel.completeWelcome()
                    viewModel.selectTab(0)
                    viewModel.fetchLink(link)
                }
            }
            GrabTileService.ACTION_GRAB_CLIPBOARD -> {
                viewModel.completeWelcome()
                pendingClipboardGrab = true
            }
        }
    }
}

@Composable
private fun GrabAppRoot(
    viewModel: GrabViewModel,
    detailJobId: String?,
    videoPlayerItem: LibraryItem?,
    showAudioPlayer: Boolean,
    showAbout: Boolean,
    ringtoneItem: LibraryItem?,
    onOpenDetail: (String) -> Unit,
    onCloseDetail: () -> Unit,
    onOpenVideo: (LibraryItem) -> Unit,
    onCloseVideo: () -> Unit,
    onOpenAudio: () -> Unit,
    onCloseAudio: () -> Unit,
    onOpenAbout: () -> Unit,
    onCloseAbout: () -> Unit,
    onOpenRingtone: (LibraryItem) -> Unit,
    onCloseRingtone: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val jobs by viewModel.jobs.collectAsStateWithLifecycle()
    val speeds by viewModel.speeds.collectAsStateWithLifecycle()
    val extractions by viewModel.extractions.collectAsStateWithLifecycle()
    val libraryItems by viewModel.libraryItems.collectAsStateWithLifecycle()
    val activeCount by viewModel.activeDownloadCount.collectAsStateWithLifecycle()
    val engineVersion by viewModel.engineVersion.collectAsStateWithLifecycle()
    val fetchState by viewModel.fetchState.collectAsStateWithLifecycle()
    val clipboardOffer by viewModel.clipboardOffer.collectAsStateWithLifecycle()
    val toast by viewModel.toast.collectAsStateWithLifecycle()
    val confettiCount by viewModel.confettiCounter.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val audioState by viewModel.audioState.collectAsStateWithLifecycle()
    val releaseCheck by viewModel.releaseCheck.collectAsStateWithLifecycle()

    val hasMiniPlayer = audioState.current != null
    val extraBottom = if (hasMiniPlayer) 72 else 0

    Box(modifier = Modifier.fillMaxSize()) {
        if (!settings.seenWelcome) {
            WelcomeScreen(onDone = { viewModel.completeWelcome() })
        } else {
            // Main 4-tab shell
            Box(modifier = Modifier.fillMaxSize()) {
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(160)) },
                    label = "tabContent",
                ) { tab ->
                    when (tab) {
                        0 -> HomeScreen(
                            fetchState = fetchState,
                            recentJobs = jobs,
                            engineVersion = engineVersion,
                            extraBottomPadding = extraBottom,
                            onUrlChange = viewModel::updateUrlText,
                            onPasteAndFetch = viewModel::pasteAndFetch,
                            onFetch = { viewModel.fetchLink() },
                            onReopenReady = viewModel::reopenFetchedSheet,
                            onOpenJobDetail = onOpenDetail,
                            onCopyError = { viewModel.showToast("Error copied") },
                        )
                        1 -> DownloadsScreen(
                            jobs = jobs,
                            speeds = speeds,
                            extraBottomPadding = extraBottom,
                            onPause = viewModel::pauseJob,
                            onResume = viewModel::resumeJob,
                            onCancel = viewModel::cancelJob,
                            onRetry = viewModel::retryJob,
                            onRemove = viewModel::removeJob,
                            onClearFinished = viewModel::clearFinishedJobs,
                            onOpenDetail = onOpenDetail,
                        )
                        2 -> LibraryScreen(
                            items = libraryItems,
                            sort = settings.librarySort,
                            groupBySite = settings.libraryGroupBySite,
                            extraBottomPadding = extraBottom,
                            onSortChange = { s -> viewModel.updateSettings { it.copy(librarySort = s) } },
                            onGroupChange = { g -> viewModel.updateSettings { it.copy(libraryGroupBySite = g) } },
                            onPlayVideo = onOpenVideo,
                            onPlayAudio = { item ->
                                viewModel.playAudio(item)
                                onOpenAudio()
                            },
                            onOpenDetail = onOpenDetail,
                            onDeleteSelected = viewModel::deleteFiles,
                        )
                        else -> SettingsScreen(
                            settings = settings,
                            engineVersion = engineVersion,
                            storageBytes = libraryItems.sumOf { it.sizeBytes },
                            failedCount = jobs.count {
                                it.jobStatus == JobStatus.FAILED || it.jobStatus == JobStatus.CANCELED
                            },
                            extraBottomPadding = extraBottom,
                            onUpdateSettings = viewModel::updateSettings,
                            onClearFailed = viewModel::clearFailedJobs,
                            onOpenAbout = onOpenAbout,
                        )
                    }
                }

                // Floating Clipboard Offer + Mini Audio Player above the dock
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(start = 16.dp, end = 16.dp, bottom = 92.dp),
                ) {
                    AnimatedVisibility(
                        visible = clipboardOffer != null,
                        enter = fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 2 },
                        exit = fadeOut(tween(180)) + slideOutVertically(tween(200)) { it / 2 },
                    ) {
                        clipboardOffer?.let { offer ->
                            ClipboardOfferCard(
                                offer = offer,
                                onGrab = viewModel::grabClipboardOffer,
                                onDismiss = viewModel::dismissClipboardOffer,
                            )
                        }
                    }
                    if (clipboardOffer != null && hasMiniPlayer) {
                        Spacer(Modifier.height(8.dp))
                    }
                    AnimatedVisibility(
                        visible = hasMiniPlayer,
                        enter = fadeIn(tween(200)) + slideInVertically(tween(240)) { it / 2 },
                        exit = fadeOut(tween(160)) + slideOutVertically(tween(180)) { it / 2 },
                    ) {
                        MiniPlayerBar(
                            audioState = audioState,
                            onOpenFullPlayer = onOpenAudio,
                            onTogglePlayPause = viewModel::toggleAudioPlayPause,
                            onClose = viewModel::stopAudio,
                        )
                    }
                }

                // Floating frosted-glass gooey dock
                PopTabBar(
                    tabs = listOf(
                        PopTabItem(Icons.Rounded.Home, "Home", testTag = "tab_home"),
                        PopTabItem(Icons.Rounded.Download, "Downloads", badge = activeCount, testTag = "tab_downloads"),
                        PopTabItem(Icons.Rounded.VideoLibrary, "Library", testTag = "tab_library"),
                        PopTabItem(Icons.Rounded.Tune, "Settings", testTag = "tab_settings"),
                    ),
                    selectedIndex = selectedTab,
                    onSelect = viewModel::selectTab,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }

            // Secondary full-screen views (all equipped with BackHandler)
            AnimatedVisibility(
                visible = detailJobId != null,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(180)),
            ) {
                detailJobId?.let { id ->
                    val job = jobs.firstOrNull { it.id == id }
                    DownloadDetailScreen(
                        job = job,
                        extraction = extractions[id],
                        onBack = onCloseDetail,
                        onRemoveFromHistory = {
                            viewModel.removeJob(it)
                            viewModel.showToast("Removed from history", "Files on the phone were kept.")
                        },
                        onDownloadAgain = { url ->
                            onCloseDetail()
                            viewModel.selectTab(0)
                            viewModel.fetchLink(url)
                        },
                        onCopyLink = { viewModel.showToast("Link copied") },
                        onExtractAudio = viewModel::extractAudio,
                        onPlayVideo = onOpenVideo,
                        onPlayAudio = { item ->
                            viewModel.playAudio(item)
                            onOpenAudio()
                        },
                        onMakeRingtone = onOpenRingtone,
                        onDeleteFile = { jId, path ->
                            viewModel.deleteFile(jId, path)
                            viewModel.showToast("File deleted")
                        },
                    )
                }
            }

            AnimatedVisibility(
                visible = showAbout,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(180)),
            ) {
                AboutScreen(
                    releaseState = releaseCheck,
                    onCheckUpdates = { viewModel.checkForGithubUpdates(BuildConfig.VERSION_NAME) },
                    onBack = onCloseAbout,
                )
            }

            AnimatedVisibility(
                visible = showAudioPlayer,
                enter = fadeIn(tween(200)) + slideInVertically(tween(240)) { it / 5 },
                exit = fadeOut(tween(180)) + slideOutVertically(tween(200)) { it / 5 },
            ) {
                AudioPlayerScreen(
                    audioState = audioState,
                    onBack = onCloseAudio,
                    onTogglePlayPause = viewModel::toggleAudioPlayPause,
                    onSeek = viewModel::seekAudio,
                    onSkip = viewModel::skipAudio,
                    onCycleSpeed = viewModel::cycleAudioSpeed,
                    onMakeRingtone = onOpenRingtone,
                )
            }

            AnimatedVisibility(
                visible = videoPlayerItem != null,
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(160)),
            ) {
                videoPlayerItem?.let { item ->
                    VideoPlayerScreen(
                        item = item,
                        onBack = onCloseVideo,
                    )
                }
            }

            // Modal bottom sheets
            if (fetchState.showFormatSheet && fetchState.currentInfo != null) {
                FormatBottomSheet(
                    fetchState = fetchState,
                    settings = settings,
                    engine = viewModel.engine,
                    onDismiss = viewModel::closeSheets,
                    onModeChange = viewModel::setFetchMode,
                    onOptionChange = viewModel::setFetchOptionIndex,
                    onTrimToggle = viewModel::setTrimEnabled,
                    onTrimChange = viewModel::updateTrimRange,
                    onToggleSubLang = viewModel::toggleSubtitleLang,
                    onToggleSubAuto = viewModel::setSubtitleIncludeAuto,
                    onToggleSubEmbed = viewModel::setSubtitleEmbed,
                    onDownload = viewModel::enqueueCurrentMedia,
                )
            }

            if (fetchState.showPlaylistSheet && fetchState.currentInfo != null) {
                PlaylistBottomSheet(
                    info = fetchState.currentInfo!!,
                    settings = settings,
                    onDismiss = viewModel::closeSheets,
                    onDownloadSelected = { indices, mode, height ->
                        viewModel.enqueuePlaylistItems(fetchState.currentInfo!!, indices, mode, height)
                    },
                )
            }

            ringtoneItem?.let { item ->
                RingtoneBottomSheet(
                    item = item,
                    engine = viewModel.engine,
                    onMakeRingtone = viewModel::makeRingtone,
                    onSetRingtone = viewModel::setRingtone,
                    onToast = { t, d, err -> viewModel.showToast(t, d, err) },
                    onDismiss = onCloseRingtone,
                )
            }
        }

        // Top confetti celebration & toast overlay
        PopConfettiOverlay(triggerCount = confettiCount)
        PopToastHost(
            message = toast,
            onDismiss = viewModel::dismissToast,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 12.dp),
        )
    }
}
