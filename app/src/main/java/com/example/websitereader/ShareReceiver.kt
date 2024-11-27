package com.example.websitereader

import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.util.Patterns
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI

class ShareReceiver : AppCompatActivity() {
    val websiteFetcher = WebsiteFetcher()
    lateinit var ttsReader: TTSReader

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //setContentView(R.layout.activity_your)

        // Handle the incoming intent
        handleIncomingIntent()

        //websiteFetcher = WebsiteFetcher()
        ttsReader = TTSReader(this)
    }

    private fun handleIncomingIntent() {
        // Check if the intent has the type of a share action
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            // Get the shared text
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)

            // Check if the shared text is a URL
            sharedText?.let { url ->
                // Do something with the URL

                val scope = CoroutineScope(Dispatchers.Main)
                scope.launch {
                    validateAndProcessSharedUrl(url)
                }
            }
        }
    }


    private suspend fun validateAndProcessSharedUrl(sharedText: String) {
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

    private suspend fun processSharedUrl(sharedUrl: String) {
        val content = websiteFetcher.processUrl(sharedUrl)
        if(content != null) {
            //ttsReader.speak(content)

            CoroutineScope(Dispatchers.Default).launch {
                while(ttsReader.readyStatus != TextToSpeech.SUCCESS) {
                    delay(100)
                }
                withContext(Dispatchers.Main) {
                    ttsReader.synthesizeTextToFile(this@ShareReceiver, content, "audio/test.opus", { result -> if(result != null) {
                        Log.i("lang", content.langCode)
                        val fileUri = FileProvider.getUriForFile(this@ShareReceiver, "com.example.websitereader.fileprovider", File(result))
                        val intent = Intent(Intent.ACTION_VIEW)
                        intent.setDataAndType(fileUri, "audio/ogg")
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        startActivity(intent)

                        runOnUiThread({
                            ttsReader.playAudio(this@ShareReceiver, result)
                        })
                    }})
                }
            }
        }
    }
}