package com.example.websitereader

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class PreviewArticle : AppCompatActivity() {
    companion object {
        const val EXTRA_HEADLINE = "EXTRA_HEADLINE"
        const val EXTRA_CONTENT = "EXTRA_CONTENT"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_preview_article)

        // Get parameters passed to the intent
        val content = intent.getStringExtra(EXTRA_CONTENT)
        val headline = intent.getStringExtra(EXTRA_HEADLINE)

        findViewById<TextView>(R.id.textViewHeadline).text = headline
        findViewById<TextView>(R.id.textViewLongText).text = content
    }
}