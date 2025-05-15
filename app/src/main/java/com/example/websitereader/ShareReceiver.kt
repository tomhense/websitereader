package com.example.websitereader

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.util.Patterns
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageButton
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionToken
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
    private val supportedLanguages = arrayOf("en-US", "de-DE")
    private var foregroundService: ForegroundService? = null
    private var bound = false
    private var article: Article? = null
    private lateinit var controller: MediaController
    private var outputFileUri: Uri? = null

    private lateinit var progressBar: ProgressBar
    private lateinit var languageSpinner: Spinner
    private lateinit var ttsSpinner: Spinner
    private lateinit var generateAudioButton: Button
    private lateinit var previewButton: AppCompatImageButton
    private lateinit var loadingProgressSpinner: ProgressBar
    private lateinit var contentPanel: LinearLayout
    private lateinit var estimatedCostLabel: TextView
    private lateinit var articleInfo: TextView
    private lateinit var urlLabel: TextView
    private lateinit var saveFileButton: AppCompatImageButton

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as ForegroundService.LocalBinder
            foregroundService = binder.getService()
            bound = true

            // Set the progress listener
            binder.setProgressListener { progress ->
                runOnUiThread {
                    progressBar.progress = (progress * 100).toInt()
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            foregroundService = null
            bound = false
        }
    }

    private val createFileLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { destUri: Uri? ->
            if (destUri != null && outputFileUri != null) {
                copyFile(outputFileUri!!, destUri)
            } else {
                Toast.makeText(
                    this, "File not saved (no destination selected).", Toast.LENGTH_SHORT
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_share_receiver)

        // Initialize views
        progressBar = findViewById(R.id.generationProgressBar)
        languageSpinner = findViewById(R.id.spinnerLanguage)
        ttsSpinner = findViewById(R.id.spinnerTts)
        generateAudioButton = findViewById(R.id.btnGenerateAudio)
        previewButton = findViewById(R.id.btnPreview)
        loadingProgressSpinner = findViewById(R.id.loadingSpinner)
        contentPanel = findViewById(R.id.contentPanel)
        estimatedCostLabel = findViewById(R.id.tvEstimatedCost)
        articleInfo = findViewById(R.id.tvArticleInfo)
        urlLabel = findViewById(R.id.tvUrl)
        saveFileButton = findViewById(R.id.btnSaveFile)

        // Set outputFile
        outputFileUri = Uri.fromFile(File(this@ShareReceiver.cacheDir, "output.wav"))
        Log.i("tts", "Output file uri: $outputFileUri")

        // Setup spinners
        languageSpinner.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_item, supportedLanguages)
        ttsSpinner.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_item, arrayOf("Android", "OpenAI"))

        // Setup media controller
        val sessionToken = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture.addListener(
            {
                controller = controllerFuture.get()
            }, MoreExecutors.directExecutor()
        )

        // Handle incoming intents
        handleIncomingIntent()
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter("com.example.websitereader.AUDIO_GENERATION_COMPLETE")
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(audioGenerationCompleteReceiver, filter)
    }

    private fun handleIncomingIntent() {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let { url ->
                CoroutineScope(Dispatchers.Main).launch {
                    validateAndProcessSharedUrl(url)
                }
            }
        }
    }

    private suspend fun validateAndProcessSharedUrl(sharedText: String) {
        if (Patterns.WEB_URL.matcher(sharedText).matches()) {
            try {
                val uri = URI(sharedText)
                if (uri.scheme.equals("http", ignoreCase = true) || uri.scheme.equals(
                        "https", ignoreCase = true
                    )
                ) {
                    processSharedUrl(sharedText) // Valid url
                } else {
                    Log.d("Invalid URL", "Only http/https URLs are supported.")
                }
            } catch (e: Exception) {
                Log.d("Invalid URL", "Invalid URL format: ${e.message}")
            }
        } else {
            Log.d("Invalid URL", "Text does not match URL pattern.")
        }
    }

    private fun previewArticle(article: Article) {
        val intent = Intent(this, PreviewArticle::class.java)
        intent.putExtra(PreviewArticle.EXTRA_HEADLINE, article.headline)
        intent.putExtra(PreviewArticle.EXTRA_CONTENT, article.wholeText)
        startActivity(intent)
    }

    private fun playAudio(audioFile: Uri) {
        var mediaItem = MediaItem.fromUri(audioFile)
        controller.setMediaItem(mediaItem)
        controller.prepare()
        controller.play()
    }

    private fun copyFile(sourceUri: Uri, destUri: Uri) {
        try {
            contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                contentResolver.openOutputStream(destUri)?.use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            Toast.makeText(this, "File copied successfully!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error copying file: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private val audioGenerationCompleteReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            progressBar.visibility = ProgressBar.GONE
            generateAudioButton.isEnabled = true
            saveFileButton.visibility = AppCompatImageButton.VISIBLE

            saveFileButton.setOnClickListener {
                createFileLauncher.launch("audio.wav")
            }

            outputFileUri?.let { playAudio(it) }
        }
    }

    private fun generateAudio(article: Article) {
        progressBar.visibility = ProgressBar.VISIBLE
        generateAudioButton.isEnabled = false // Disable the button
        saveFileButton.visibility = AppCompatImageButton.GONE

        val intent = Intent(this, ForegroundService::class.java)
        intent.putExtra(
            ForegroundService.EXTRA_PROVIDER_CLASS_NAME, "com.example.websitereader.tts.Android"
        )
        intent.putExtra(ForegroundService.EXTRA_TEXT, article.text)
        intent.putExtra(ForegroundService.EXTRA_LANG, article.lang)
        intent.putExtra(ForegroundService.EXTRA_FILE_URI, outputFileUri.toString())
        startService(intent).also {
            bindService(intent, connection, BIND_AUTO_CREATE)
        }
    }

    private suspend fun processSharedUrl(sharedUrl: String) {
        // Set the url in the ui label
        urlLabel.text = getString(R.string.share_receiver_url_label, sharedUrl)

        // Get the article from the url, this will take a while
        article = Article.fromUrl(sharedUrl)
        if (article == null) {
            Log.i("article", "Article is null")
            return
        }

        // Now we have an article, setup the ui further
        contentPanel.visibility = LinearLayout.VISIBLE
        loadingProgressSpinner.visibility = ProgressBar.GONE
        articleInfo.text = getString(
            R.string.share_receiver_article_info_label,
            article!!.text.split(" ").size,
            article!!.text.length,
            article!!.lang ?: "Unknown"
        )

        // Set the estimated cost
        estimatedCostLabel.visibility = TextView.GONE

        // Setup language spinner
        languageSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>, view: View?, position: Int, id: Long
            ) {
                val selectedLanguage = supportedLanguages[position].removeSuffix(" (auto detected)")
                Log.i("tts", "Set article language to $selectedLanguage")
                article!!.lang = selectedLanguage
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        if (article!!.lang != null) {
            // Mark the auto detected language in the spinner
            val languageIndex = supportedLanguages.indexOf(article!!.lang)
            if (languageIndex != -1) {
                supportedLanguages[languageIndex] += " (auto detected)"
                languageSpinner.setSelection(languageIndex)
            }
        } else {
            Log.i("tts", "No language detected, falling back to default")
            languageSpinner.setSelection(0)
            article!!.lang = supportedLanguages[0]
        }

        // Setup preview button
        previewButton.setOnClickListener {
            previewArticle(article!!)
        }

        generateAudioButton.setOnClickListener {
            generateAudio(article!!)
        }
    }

    override fun onStop() {
        if (bound) {
            unbindService(connection)
            bound = false
        }
        LocalBroadcastManager.getInstance(this).unregisterReceiver(audioGenerationCompleteReceiver)
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}