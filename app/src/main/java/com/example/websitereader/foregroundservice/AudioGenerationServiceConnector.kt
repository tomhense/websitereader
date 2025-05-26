package com.example.websitereader.foregroundservice

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder

class AudioGenerationServiceConnector(
    private val onProgress: (Float) -> Unit,
    private val onServiceConnectedCallback: (ForegroundService) -> Unit = {},
    private val onServiceDisconnectedCallback: () -> Unit = {},
    private val onSucceeded: (String) -> Unit,
    private val onError: (String) -> Unit
) : ServiceConnection {
    private var service: ForegroundService? = null
    private var bound: Boolean = false

    override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
        service = (binder as ForegroundService.LocalBinder).getService()
        bound = true
        binder.setProgressListener { progress ->
            onProgress(progress.toFloat())
        }
        binder.setSucceededListener { result ->
            onSucceeded(result)
        }
        binder.setErrorListener { error ->
            onError(error)
        }
        onServiceConnectedCallback(service!!)
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        service = null
        bound = false
        onServiceDisconnectedCallback()
    }
}