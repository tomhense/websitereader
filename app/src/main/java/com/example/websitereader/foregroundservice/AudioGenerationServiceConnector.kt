package com.example.websitereader.foregroundservice

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder

class AudioGenerationServiceConnector(
    private val onProgress: (Float) -> Unit,
    private val onServiceConnectedCallback: (ForegroundService) -> Unit = {},
    private val onServiceDisconnectedCallback: () -> Unit = {}
) : ServiceConnection {
    var service: ForegroundService? = null
        private set
    var bound: Boolean = false
        private set

    override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
        service = (binder as ForegroundService.LocalBinder).getService()
        bound = true
        binder.setProgressListener { progress ->
            onProgress(progress.toFloat())
        }
        onServiceConnectedCallback(service!!)
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        service = null
        bound = false
        onServiceDisconnectedCallback()
    }
}