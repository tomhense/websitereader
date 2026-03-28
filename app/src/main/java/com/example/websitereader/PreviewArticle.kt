package com.example.websitereader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.websitereader.ui.PreviewArticleScreen
import com.example.websitereader.ui.WebsiteReaderTheme

class PreviewArticle : ComponentActivity() {
    companion object {
        const val EXTRA_HEADLINE = "EXTRA_HEADLINE"
        const val EXTRA_CONTENT = "EXTRA_CONTENT"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val headline = intent.getStringExtra(EXTRA_HEADLINE) ?: ""
        val content = intent.getStringExtra(EXTRA_CONTENT) ?: ""

        setContent {
            WebsiteReaderTheme {
                PreviewArticleScreen(headline = headline, content = content)
            }
        }
    }
}
