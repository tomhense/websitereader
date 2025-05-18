package com.example.websitereader.foregroundservice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AudioGenerationBroadcastReceiver(
    private val onComplete: () -> Unit, private val onFailure: () -> Unit
) {
    val completeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            onComplete()
        }
    }

    val failedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            onFailure()
        }
    }
}