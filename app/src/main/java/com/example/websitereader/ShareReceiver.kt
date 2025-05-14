package com.example.websitereader

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionToken
import com.example.websitereader.tts.Android
import com.example.websitereader.tts.ProgressState
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.net.URI

class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    // Create your Player and MediaSession in the onCreate lifecycle event
    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this).build()
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo
    ): MediaSession? = mediaSession

    // Remember to release the player and media session in onDestroy
    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}

class ShareReceiver : AppCompatActivity() {
    private val websiteFetcher = WebsiteFetcher()
    private lateinit var tts: Android
    private val supportedLanguages = arrayOf("en-US", "de-DE")

    private lateinit var controller: MediaController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_share_receiver)

        val languageSpinner = findViewById<Spinner>(R.id.spinnerLanguage)
        languageSpinner.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_item, supportedLanguages)

        val ttsSpinner = findViewById<Spinner>(R.id.spinnerTts)
        ttsSpinner.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_item, arrayOf("Android", "OpenAI"))

        val sessionToken = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture.addListener(
            {
                //playerView.setPlayer(controllerFuture.get())
                controller = controllerFuture.get()
            }, MoreExecutors.directExecutor()
        )

        // Handle the incoming intent
        handleIncomingIntent()

        tts = Android(this, initCallback = {
            Log.i("tts", "TTS engine initialized")
            findViewById<Button>(R.id.btnGenerateAudio).isEnabled = true
        })
    }

    override fun onDestroy() {
        tts.onDestroy()
        super.onDestroy()
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
                if (uri.scheme.equals("http", ignoreCase = true) || uri.scheme.equals(
                        "https", ignoreCase = true
                    )
                ) {
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

    private fun previewArticle(text: String) {
        val intent = Intent(this, PreviewArticle::class.java)
        intent.putExtra("EXTRA_LONG_TEXT", text)
        startActivity(intent)
    }

    private fun playAudio(audioFile: Uri) {
        var mediaItem = MediaItem.fromUri(audioFile)
        controller.setMediaItem(mediaItem)
        controller.prepare()
        controller.play()
    }

    private suspend fun generateAudio(content: WebsiteFetcher.LocalizedString) {
        assert(content.langCode != null)
        val progressBar = findViewById<ProgressBar>(R.id.generationProgressBar)
        progressBar.visibility = ProgressBar.VISIBLE
        findViewById<Button>(R.id.btnGenerateAudio).isEnabled = false // Disable the button

        val outputFile = File(this@ShareReceiver.filesDir, "audio/output.opus")

        tts.synthesizeTextToFile(
            content,
            outputFile.absolutePath,
            progressCallback = { progress: Double, state: ProgressState ->
                runOnUiThread {
                    if (state == ProgressState.AUDIO_GENERATION) {
                        progressBar.progressDrawable.setTint(getColor(R.color.teal_200))
                    } else {
                        progressBar.progressDrawable.setTint(getColor(R.color.purple_500))
                    }
                    progressBar.progress = (progress * 100).toInt()
                }
            })

        progressBar.visibility = ProgressBar.GONE
        findViewById<Button>(R.id.btnGenerateAudio).isEnabled = true // Enable the button

        val fileUri = FileProvider.getUriForFile(
            this@ShareReceiver,
            this@ShareReceiver.packageName + ".fileprovider",
            outputFile,
        )

        Log.i("tts", "File URI: $fileUri")

        playAudio(fileUri)

        /*
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(fileUri, "audio/ogg")
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(intent)
         */
    }

    private suspend fun processSharedUrl(sharedUrl: String) {
        // Set the url in the ui label
        findViewById<TextView>(R.id.tvUrl).text =
            getString(R.string.share_receiver_url_label, sharedUrl)

        val content = websiteFetcher.processUrl(sharedUrl)
        if (content != null) {
            Log.i("lang", content.langCode ?: "Unknown")

            // Show the rest of the ui (and hide the progress bar)
            findViewById<LinearLayout>(R.id.contentPanel).visibility = LinearLayout.VISIBLE
            findViewById<ProgressBar>(R.id.loadingSpinner).visibility = ProgressBar.GONE

            findViewById<TextView>(R.id.tvArticleInfo).text = getString(
                R.string.share_receiver_article_info_label,
                content.string.split(" ").size,
                content.string.length,
                content.langCode ?: "Unknown"
            )

            // Set the estimated cost
            findViewById<TextView>(R.id.tvEstimatedCost).visibility = TextView.GONE

            if (content.langCode == null) {
                // If no locale is defined fall back to english
                content.langCode = "en_US"
            } else {
                // Set the preselected language in the spinner
                val languageIndex = supportedLanguages.indexOf(content.langCode)
                if (languageIndex != -1) {
                    supportedLanguages[languageIndex] += " (auto detected)"
                    findViewById<Spinner>(R.id.spinnerLanguage).setSelection(languageIndex)
                }
            }

            findViewById<ImageButton>(R.id.btnPreview).setOnClickListener {
                previewArticle(content.string)
            }

            findViewById<Button>(R.id.btnGenerateAudio).setOnClickListener {
                lifecycleScope.launch {
                    generateAudio(content)
                }
            }
        }
    }
}