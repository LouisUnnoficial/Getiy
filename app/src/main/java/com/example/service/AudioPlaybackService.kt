package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.core.AudioPlaybackManager

/**
 * Foreground media service keeping background audio playing with notification controls.
 */
class AudioPlaybackService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> {
                AudioPlaybackManager.togglePlayPause(this)
                return START_NOT_STICKY
            }
            ACTION_STOP -> {
                AudioPlaybackManager.stop(this)
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }

        val active = intent?.getBooleanExtra(EXTRA_ACTIVE, false) ?: false
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "Playing audio"
        val subtitle = intent?.getStringExtra(EXTRA_SUBTITLE) ?: "Getiy"
        val isPlaying = intent?.getBooleanExtra(EXTRA_PLAYING, false) ?: false

        val notification = buildNotification(this, title, subtitle, isPlaying)
        val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else {
            0
        }
        runCatching {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, fgsType)
        }

        if (!active) {
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
        private const val CHANNEL_ID = "grab_audio_playback"
        private const val NOTIFICATION_ID = 1002
        private const val ACTION_TOGGLE = "com.example.AUDIO_TOGGLE"
        private const val ACTION_STOP = "com.example.AUDIO_STOP"
        private const val EXTRA_ACTIVE = "active"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_SUBTITLE = "subtitle"
        private const val EXTRA_PLAYING = "playing"

        fun sync(context: Context, title: String?, subtitle: String?, isPlaying: Boolean) {
            val active = title != null
            val intent = Intent(context, AudioPlaybackService::class.java)
                .putExtra(EXTRA_ACTIVE, active)
                .putExtra(EXTRA_TITLE, title ?: "")
                .putExtra(EXTRA_SUBTITLE, subtitle ?: "Getiy")
                .putExtra(EXTRA_PLAYING, isPlaying)
            runCatching {
                if (active) {
                    ContextCompat.startForegroundService(context, intent)
                } else {
                    context.stopService(intent)
                }
            }
        }

        private fun buildNotification(
            context: Context,
            title: String,
            subtitle: String,
            isPlaying: Boolean,
        ): Notification {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                manager != null &&
                manager.getNotificationChannel(CHANNEL_ID) == null
            ) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "Audio playback",
                        NotificationManager.IMPORTANCE_LOW,
                    ),
                )
            }
            val open = PendingIntent.getActivity(
                context,
                10,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE,
            )
            val toggle = PendingIntent.getService(
                context,
                11,
                Intent(context, AudioPlaybackService::class.java).setAction(ACTION_TOGGLE),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val stop = PendingIntent.getService(
                context,
                12,
                Intent(context, AudioPlaybackService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_grab)
                .setContentTitle(title)
                .setContentText(subtitle)
                .setOngoing(isPlaying)
                .setOnlyAlertOnce(true)
                .setContentIntent(open)
                .addAction(
                    if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                    if (isPlaying) "Pause" else "Play",
                    toggle,
                )
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stop)
                .build()
        }
    }
}
