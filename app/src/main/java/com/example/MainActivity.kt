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
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.core.FileShareHelper
import com.example.core.Links
import com.example.data.model.AppThemeMode
import com.example.data.model.JobStatus
import com.example.data.model.LibraryItem
import com.example.service.GetiyTileService
import com.example.ui.components.ClipboardOfferCard
import com.example.ui.components.GetiySideRail
import com.example.ui.components.GetiyTopNavBar
import com.example.ui.components.PopConfettiOverlay
import com.example.ui.components.PopTabItem
import com.example.ui.components.PopToastHost
import com.example.ui.components.UpdateAvailableDialog
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
import com.example.ui.theme.GetiyNavy
import com.example.ui.theme.GetiyTheme
import com.example.ui.theme.GetiyThemeTokens
import com.example.ui.viewmodel.GetiyViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: GetiyViewModel by viewModels()
    private var pendingClipboardFetch = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingIntent(intent)

        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val settingsLoaded by viewModel.settingsLoaded.collectAsStateWithLifecycle()
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

            val navyTopBar = !settingsLoaded || !settings.seenWelcome || showAbout || detailJobId != null
            val navyBottomBar = !settingsLoaded || !settings.seenWelcome
            LaunchedEffect(darkTheme, navyTopBar, navyBottomBar, videoPlayerItem) {
                val useDarkStatusIcons = when {
                    videoPlayerItem != null -> false
                    navyTopBar -> false
                    else -> !darkTheme
                }
                val useDarkNavIcons = when {
                    videoPlayerItem != null -> false
                    navyBottomBar -> false
                    else -> !darkTheme
                }
                val transparent = Color.Transparent.toArgb()
                enableEdgeToEdge(
                    statusBarStyle = if (useDarkStatusIcons) {
                        SystemBarStyle.light(transparent, transparent)
                    } else {
                        SystemBarStyle.dark(transparent)
                    },
                    navigationBarStyle = if (useDarkNavIcons) {
                        SystemBarStyle.light(transparent, transparent)
                    } else {
                        SystemBarStyle.dark(transparent)
                    },
                )
            }

            GetiyTheme(themeMode = settings.themeMode) {
                GetiyAppRoot(
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

    override fun onStart() {
        super.onStart()
        viewModel.checkForAppUpdateOnOpen(BuildConfig.VERSION_NAME)
    }

    override fun onStop() {
        super.onStop()
        viewModel.onAppBackgrounded()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) return
        if (pendingClipboardFetch) {
            pendingClipboardFetch = false
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
            GetiyTileService.ACTION_GETIY_CLIPBOARD -> {
                viewModel.completeWelcome()
                pendingClipboardFetch = true
            }
        }
    }
}

@Composable
private fun GetiyAppRoot(
    viewModel: GetiyViewModel,
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
    val pop = GetiyThemeTokens.pop
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val settingsLoaded by viewModel.settingsLoaded.collectAsStateWithLifecycle()
    val jobs by viewModel.jobs.collectAsStateWithLifecycle()
    val speeds by viewModel.speeds.collectAsStateWithLifecycle()
    val extractions by viewModel.extractions.collectAsStateWithLifecycle()
    val libraryItems by viewModel.libraryItems.collectAsStateWithLifecycle()
    val activeCount by viewModel.activeDownloadCount.collectAsStateWithLifecycle()
    val engineVersion by viewModel.engineVersion.collectAsStateWithLifecycle()
    val engineStatus by viewModel.engineStatus.collectAsStateWithLifecycle()
    val fetchState by viewModel.fetchState.collectAsStateWithLifecycle()
    val clipboardOffer by viewModel.clipboardOffer.collectAsStateWithLifecycle()
    val toast by viewModel.toast.collectAsStateWithLifecycle()
    val confettiCount by viewModel.confettiCounter.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val audioState by viewModel.audioState.collectAsStateWithLifecycle()
    val releaseCheck by viewModel.releaseCheck.collectAsStateWithLifecycle()
    val updatePopupRelease by viewModel.updatePopupRelease.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val hasMiniPlayer = audioState.current != null
    val hasOverlayAboveBottom = hasMiniPlayer || clipboardOffer != null
    val extraBottom = when {
        hasMiniPlayer && clipboardOffer != null -> 140
        hasOverlayAboveBottom -> 74
        else -> 0
    }

    val navTabs = remember(activeCount) {
        listOf(
            PopTabItem(Icons.Rounded.Home, "Home", testTag = "tab_home"),
            PopTabItem(Icons.Rounded.Download, "Queue", badge = activeCount, testTag = "tab_downloads"),
            PopTabItem(Icons.Rounded.VideoLibrary, "Vault", testTag = "tab_library"),
            PopTabItem(Icons.Rounded.Tune, "Settings", testTag = "tab_settings"),
        )
    }
    val storageBytes = remember(libraryItems) { libraryItems.sumOf { it.sizeBytes } }
    val failedCount = remember(jobs) {
        jobs.count { it.jobStatus == JobStatus.FAILED || it.jobStatus == JobStatus.CANCELED }
    }

    Box(modifier = Modifier.fillMaxSize().background(if (settingsLoaded) pop.bg else GetiyNavy)) {
        if (!settingsLoaded) {
            // Keep seamless #0E1935 launch backdrop until DataStore emits saved onboarding state
            Box(modifier = Modifier.fillMaxSize().background(GetiyNavy))
        } else if (!settings.seenWelcome) {
            WelcomeScreen(onDone = { viewModel.completeWelcome() })
        } else {
            // Adaptive Shell: Top Navigation Bar on compact phones, Left Side Rail on wide screens
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val useSideRail = maxWidth >= 700.dp

                val mainContent: @Composable (Modifier) -> Unit = { contentModifier ->
                    Box(modifier = contentModifier) {
                        AnimatedContent(
                            targetState = selectedTab,
                            transitionSpec = {
                                if (settings.liteMode) {
                                    EnterTransition.None togetherWith ExitTransition.None
                                } else {
                                    fadeIn(tween(150)) togetherWith fadeOut(tween(120))
                                }
                            },
                            label = "tabContent",
                        ) { tab ->
                            when (tab) {
                                0 -> HomeScreen(
                                    fetchState = fetchState,
                                    recentJobs = jobs,
                                    speeds = speeds,
                                    activeCount = activeCount,
                                    savedCount = libraryItems.size,
                                    engineVersion = engineVersion,
                                    extraBottomPadding = extraBottom,
                                    onUrlChange = viewModel::updateUrlText,
                                    onPasteAndFetch = viewModel::pasteAndFetch,
                                    onFetch = { viewModel.fetchLink() },
                                    onReopenReady = viewModel::reopenFetchedSheet,
                                    onOpenJobDetail = onOpenDetail,
                                    onJumpToDownloads = { viewModel.selectTab(1) },
                                    onJumpToLibrary = { viewModel.selectTab(2) },
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
                                    engineStatus = engineStatus,
                                    storageBytes = storageBytes,
                                    failedCount = failedCount,
                                    extraBottomPadding = extraBottom,
                                    onUpdateSettings = viewModel::updateSettings,
                                    onReinitEngines = viewModel::reinitializeEngines,
                                    onClearFailed = viewModel::clearFailedJobs,
                                    onOpenAbout = onOpenAbout,
                                )
                            }
                        }

                        // Floating Clipboard Offer + Mini Audio Player positioned smartly above Home's Fetch Dock or bottom safe area
                        val overlayBottom = if (selectedTab == 0) 152.dp else 14.dp
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .padding(start = 16.dp, end = 16.dp, bottom = overlayBottom),
                        ) {
                            AnimatedVisibility(
                                visible = clipboardOffer != null,
                                enter = fadeIn(tween(180)) + slideInVertically(tween(200)) { it / 2 },
                                exit = fadeOut(tween(140)) + slideOutVertically(tween(160)) { it / 2 },
                            ) {
                                clipboardOffer?.let { offer ->
                                    ClipboardOfferCard(
                                        offer = offer,
                                        onFetchOffer = viewModel::fetchClipboardOffer,
                                        onDismiss = viewModel::dismissClipboardOffer,
                                        liteMode = settings.liteMode,
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
                    }
                }

                if (useSideRail) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        GetiySideRail(
                            tabs = navTabs,
                            selectedIndex = selectedTab,
                            onSelect = viewModel::selectTab,
                        )
                        mainContent(Modifier.weight(1f).fillMaxSize().statusBarsPadding())
                    }
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        GetiyTopNavBar(
                            tabs = navTabs,
                            selectedIndex = selectedTab,
                            onSelect = viewModel::selectTab,
                        )
                        mainContent(Modifier.weight(1f).fillMaxSize())
                    }
                }
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
                    engineStatus = engineStatus,
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

            updatePopupRelease?.let { release ->
                UpdateAvailableDialog(
                    currentVersion = BuildConfig.VERSION_NAME,
                    release = release,
                    onUpdateNow = {
                        viewModel.onUpdateNowClicked()
                        FileShareHelper.openExternalUrl(context, release.htmlUrl)
                    },
                    onMaybeLater = viewModel::dismissUpdatePopup,
                )
            }
        }

        // Top confetti celebration & toast overlay (confetti disabled in Lite Mode to save GPU)
        PopConfettiOverlay(
            triggerCount = confettiCount,
            enabled = !settings.liteMode,
        )
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
