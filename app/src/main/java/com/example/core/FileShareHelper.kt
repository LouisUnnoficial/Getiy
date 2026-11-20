package com.example.core

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.example.R
import java.io.File
import kotlin.math.abs

object FileShareHelper {

    fun uriForFile(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)

    fun mimeTypeFor(file: File): String {
        val ext = file.extension.lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: when (ext) {
            "mp4", "mkv", "webm", "mov" -> "video/*"
            "mp3", "m4a", "opus", "flac", "ogg", "wav" -> "audio/*"
            "srt", "vtt", "ass" -> "text/plain"
            else -> "*/*"
        }
    }

    fun shareFiles(context: Context, paths: List<String>, subject: String? = null) {
        val files = paths.map(::File).filter { it.exists() }
        if (files.isEmpty()) return

        val uris = ArrayList<Uri>(files.map { uriForFile(context, it) })
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = mimeTypeFor(files.first())
                putExtra(Intent.EXTRA_STREAM, uris.first())
            }
        } else {
            val allVideo = files.all { mimeTypeFor(it).startsWith("video/") }
            val allAudio = files.all { mimeTypeFor(it).startsWith("audio/") }
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = when {
                    allVideo -> "video/*"
                    allAudio -> "audio/*"
                    else -> "*/*"
                }
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            }
        }

        if (!subject.isNullOrBlank()) {
            intent.putExtra(Intent.EXTRA_SUBJECT, subject)
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        val chooser = Intent.createChooser(intent, subject ?: "Share").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(chooser) }
    }

    fun openExternalUrl(context: Context, url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    fun openFileExternally(context: Context, path: String) {
        val file = File(path)
        if (!file.exists()) return
        val uri = uriForFile(context, file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeTypeFor(file))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(intent, file.name).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(chooser) }
    }

    fun copyToClipboard(context: Context, label: String, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    fun readClipboardText(context: Context): String? {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
        if (!cm.hasPrimaryClip()) return null
        return runCatching {
            cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
        }.getOrNull()
    }

    private val audioCovers = listOf(
        R.drawable.img_audio_art_1,
        R.drawable.img_audio_art_2,
        R.drawable.img_audio_art_3,
        R.drawable.img_audio_art_4,
    )

    fun audioCoverRes(seed: String): Int =
        audioCovers[abs(seed.hashCode()) % audioCovers.size]
}
