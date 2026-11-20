package com.example.data.settings

import com.example.data.model.AppThemeMode
import com.example.data.model.DownloadMode
import com.example.data.model.LibrarySort
import com.example.data.model.MediaHelpers

data class AppSettings(
    val defaultMode: DownloadMode = DownloadMode.VIDEO,
    val maxHeight: Int? = null,
    val container: String = "mp4",
    val audioFormat: String = "mp3",
    val concurrency: Int = 2,
    val saveBase: String = "download", // download | movies | music
    val saveFolder: String = "Getiy",
    val fileTemplate: String = MediaHelpers.DEFAULT_FILE_TEMPLATE,
    val themeMode: AppThemeMode = AppThemeMode.DARK,
    val hapticsEnabled: Boolean = true,
    val seenWelcome: Boolean = false,
    val librarySort: LibrarySort = LibrarySort.NEWEST,
    val libraryGroupBySite: Boolean = false,
    val useAria2c: Boolean = false,
) {
    val savePathLabel: String
        get() {
            val baseLabel = when (saveBase) {
                "movies" -> "Movies"
                "music" -> "Music"
                else -> "Download"
            }
            return "$baseLabel/$saveFolder"
        }

    companion object {
        val heights: List<Int?> = listOf(null, 2160, 1440, 1080, 720, 480)
        val containers: List<String> = listOf("mp4", "mkv", "webm")
        val audioFormats: List<String> = listOf("mp3", "m4a", "opus", "flac")
        val saveBases: List<Pair<String, String>> = listOf(
            "download" to "Downloads",
            "movies" to "Movies",
            "music" to "Music",
        )
    }
}
