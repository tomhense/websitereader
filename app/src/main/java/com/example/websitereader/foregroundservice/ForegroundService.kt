package com.example.websitereader.foregroundservice

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import com.example.websitereader.R
import com.example.websitereader.settings.TTSProviderRepository
import com.example.websitereader.tts.Android
import com.example.websitereader.tts.OpenAI
import com.example.websitereader.tts.ProgressState
import com.example.websitereader.tts.Provider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

class ForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val binder = LocalBinder()
    private var progressListener: ((Double) -> Unit)? = null

    inner class LocalBinder : Binder() {
        fun getService() = this@ForegroundService
        fun setProgressListener(listener: (Double) -> Unit) {
            progressListener = listener
        }

        fun removeProgressListener() {
            progressListener = null
        }
    }

    companion object {
        const val CHANNEL_ID = "my_channel_01"
        const val NOTIFICATION_ID = 1
        const val EXTRA_PROVIDER_NAME = "PROVIDER_NAME"
        const val EXTRA_TEXT = "TEXT"
        const val EXTRA_LANG = "LANG"
        const val EXTRA_FILE_URI = "FILE_URI"
        const val ACTION_STOP = "STOP_SERVICE"
        const val BROADCAST_COMPLETE = "com.example.websitereader.AUDIO_GENERATION_COMPLETE"
        const val BROADCAST_FAILED = "com.example.websitereader.AUDIO_GENERATION_FAILED"
    }

    private lateinit var notificationHelper: ForegroundNotificationHelper

    override fun onCreate() {
        super.onCreate()
        notificationHelper =
            ForegroundNotificationHelper(this, CHANNEL_ID, "Foreground Service Channel")
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 1. Stop Action
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            serviceScope.cancel()
            return START_NOT_STICKY
        }

        // 2. Parameters
        val ttsProviderName = intent?.getStringExtra(EXTRA_PROVIDER_NAME)
        val text = intent?.getStringExtra(EXTRA_TEXT)
        val lang = intent?.getStringExtra(EXTRA_LANG)
        val fileUriString = intent?.getStringExtra(EXTRA_FILE_URI)
        if (text == null || lang == null || fileUriString == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        // 3. Notification Channel
        notificationHelper.createChannel()

        // 4. Stop PendingIntent
        val stopIntent = Intent(this, ForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStopIntent = PendingIntent.getService(
            this, 101, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 5. Notification
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            stopSelf()
            return START_NOT_STICKY
        }
        val notificationBuilder = notificationHelper.buildNotification(
            progress = 0, stopActionIntent = pendingStopIntent
        )
        startForeground(NOTIFICATION_ID, notificationBuilder.build())
        val notificationManager = NotificationManagerCompat.from(this)

        // 6. Launch coroutine for the heavy work
        serviceScope.launch {
            try {
                // Select provider
                val providerInstance: Provider = if (ttsProviderName != null) {
                    // Await the list, then find the matching provider
                    val ttsProviders = TTSProviderRepository.load(this@ForegroundService).first()
                    val entry = ttsProviders.find { it.name == ttsProviderName }
                        ?: throw IllegalStateException("No provider found for $ttsProviderName")
                    OpenAI(this@ForegroundService, entry)
                } else {
                    Android(this@ForegroundService)
                }

                val outputFile = File(fileUriString.toUri().path!!)

                providerInstance.synthesizeTextToFile(
                    text = text,
                    langCode = lang,
                    outputFile = outputFile,
                    progressCallback = { progress: Double, _: ProgressState ->
                        progressListener?.invoke(progress)
                        val progInt = (progress * 100).toInt()
                        notificationBuilder.setProgress(100, progInt, false).setContentText(
                            getString(R.string.tts_generation_notification_progress, progInt)
                        )
                        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
                    })

                ServiceBroadcastHelper.send(this@ForegroundService, BROADCAST_COMPLETE)
                stopForeground(STOP_FOREGROUND_REMOVE)

            } catch (e: CancellationException) {
                ServiceBroadcastHelper.send(this@ForegroundService, BROADCAST_FAILED)
                Log.i("ForegroundService", "Cancelled by user or system, no error notification.")
            } catch (e: Exception) {
                e.printStackTrace()
                notificationBuilder.setContentText("Task failed").setProgress(0, 0, false)
                notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
                ServiceBroadcastHelper.send(this@ForegroundService, BROADCAST_FAILED)
            } finally {
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}