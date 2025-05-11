package com.example.websitereader

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class PreviewArticle : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //enableEdgeToEdge()
        setContentView(R.layout.activity_preview_article)

        // Get the text passed from the Intent
        val longText = intent.getStringExtra("EXTRA_LONG_TEXT")

        // Find your TextView in the layout
        val textView: TextView = findViewById(R.id.textViewLongText)
        textView.text = longText
    }
}