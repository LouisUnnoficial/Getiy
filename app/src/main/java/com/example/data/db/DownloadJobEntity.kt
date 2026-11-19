package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.DownloadMode
import com.example.data.model.JobStatus

@Entity(tableName = "download_jobs")
data class DownloadJobEntity(
    @PrimaryKey val id: String,
    val url: String,
    val title: String,
    val site: String?,
    val thumbnail: String?,
    val mediaId: String?,
    val mode: String,
    val maxHeight: Int?,
    val container: String,
    val audioFormat: String,
    val argsJson: String,
    val suffix: String,
    val template: String,
    val status: String,
    val progress: Double,
    val eta: Long?,
    val paths: String,
    val error: String?,
    val attempts: Int,
    val createdAt: Long,
    val finishedAt: Long?,
) {
    val jobStatus: JobStatus get() = JobStatus.fromName(status)
    val downloadMode: DownloadMode get() = DownloadMode.fromName(mode)

    val pathList: List<String>
        get() = paths.split('\n').filter { it.isNotBlank() }

    val formatBadge: String
        get() = when (downloadMode) {
            DownloadMode.AUDIO_ONLY -> audioFormat.uppercase()
            DownloadMode.SUBTITLES_ONLY -> "Subtitles"
            else -> listOfNotNull(
                container.uppercase(),
                maxHeight?.let { "${it}p" },
            ).joinToString(" · ")
        }
}
