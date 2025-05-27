package com.example.websitereader

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.websitereader.settings.SettingsActivity
import com.example.websitereader.ui.MainScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MainScreen(
                onSettingsClicked = {
                    startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                }
            )
        }
    }
}