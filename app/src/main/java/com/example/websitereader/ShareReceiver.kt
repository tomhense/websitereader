package com.example.websitereader

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.view.WindowInsetsController
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.websitereader.databinding.ActivityShareReceiverBinding
import com.example.websitereader.foregroundservice.AudioGenerationBroadcastReceiver
import com.example.websitereader.foregroundservice.AudioGenerationServiceConnector
import com.example.websitereader.foregroundservice.ForegroundService
import com.example.websitereader.settings.TTSProviderEntry
import com.example.websitereader.settings.TTSProviderEntryStorage
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.net.URI

class ShareReceiver : AppCompatActivity() {
    private lateinit var binding: ActivityShareReceiverBinding
    private val supportedLanguages = arrayOf("en-US", "de-DE")
    private var article: Article? = null
    private lateinit var outputFileUri: Uri
    private lateinit var audioPlayer: AudioPlayer
    private lateinit var serviceConnector: AudioGenerationServiceConnector
    private lateinit var requestNotificationPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var ttsProviders: List<TTSProviderEntry>
    private lateinit var ttsProviderNames: ImmutableList<String>
    private var selectedTtsProvider: TTSProviderEntry? = null
    private lateinit var createFileLauncher: ActivityResultLauncher<String>

    private val broadcastReceiver by lazy {
        AudioGenerationBroadcastReceiver(
            onComplete = ::onAudioGenerationComplete, onFailure = ::onAudioGenerationFailed
        )
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityShareReceiverBinding.inflate(layoutInflater)
        setContentView(binding.root)


        // Handle status bar color
        val isDarkTheme =
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        if (isDarkTheme) {
            window.insetsController?.setSystemBarsAppearance(
                0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            )
        } else {
            window.insetsController?.setSystemBarsAppearance(
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            )
        }

        // Handle insets
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootPanel) { v, insets ->
            val sysInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(
                top = sysInsets.top,
                bottom = sysInsets.bottom,
                left = v.paddingLeft,
                right = v.paddingRight,
            )
            WindowInsetsCompat.CONSUMED
        }

        // UI initialization
        outputFileUri = Uri.fromFile(File(this@ShareReceiver.cacheDir, "output.wav"))
        audioPlayer = AudioPlayer(this)

        // Get tts providers
        ttsProviders = TTSProviderEntryStorage.load(this)
        ttsProviderNames =
            ImmutableList.Builder<String>().add(getString(R.string.system_tts_provider_name))
                .addAll(ttsProviders.map { it.name }).build()

        // Setup spinners
        binding.spinnerLanguage.setAdapter(
            android.widget.ArrayAdapter(
                this, android.R.layout.simple_list_item_1, supportedLanguages
            )
        )

        binding.spinnerTts.setAdapter(
            android.widget.ArrayAdapter(
                this, android.R.layout.simple_list_item_1, ttsProviderNames
            )
        )

        // Permissions
        requestNotificationPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (!isGranted) {
                Toast.makeText(
                    this, R.string.permission_notification_not_granted_message, Toast.LENGTH_SHORT
                ).show()
            }
        }

        // Service connection
        serviceConnector = AudioGenerationServiceConnector(
            onProgress = { progress ->
                runOnUiThread {
                    binding.generationProgressBar.progress = (progress * 100).toInt()
                }
            })

        // Create file launcher
        createFileLauncher =
            registerForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { destUri: Uri? ->
                if (destUri != null) {
                    copyFile(outputFileUri, destUri)
                } else {
                    Toast.makeText(
                        this, "File not saved (no destination selected).", Toast.LENGTH_SHORT
                    ).show()
                }
            }

        // Handle Intent
        handleIncomingIntent()
    }

    override fun onStart() {
        super.onStart()
        LocalBroadcastManager.getInstance(this).registerReceiver(
            broadcastReceiver.completeReceiver,
            IntentFilter("com.example.websitereader.AUDIO_GENERATION_COMPLETE")
        )
        LocalBroadcastManager.getInstance(this).registerReceiver(
            broadcastReceiver.failedReceiver,
            IntentFilter("com.example.websitereader.AUDIO_GENERATION_FAILED")
        )
    }

    override fun onStop() {
        if (serviceConnector.bound) unbindService(serviceConnector)
        LocalBroadcastManager.getInstance(this)
            .unregisterReceiver(broadcastReceiver.completeReceiver)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver.failedReceiver)
        super.onStop()
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun handleIncomingIntent() {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let { url ->
                CoroutineScope(Dispatchers.Main).launch {
                    validateAndProcessSharedUrl(url)
                }
            }
        }
    }

    private fun checkNotificationPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun validateAndProcessSharedUrl(sharedText: String) {
        if (Patterns.WEB_URL.matcher(sharedText).matches()) {
            try {
                val uri = URI(sharedText)
                if (uri.scheme.equals("http", ignoreCase = true) || uri.scheme.equals(
                        "https", ignoreCase = true
                    )
                ) {
                    processSharedUrl(sharedText)
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

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun processSharedUrl(sharedUrl: String) {
        binding.tvUrl.text = getString(R.string.share_receiver_url_label, sharedUrl)
        article = Article.fromUrl(sharedUrl)
        if (article == null) {
            Log.i("article", "Article is null")
            return
        }
        // Show content/UI
        binding.contentPanel.visibility = android.view.View.VISIBLE
        binding.loadingSpinner.visibility = android.view.View.GONE
        binding.tvArticleInfo.text = getString(
            R.string.share_receiver_article_info_label,
            article!!.text.split(" ").size,
            article!!.text.length,
            article!!.lang ?: "Unknown"
        )

        // Set TTS provider
        binding.spinnerTts.setText(ttsProviderNames[0], false)
        binding.spinnerTts.setOnItemClickListener { parent, view, position, id ->
            Log.i("tts", "Set TTS provider to ${ttsProviderNames[position]}")
            if (position == 0) {
                selectedTtsProvider = null
                binding.tvEstimatedCost.visibility = android.view.View.GONE

            } else {
                selectedTtsProvider = ttsProviders[position - 1]
                val cost =
                    ttsProviders[position - 1].pricePer1MCharacters * article!!.text.length / 1000000
                binding.tvEstimatedCost.visibility = android.view.View.VISIBLE
                binding.tvEstimatedCost.text =
                    getString(R.string.share_receiver_estimated_cost_label, cost)

            }
        }

        // Hide estimated cost
        binding.tvEstimatedCost.visibility = android.view.View.GONE

        // Language spinner
        binding.spinnerLanguage.setOnItemClickListener { parent, view, position, id ->
            val selectedLanguage = supportedLanguages[position].removeSuffix(" (auto detected)")
            Log.i("tts", "Set article language to $selectedLanguage")
            article!!.lang = selectedLanguage
        }
        if (article!!.lang != null) {
            val languageIndex = supportedLanguages.indexOf(article!!.lang)
            if (languageIndex != -1) {
                val displayLanguages = supportedLanguages.toMutableList()
                displayLanguages[languageIndex] += " (auto detected)"
                binding.spinnerLanguage.setText(displayLanguages[languageIndex], false)
            }
        } else {
            binding.spinnerLanguage.setText(supportedLanguages[0], false)
            article!!.lang = supportedLanguages[0]
        }

        // Button listeners
        binding.btnPreview.setOnClickListener {
            previewArticle(article!!)
        }
        binding.btnGenerateAudio.setOnClickListener {
            generateAudio(article!!)
        }
    }

    private fun previewArticle(article: Article) {
        val intent = Intent(this, PreviewArticle::class.java)
        intent.putExtra(PreviewArticle.EXTRA_HEADLINE, article.headline)
        intent.putExtra(PreviewArticle.EXTRA_CONTENT, article.wholeText)
        startActivity(intent)
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
            Log.e("ShareReceiver", "File copy failed: ${e.message}")
            Toast.makeText(this, "Error copying file: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun onAudioGenerationComplete() {
        binding.generationProgressBar.visibility = android.view.View.GONE
        binding.btnGenerateAudio.isEnabled = true
        binding.btnSaveFile.visibility = android.view.View.VISIBLE
        binding.btnSaveFile.setOnClickListener {
            createFileLauncher.launch("audio.wav")
        }
        audioPlayer.playAudio(outputFileUri)
    }

    private fun onAudioGenerationFailed() {
        binding.generationProgressBar.visibility = android.view.View.GONE
        binding.btnGenerateAudio.isEnabled = true
        binding.btnSaveFile.visibility = android.view.View.VISIBLE
    }

    private fun showCostDisclaimerDialog(
        onConfirmed: () -> Unit = {}, onDeclined: () -> Unit = {}
    ) {
        val prefsName = "my_prefs"
        val key = "disclaimer_api_costs_confirmed"
        val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val alreadyConfirmed = prefs.getBoolean(key, false)

        if (!alreadyConfirmed) {
            MaterialAlertDialogBuilder(this).setTitle("Disclaimer")
                .setMessage("I am not responsible for any costs that might occur by using external TTS providers you have configured such as OpenAI.")
                .setCancelable(false).setPositiveButton("Confirm") { dialog, _ ->
                    prefs.edit().putBoolean(key, true).apply()
                    onConfirmed()
                }.setNegativeButton("Cancel", { dialog, _ ->
                    onDeclined()
                }).show()
        } else {
            onConfirmed()
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun generateAudio(article: Article) {
        // Show disclaimer dialog about api costs
        showCostDisclaimerDialog(
            onConfirmed = {
                if (!checkNotificationPermission()) {
                    requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    return@showCostDisclaimerDialog
                }
                binding.generationProgressBar.visibility = android.view.View.VISIBLE
                binding.btnGenerateAudio.isEnabled = false
                binding.btnSaveFile.visibility = android.view.View.GONE

                val intent = Intent(this, ForegroundService::class.java)
                intent.putExtra(ForegroundService.EXTRA_TEXT, article.text)
                intent.putExtra(ForegroundService.EXTRA_LANG, article.lang)
                intent.putExtra(ForegroundService.EXTRA_FILE_URI, outputFileUri.toString())
                if (selectedTtsProvider != null) {
                    intent.putExtra(
                        ForegroundService.EXTRA_PROVIDER_ENTRY,
                        TTSProviderEntryStorage.toJson(selectedTtsProvider!!)
                    )
                }

                startService(intent)
                bindService(intent, serviceConnector, BIND_AUTO_CREATE)
            },
            onDeclined = {
                Toast.makeText(
                    this,
                    "Please confirm the disclaimer to continue.",
                    Toast.LENGTH_SHORT
                )
                    .show()
                return@showCostDisclaimerDialog
            })
    }
}