package com.example.websitereader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.websitereader.settings.TTSProviderStore
import com.example.websitereader.ui.SettingsScreen

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TTSProviderStore.load(this)
        setContent {
            SettingsScreen(finishActivity = { finish() })
        }
    }
}