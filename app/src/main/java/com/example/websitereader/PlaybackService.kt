package com.example.websitereader

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import androidx.annotation.RequiresApi
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionToken
import androidx.media3.session.legacy.AudioAttributesCompat
import androidx.media3.session.legacy.MediaBrowserServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

@UnstableApi
class PlaybackService : MediaBrowserServiceCompat() {

    private lateinit var mediaSession: MediaSession
    private lateinit var player: Player
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        initializeSessionAndPlayer()
        initializeNotificationManager()
    }

    private fun initializeSessionAndPlayer() {
        player = TtsPlayer(Looper.getMainLooper(), this)
        mediaSession = MediaSession(this, player, sessionCallback)
        sessionToken = mediaSession.token
        mediaSession.setCallback(sessionCallback)
        val rootMediaId = "rootMediaId"
        val rootMediaItemMetadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, rootMediaId)
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, "TTS Player")
            .putLong(MediaMetadataCompat.METADATA_KEY_PLAYABLE, 1L)
            .build()
        val rootMediaItemDescription = MediaDescriptionCompat.Builder()
            .setMediaId(rootMediaId)
            .setTitle("TTS Player")
            .setSubtitle("Android TTS Player")
            .setMediaUri(MediaLibraryService.URI_CONCATENATED_MEDIA_ROOT)
            .setIconUri(MediaLibraryService.getRoot())
            .setExtras(Bundle())
            .build()
        val rootMediaItem = MediaBrowserCompat.MediaItem(
            rootMediaItemDescription,
            MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
        )
        onLoadChildren(rootMediaId, MediaBrowserCompat.SubscriptionCallback.addSubscription(
            MediaLibraryService.ROOT_MEDIA_ID,
            rootMediaItem
        ))
    }

    private val sessionCallback = MediaSessionCallback()

    inner class MediaSessionCallback : MediaSession.Callback {
        override
        override fun onPlayerStateChanged(player: Player, playWhenReady: Boolean, playbackState: Int) {
            super.onPlayerStateChanged(player, playWhenReady, playbackState)
            updateNotificationAndForegroundService(playbackState)
        }
    }

    private fun updateNotificationAndForegroundService(playbackState: Int) {
        val isPlaying = playbackState == Player.STATE_READY
        if (isPlaying) {
            startForegroundService(createNotification())
        } else {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
    }

    private fun createNotification(): Intent? {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)

        return Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("TTS Player")
            .setContentText("Playing TTS audio")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setTicker("TTS Player")
            .build()
    }

    @SuppressLint("RestrictedApi")
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        player.release()
    }

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "tts_player_channel"
        const val MEDIA_ROOT_ID = "media_root_id"
        @SuppressLint("RestrictedApi")
        val DEFAULT_AUDIO_ATTRIBUTES = AudioAttributesCompat.Builder()
            .setContentType(C.CONTENT_TYPE_SPEECH)
            .setUsage(C.USAGE_MEDIA)
            .build()
    }
}