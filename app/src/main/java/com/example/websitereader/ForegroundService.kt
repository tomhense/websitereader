package com.example.websitereader

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.websitereader.tts.ProgressState
import com.example.websitereader.tts.Provider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File

class ForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val binder = LocalBinder()
    private var progressListener: ((Double) -> Unit)? = null
    private val handler = Handler(Looper.getMainLooper())
    private var progress: Double = 0.0
    private var isRunning = false


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
        const val EXTRA_PROVIDER_CLASS_NAME = "PROVIDER_CLASS_NAME"
        const val EXTRA_TEXT = "TEXT"
        const val EXTRA_LANG = "LANG"
        const val EXTRA_FILE_URI = "FILE_URI"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val providerClassName = intent?.getStringExtra(EXTRA_PROVIDER_CLASS_NAME)
        val text = intent?.getStringExtra(EXTRA_TEXT)
        val lang = intent?.getStringExtra(EXTRA_LANG)
        val fileUriString = intent?.getStringExtra(EXTRA_FILE_URI)
        if (providerClassName == null || text == null || lang == null || fileUriString == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        Log.i("tts", "Starting foreground service")

        // Create notification channel
        val channel = NotificationChannel(
            CHANNEL_ID, "Foreground Service Channel", NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

        // Create notification builder
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.tts_generation_notification_title))
            .setContentText(getString(R.string.tts_generation_notification_progress, 0))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW).setOnlyAlertOnce(true).setOngoing(true)
            .setProgress(100, 0, false)

        // Check POST_NOTIFICATIONS permission (omit for brevity here, but keep from previous example)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, builder.build())
        val notificationManager = NotificationManagerCompat.from(this)

        val providerInstance = try {
            val clazz = Class.forName(providerClassName)
            // get constructor that accepts Context
            val cons = clazz.getDeclaredConstructor(Context::class.java)
            cons.isAccessible = true
            cons.newInstance(this) as Provider  // 'this' is the Service -> subclass of Context
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
            return START_NOT_STICKY
        }

        // Get output file
        val outputFile = File(fileUriString.toUri().path!!)

        // Run the suspend function inside a coroutine
        serviceScope.launch {
            try {
                // Do the actual tts synthesization
                providerInstance.synthesizeTextToFile(
                    text = text,
                    langCode = lang,
                    outputFile = outputFile,
                    progressCallback = { progress: Double, state: ProgressState ->
                        // Update progress of progress listener
                        progressListener?.invoke(progress)

                        // Update notification progress on main thread
                        val progressInt = (progress * 100).toInt()
                        builder.setProgress(100, progressInt, false).setContentText(
                            getString(
                                R.string.tts_generation_notification_progress, progressInt
                            )
                        )
                        notificationManager.notify(NOTIFICATION_ID, builder.build())
                    })

                // When done send broadcast to notify activity that task is done and dismiss notification
                val doneIntent = Intent("com.example.websitereader.AUDIO_GENERATION_COMPLETE")
                LocalBroadcastManager.getInstance(this@ForegroundService).sendBroadcast(doneIntent)

                // Remove foreground status and notification
                stopForeground(STOP_FOREGROUND_REMOVE)
            } catch (e: Exception) {
                e.printStackTrace()
                builder.setContentText("Task failed").setProgress(0, 0, false)
                notificationManager.notify(NOTIFICATION_ID, builder.build())
            } finally {
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }
}