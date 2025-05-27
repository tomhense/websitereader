package com.example.websitereader.foregroundservice

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import com.example.websitereader.R

class ForegroundNotificationHelper(
    private val context: Context,
    private val channelId: String,
    private val channelName: String
) {
    fun createChannel() {
        val channel = NotificationChannel(
            channelId, channelName, NotificationManager.IMPORTANCE_LOW
        )
        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    fun buildNotification(
        progress: Int,
        stopActionIntent: PendingIntent
    ): NotificationCompat.Builder {
        return NotificationCompat.Builder(context, channelId)
            .setContentTitle(context.getString(R.string.tts_generation_notification_title))
            .setContentText(
                context.getString(
                    R.string.tts_generation_notification_progress, progress
                )
            )
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, progress, false)
            .addAction(
                R.drawable.baseline_stop_24,
                context.getString(R.string.stop),
                stopActionIntent
            )
    }
}