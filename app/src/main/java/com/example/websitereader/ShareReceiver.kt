package com.example.websitereader

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.websitereader.settings.TTSProviderStore
import com.example.websitereader.ui.ShareReceiverScreen

class ShareReceiver : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        TTSProviderStore.load(this)
        setContent {
            ShareReceiverScreen(
                sharedUrl = (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain").let {
                    intent?.getStringExtra(Intent.EXTRA_TEXT) ?: ""
                },
                finishActivity = { finish() },
            )
        }
    }
}