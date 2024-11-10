package com.example.websitereader

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import androidx.appcompat.app.AppCompatActivity
import java.net.URI

class  ShareReceiver : AppCompatActivity() {
    lateinit var websiteFetcher: WebsiteFetcher
    lateinit var ttsReader: TTSReader

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //setContentView(R.layout.activity_your)

        // Handle the incoming intent
        handleIncomingIntent()

        websiteFetcher = WebsiteFetcher()
        ttsReader = TTSReader()
    }

    private fun handleIncomingIntent() {
        // Check if the intent has the type of a share action
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            // Get the shared text
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)

            // Check if the shared text is a URL
            sharedText?.let { url ->
                // Do something with the URL
                validateAndProcessSharedUrl(url)
            }
        }
    }


    private fun validateAndProcessSharedUrl(sharedText: String) {
        // Check if the shared text matches a URL pattern
        if (Patterns.WEB_URL.matcher(sharedText).matches()) {
            try {
                val uri = URI(sharedText)
                // Ensure it starts with http or https
                if (uri.scheme.equals("http", ignoreCase = true) || uri.scheme.equals("https", ignoreCase = true)) {
                    // It's a valid URL, process further
                    processSharedUrl(sharedText)
                } else {
                    // The URL does not start with http/https
                    Log.d("Invalid URL", "Only http/https URLs are supported.")
                }
            } catch (e: Exception) {
                // Handle the case where URI parsing fails
                Log.d("Invalid URL", "Invalid URL format: ${e.message}")
            }
        } else {
            Log.d("Invalid URL", "Text does not match URL pattern.")
        }
    }

    private fun processSharedUrl(sharedUrl: String) {
        val content = websiteFetcher.processUrl(sharedUrl)
        if(content != null) {
            ttsReader.speak(content)
        }
    }
}