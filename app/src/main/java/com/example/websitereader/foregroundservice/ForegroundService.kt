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
import com.example.websitereader.R
import com.example.websitereader.model.Article
import com.example.websitereader.settings.TTSProviderStore
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
import kotlin.coroutines.cancellation.CancellationException

class ForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val binder = LocalBinder()
    private var progressListener: ((Double) -> Unit)? = null
    private var succeededListener: ((String) -> Unit)? = null
    private var errorListener: ((String) -> Unit)? = null

    inner class LocalBinder : Binder() {
        fun getService() = this@ForegroundService
        fun setProgressListener(listener: (Double) -> Unit) {
            progressListener = listener
        }

        fun setSucceededListener(listener: (String) -> Unit) {
            succeededListener = listener
        }

        fun setErrorListener(listener: (String) -> Unit) {
            errorListener = listener
        }
    }

    companion object {
        const val CHANNEL_ID = "my_channel_01"
        const val NOTIFICATION_ID = 1
        const val EXTRA_PROVIDER_NAME = "PROVIDER_NAME"
        const val EXTRA_ARTICLE = "ARTICLE"
        const val ACTION_STOP = "STOP_SERVICE"
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
        val article = Article.fromJson(intent?.getStringExtra(EXTRA_ARTICLE)!!)
        if (ttsProviderName == null) {
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
                val providerInstance: Provider =
                    if (ttsProviderName == getString(R.string.system_tts_provider_name)) {
                        Android(this@ForegroundService)
                    } else {
                        // Await the list, then find the matching provider
                        val ttsProviders = TTSProviderStore.providers.first()
                        val entry = ttsProviders.find { it.name == ttsProviderName }
                            ?: throw IllegalStateException("No provider found for $ttsProviderName")
                        OpenAI(this@ForegroundService, entry)
                    }

                val fileName = article.headline.replace(
                    Regex("[^a-zA-Z0-9]"),
                    "_"
                ) + "." + providerInstance.audioFormat
                val outputFile = this@ForegroundService.filesDir.resolve(fileName)

                providerInstance.synthesizeTextToFile(
                    text = article.text,
                    langCode = article.lang ?: "en_US",
                    outputFile = outputFile,
                    progressCallback = { progress: Double, _: ProgressState ->
                        progressListener?.invoke(progress)
                        val progInt = (progress * 100).toInt()
                        notificationBuilder.setProgress(100, progInt, false).setContentText(
                            getString(R.string.tts_generation_notification_progress, progInt)
                        )
                        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
                    })

                succeededListener?.invoke(outputFile.absolutePath)
                stopForeground(STOP_FOREGROUND_REMOVE)
            } catch (e: CancellationException) {
                errorListener?.invoke("Task cancelled")
                Log.i("ForegroundService", "Cancelled by user or system, no error notification.")
            } catch (e: Exception) {
                e.printStackTrace()
                notificationBuilder.setContentText("Task failed").setProgress(0, 0, false)
                notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
                errorListener?.invoke(e.message ?: "Unknown error")
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