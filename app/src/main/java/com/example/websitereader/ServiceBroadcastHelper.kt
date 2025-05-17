package com.example.websitereader

import android.content.Context
import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager

object ServiceBroadcastHelper {
    fun send(context: Context, action: String) {
        LocalBroadcastManager.getInstance(context).sendBroadcast(Intent(action))
    }
}