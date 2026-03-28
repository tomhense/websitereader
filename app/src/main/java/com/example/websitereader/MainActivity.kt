package com.example.websitereader

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.websitereader.ui.MainScreen
import com.example.websitereader.ui.WebsiteReaderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WebsiteReaderTheme {
                MainScreen(
                    onSettingsClicked = {
                        startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                    }
                )
            }
        }
    }
}
