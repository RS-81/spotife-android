package com.example.spotifywrapper.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.spotifywrapper.data.SpotifyRepository
import com.example.spotifywrapper.webview.HeadlessSpotifyWebView

class PlaybackService : Service() {

    companion object {
        private const val TAG = "PlaybackService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "spotify_wrapper_playback_channel"
        private const val WAKELOCK_TAG = "SpotifyWrapper:PlaybackWakeLock"

        const val ACTION_START = "com.example.spotifywrapper.action.START"
        const val ACTION_PLAY_PAUSE = "com.example.spotifywrapper.action.PLAY_PAUSE"
        const val ACTION_ACTIVATE_DEVICE = "com.example.spotifywrapper.action.ACTIVATE_DEVICE"
        const val ACTION_STOP = "com.example.spotifywrapper.action.STOP"
    }

    inner class LocalBinder : Binder() {
        fun getService(): PlaybackService = this@PlaybackService
    }

    private val binder = LocalBinder()
    private var wakeLock: PowerManager.WakeLock? = null

    val repository = SpotifyRepository()
    var headlessWebView: HeadlessSpotifyWebView? = null
        private set

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()

        headlessWebView = HeadlessSpotifyWebView(
            context = this,
            repository = repository,
            onReadyStateChanged = { isReady ->
                Log.d(TAG, "HeadlessSpotifyWebView ready state: $isReady")
            }
        ).also {
            it.loadPlayer()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundMediaService()

        when (intent?.action) {
            ACTION_PLAY_PAUSE -> headlessWebView?.togglePlayPause()
            ACTION_ACTIVATE_DEVICE -> headlessWebView?.triggerDeviceMenuAndActivate()
            ACTION_STOP -> {
                stopForegroundMediaService()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private fun startForegroundMediaService() {
        val notification = buildNotification()
        val foregroundType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else {
            0
        }

        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, foregroundType)
    }

    private fun stopForegroundMediaService() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG).apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Media Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the web media player active in the background"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val playPauseIntent = Intent(this, PlaybackService::class.java).apply {
            action = ACTION_PLAY_PAUSE
        }
        val playPausePendingIntent = PendingIntent.getService(
            this, 1, playPauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, PlaybackService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 2, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Spotify Web Player")
            .setContentText("Background playback active")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(android.R.drawable.ic_media_pause, "Play/Pause", playPausePendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .build()
    }

    override fun onDestroy() {
        headlessWebView?.release()
        headlessWebView = null
        releaseWakeLock()
        super.onDestroy()
    }
}
