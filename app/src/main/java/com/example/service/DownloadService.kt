package com.example.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

/**
 * Keeps the app process alive while downloads run and shows live progress in the notification shade.
 */
class DownloadService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val count = intent?.getIntExtra(EXTRA_COUNT, 0) ?: 0
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "Downloading"
        val percent = intent?.getIntExtra(EXTRA_PERCENT, 0) ?: 0

        val notification = buildProgressNotification(
            context = this,
            title = title,
            count = maxOf(count, 1),
            percent = percent,
        )

        val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }

        runCatching {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, fgsType)
        }

        if (count == 0) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "grab_downloads"
        private const val DONE_CHANNEL_ID = "grab_finished"
        private const val NOTIFICATION_ID = 1001
        private const val EXTRA_COUNT = "count"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_PERCENT = "percent"

        @Volatile
        private var running = false
        private var lastPercent = -1
        private var lastCount = -1

        fun update(context: Context, jobs: List<Pair<String, Float>>) {
            val count = jobs.size
            val percent = if (count == 0) 0 else jobs.map { it.second.coerceIn(0f, 100f) }.average().toInt()
            if (count == 0 && !running) return
            if (count == lastCount && percent == lastPercent) return
            lastCount = count
            lastPercent = percent

            val intent = Intent(context, DownloadService::class.java)
                .putExtra(EXTRA_COUNT, count)
                .putExtra(EXTRA_TITLE, jobs.firstOrNull()?.first ?: "")
                .putExtra(EXTRA_PERCENT, percent)
            running = count > 0
            runCatching {
                ContextCompat.startForegroundService(context, intent)
            }
        }

        fun notifyFinished(context: Context, jobId: String, title: String, ok: Boolean) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                manager.getNotificationChannel(DONE_CHANNEL_ID) == null
            ) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        DONE_CHANNEL_ID,
                        "Finished downloads",
                        NotificationManager.IMPORTANCE_DEFAULT,
                    ),
                )
            }
            val open = PendingIntent.getActivity(
                context,
                1,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(context, DONE_CHANNEL_ID)
                .setSmallIcon(if (ok) R.drawable.ic_stat_grab else android.R.drawable.stat_notify_error)
                .setContentTitle(if (ok) "Saved" else "Download failed")
                .setContentText(title)
                .setAutoCancel(true)
                .setContentIntent(open)
                .build()
            manager.notify(jobId.hashCode(), notification)
        }

        private fun buildProgressNotification(
            context: Context,
            title: String,
            count: Int,
            percent: Int,
        ): Notification {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                manager != null &&
                manager.getNotificationChannel(CHANNEL_ID) == null
            ) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "Downloads",
                        NotificationManager.IMPORTANCE_LOW,
                    ),
                )
            }
            val open = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE,
            )
            val heading = if (count == 1) title else "Downloading $count items"
            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_grab)
                .setContentTitle(heading)
                .setContentText("$percent%")
                .setProgress(100, percent, percent == 0)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(open)
                .build()
        }
    }
}
