package com.example.websitereader.ui

import android.content.Context
import android.content.Context.BIND_AUTO_CREATE
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.util.Patterns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat.startActivity
import androidx.core.net.toUri
import com.example.websitereader.PreviewArticle
import com.example.websitereader.R
import com.example.websitereader.audioplayer.AudioPlayer
import com.example.websitereader.foregroundservice.AudioGenerationServiceConnector
import com.example.websitereader.foregroundservice.ForegroundService
import com.example.websitereader.model.Article
import com.example.websitereader.model.TTSProvider
import com.example.websitereader.settings.TTSProviderStore

private fun copyFile(context: Context, sourceUri: Uri, destUri: Uri) {
    try {
        context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
            context.contentResolver.openOutputStream(destUri)?.use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
        Toast.makeText(context, "File copied successfully!", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Log.e("ShareReceiver", "File copy failed: ${e.message}")
        Toast.makeText(context, "Error copying file: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

private fun shareFile(context: Context, uri: Uri) {
    val outputBaseFileName = uri.lastPathSegment ?: "output.wav"
    val extension = outputBaseFileName.substringAfterLast('.', "")
    val fileType = when (extension) {
        "wav" -> "audio/wav"
        "mp3" -> "audio/mpeg"
        else -> "audio/*"
    }

    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = fileType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(shareIntent, null)
    context.startActivity(chooser)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareReceiverScreen(
    sharedUrl: String,
) {
    val context = LocalContext.current

    var urlToProcess by remember { mutableStateOf(sharedUrl) }
    var urlError by remember { mutableStateOf<String?>(null) }

    val externalTTSProviderList by TTSProviderStore.providers.collectAsState()
    val ttsProviderList: List<TTSProvider> by remember {
        mutableStateOf(
            listOf(
                com.example.websitereader.model.SystemTTSProvider
            ) + externalTTSProviderList
        )
    }
    var selectedTTSProviderIndex by remember { mutableStateOf<Int?>(0) }

    var isGenerating by remember { mutableStateOf(false) }
    var outputFile by remember { mutableStateOf<Uri?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    var article by remember { mutableStateOf<Article?>(null) }

    val supportedLanguages by remember { mutableStateOf(listOf("en-US", "de-DE")) }
    var selectedLanguageIndex by remember { mutableStateOf<Int?>(0) }

    // Progress state
    var progress by remember { mutableFloatStateOf(0f) }
    var isBound by remember { mutableStateOf(false) }
    var service by remember { mutableStateOf<ForegroundService?>(null) }

    // Audio Player
    val audioPlayer = remember { AudioPlayer(context) }

    // Remember the connector so it's not recreated on every recomposition
    val connector = remember {
        AudioGenerationServiceConnector(
            onProgress = { p -> progress = p },
            onSucceeded = { result ->
                progress = 0f
                isGenerating = false
                outputFile = result.toUri()
            },
            onError = { error ->
                progress = 0f
                isGenerating = false
                errorMsg = error
            },
            onServiceConnectedCallback = { svc ->
                service = svc
                isBound = true
            },
            onServiceDisconnectedCallback = {
                service = null
                isBound = false
            })
    }

    // Create file launcher
    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { destUri: Uri? ->
        if (destUri != null) {
            outputFile?.let { copyFile(context, it, destUri) }
        } else {
            Toast.makeText(
                context, "File not saved (no destination selected).", Toast.LENGTH_SHORT
            ).show()
        }
    }


    // Bind/unbind using DisposableEffect (runs on enter/exit composition)
    DisposableEffect(Unit) {
        val intent = Intent(context, ForegroundService::class.java)
        context.bindService(intent, connector, BIND_AUTO_CREATE)
        onDispose {
            context.unbindService(connector)
        }
    }

    // Whenever urlToProcess changes, load the article
    LaunchedEffect(urlToProcess) {
        article = null // Clear previous article, so the spinner shows
        article = Article.fromUrl(urlToProcess)
    }

    // Whenever article changes, reset the language index
    LaunchedEffect(article) {
        if (article != null && article!!.lang != null) {
            supportedLanguages.find { it == article!!.lang }?.let {
                selectedLanguageIndex = supportedLanguages.indexOf(it)
            }
        } else {
            selectedLanguageIndex = 0
        }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Share URL to Audio") })
    }, floatingActionButton = {
        Row {
            if (article != null) {
                FloatingActionButton(
                    onClick = {
                        val intent = Intent(context, PreviewArticle::class.java)
                        intent.putExtra(PreviewArticle.EXTRA_HEADLINE, article!!.headline)
                        intent.putExtra(PreviewArticle.EXTRA_CONTENT, article!!.wholeText)
                        startActivity(context, intent, null)
                        context.bindService(intent, connector, BIND_AUTO_CREATE)
                    }, modifier = Modifier.padding(end = 16.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.baseline_article_24),
                        contentDescription = stringResource(id = R.string.share_receiver_preview_article_button)
                    )
                }
            }
            FloatingActionButton(
                onClick = {
                    if (!isGenerating) {
                        // Input validation
                        if (!Patterns.WEB_URL.matcher(urlToProcess).matches()) {
                            urlError = "Invalid URL"
                            return@FloatingActionButton
                        }
                        val ttsProvider =
                            selectedTTSProviderIndex?.let { idx -> ttsProviderList[idx] }
                        if (ttsProvider == null) {
                            errorMsg = "No TTS provider selected"
                            return@FloatingActionButton
                        }
                        if (article == null) {
                            return@FloatingActionButton
                        }

                        isGenerating = true
                        errorMsg = null

                        val intent = Intent(context, ForegroundService::class.java)
                        intent.putExtra(ForegroundService.EXTRA_ARTICLE, article!!.toJson())
                        intent.putExtra(
                            ForegroundService.EXTRA_PROVIDER_NAME, when (ttsProvider) {
                                is com.example.websitereader.model.SystemTTSProvider -> context.getString(
                                    R.string.system_tts_provider_name
                                )

                                is com.example.websitereader.model.ExternalTTSProvider -> ttsProvider.name
                            }
                        )
                        context.startForegroundService(intent)
                    } else {
                        // Stop the generation
                        val intent = Intent(context, ForegroundService::class.java)
                        intent.action = ForegroundService.ACTION_STOP
                        context.startForegroundService(intent)
                    }
                }) {
                if (isGenerating) {
                    Icon(
                        painter = painterResource(R.drawable.baseline_stop_24),
                        contentDescription = "Stp["
                    )
                } else {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Generate")
                }
            }
        }
    }) { padding ->
        Column(
            Modifier
                .fillMaxWidth()
                .padding(padding)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = urlToProcess,
                onValueChange = {
                    urlToProcess = it
                    urlError = null
                },
                isError = urlError != null,
                enabled = !isGenerating,
                label = { Text("Article URL") },
                modifier = Modifier.fillMaxWidth()
            )

            if (article != null) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    KeyValueRow("Headline", article!!.headline)
                    KeyValueRow("Detected Language", article!!.lang ?: "Unknown")
                    KeyValueRow("Words", article!!.text.split(Regex("\\s+")).size.toString())
                    KeyValueRow("Characters", article!!.text.length.toString())
                }
            }

            if (urlError != null) {
                Text(
                    text = urlError!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(Modifier.height(24.dp))
            if (article != null) {
                Text("Choose TTS Provider:", style = MaterialTheme.typography.bodyLarge)
                LazyColumn {
                    itemsIndexed(ttsProviderList) { idx, provider ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { selectedTTSProviderIndex = idx },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedTTSProviderIndex == idx,
                                enabled = !isGenerating,
                                onClick = { selectedTTSProviderIndex = idx })
                            Spacer(Modifier.width(12.dp))
                            Text(
                                when (provider) {
                                    is com.example.websitereader.model.SystemTTSProvider -> context.getString(
                                        R.string.system_tts_provider_name
                                    )

                                    is com.example.websitereader.model.ExternalTTSProvider -> provider.name
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Text("Choose Language:", style = MaterialTheme.typography.bodyLarge)
                LazyColumn {
                    itemsIndexed(supportedLanguages) { idx, lang ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    article!!.lang = lang
                                    selectedLanguageIndex = idx
                                }, verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedLanguageIndex == idx,
                                enabled = !isGenerating,
                                onClick = { article!!.lang = lang; selectedLanguageIndex = idx })
                            Spacer(Modifier.width(12.dp))
                            Text(lang)
                        }
                    }
                }

                if (isGenerating || article == null) {
                    Box(
                        Modifier.fillMaxWidth(), contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            progress = { if (isGenerating) progress else 0.0f },
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }

                if (errorMsg != null) {
                    Spacer(Modifier.height(16.dp))
                    Text(errorMsg!!, color = MaterialTheme.colorScheme.error)
                }

                if (!isGenerating && outputFile != null) {
                    Card(
                        modifier = Modifier.padding(8.dp),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Row {
                            Text(
                                outputFile!!.lastPathSegment ?: "output.wav",
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier
                                    .padding(16.dp)
                                    .weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            IconButton(onClick = {
                                val outputBaseFileName =
                                    outputFile!!.lastPathSegment ?: "output.wav"
                                fileLauncher.launch(outputBaseFileName)
                            }) {
                                Icon(
                                    painter = painterResource(R.drawable.baseline_save_24),
                                    contentDescription = stringResource(id = R.string.share_receiver_save_file_button),
                                    modifier = Modifier
                                        .size(48.dp)
                                        .padding(8.dp)
                                )
                            }
                            IconButton(onClick = {
                                shareFile(context, outputFile!!)
                            }) {
                                Icon(
                                    painter = painterResource(R.drawable.baseline_share_24),
                                    contentDescription = "Share",
                                    modifier = Modifier
                                        .size(48.dp)
                                        .padding(8.dp)
                                )
                            }
                        }
                    }

                    AudioPlayerCard(
                        audioPlayer, outputFile!!, modifier = Modifier.padding(top = 16.dp)
                    )
                }
            }
        }
    }
}

@Preview
@Composable
fun ShareReceiverScreenPreview() {
    ShareReceiverScreen(sharedUrl = "https://example.com")
}
