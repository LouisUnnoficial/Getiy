package com.example.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.core.GrabHaptics
import com.example.data.model.AppThemeMode
import com.example.data.model.DownloadMode
import com.example.data.model.LibrarySort
import com.example.data.model.MediaHelpers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.grabDataStore: DataStore<Preferences> by preferencesDataStore(name = "grab_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val DEFAULT_MODE = stringPreferencesKey("default_mode")
        val MAX_HEIGHT = intPreferencesKey("max_height")
        val CONTAINER = stringPreferencesKey("container")
        val AUDIO_FORMAT = stringPreferencesKey("audio_format")
        val CONCURRENCY = intPreferencesKey("concurrency")
        val SAVE_BASE = stringPreferencesKey("save_base")
        val SAVE_FOLDER = stringPreferencesKey("save_folder")
        val FILE_TEMPLATE = stringPreferencesKey("file_template")
        val THEME_MODE = stringPreferencesKey("getiy_theme_mode")
        val HAPTICS = booleanPreferencesKey("haptics_enabled")
        val SEEN_WELCOME = booleanPreferencesKey("seen_welcome")
        val LIBRARY_SORT = stringPreferencesKey("library_sort")
        val LIBRARY_GROUP = booleanPreferencesKey("library_group")
        val USE_ARIA2C = booleanPreferencesKey("use_aria2c")
    }

    val settingsFlow: Flow<AppSettings> = context.grabDataStore.data.map { p ->
        val haptics = p[Keys.HAPTICS] ?: true
        GrabHaptics.enabled = haptics
        val heightRaw = p[Keys.MAX_HEIGHT] ?: 0
        AppSettings(
            defaultMode = DownloadMode.fromName(p[Keys.DEFAULT_MODE]),
            maxHeight = heightRaw.takeIf { it > 0 },
            container = p[Keys.CONTAINER] ?: "mp4",
            audioFormat = p[Keys.AUDIO_FORMAT] ?: "mp3",
            concurrency = (p[Keys.CONCURRENCY] ?: 2).coerceIn(1, 3),
            saveBase = p[Keys.SAVE_BASE] ?: "download",
            saveFolder = p[Keys.SAVE_FOLDER] ?: "Getiy",
            fileTemplate = p[Keys.FILE_TEMPLATE] ?: MediaHelpers.DEFAULT_FILE_TEMPLATE,
            themeMode = AppThemeMode.fromName(p[Keys.THEME_MODE]),
            hapticsEnabled = haptics,
            seenWelcome = p[Keys.SEEN_WELCOME] ?: false,
            librarySort = LibrarySort.fromName(p[Keys.LIBRARY_SORT]),
            libraryGroupBySite = p[Keys.LIBRARY_GROUP] ?: false,
            useAria2c = p[Keys.USE_ARIA2C] ?: false,
        )
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.grabDataStore.edit { p ->
            val currentHeight = (p[Keys.MAX_HEIGHT] ?: 0).takeIf { it > 0 }
            val current = AppSettings(
                defaultMode = DownloadMode.fromName(p[Keys.DEFAULT_MODE]),
                maxHeight = currentHeight,
                container = p[Keys.CONTAINER] ?: "mp4",
                audioFormat = p[Keys.AUDIO_FORMAT] ?: "mp3",
                concurrency = (p[Keys.CONCURRENCY] ?: 2).coerceIn(1, 3),
                saveBase = p[Keys.SAVE_BASE] ?: "download",
                saveFolder = p[Keys.SAVE_FOLDER] ?: "Getiy",
                fileTemplate = p[Keys.FILE_TEMPLATE] ?: MediaHelpers.DEFAULT_FILE_TEMPLATE,
                themeMode = AppThemeMode.fromName(p[Keys.THEME_MODE]),
                hapticsEnabled = p[Keys.HAPTICS] ?: true,
                seenWelcome = p[Keys.SEEN_WELCOME] ?: false,
                librarySort = LibrarySort.fromName(p[Keys.LIBRARY_SORT]),
                libraryGroupBySite = p[Keys.LIBRARY_GROUP] ?: false,
                useAria2c = p[Keys.USE_ARIA2C] ?: false,
            )
            val next = transform(current)
            GrabHaptics.enabled = next.hapticsEnabled
            p[Keys.DEFAULT_MODE] = next.defaultMode.name
            p[Keys.MAX_HEIGHT] = next.maxHeight ?: 0
            p[Keys.CONTAINER] = next.container
            p[Keys.AUDIO_FORMAT] = next.audioFormat
            p[Keys.CONCURRENCY] = next.concurrency.coerceIn(1, 3)
            p[Keys.SAVE_BASE] = next.saveBase
            p[Keys.SAVE_FOLDER] = next.saveFolder.replace(Regex("""[\\/:*?"<>|]"""), " ")
                .trim().take(40).ifEmpty { "Getiy" }
            p[Keys.FILE_TEMPLATE] = MediaHelpers.cleanTemplate(next.fileTemplate)
            p[Keys.THEME_MODE] = next.themeMode.name
            p[Keys.HAPTICS] = next.hapticsEnabled
            p[Keys.SEEN_WELCOME] = next.seenWelcome
            p[Keys.LIBRARY_SORT] = next.librarySort.name
            p[Keys.LIBRARY_GROUP] = next.libraryGroupBySite
            p[Keys.USE_ARIA2C] = next.useAria2c
        }
    }
}
