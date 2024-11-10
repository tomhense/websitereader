package com.example.websitereader

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import net.dankito.readability4j.extended.Readability4JExtended

class WebsiteFetcher {
    private var okHttpClient: OkHttpClient

    constructor() {
        okHttpClient = OkHttpClient()
    }


    @Throws(IOException::class)
    private fun fetchUrl(url: String): String {
        val request = Request.Builder()
            .url(url)
            .build();

        okHttpClient.newCall(request).execute().use { response ->
            return response.body?.string() ?: throw IOException("Unexpected empty response body")
        }
    }


    public fun processUrl(url: String): String? {
        try {
            val html = fetchUrl(url)
            val readability4J = Readability4JExtended(url, html)
            val article = readability4J.parse()
            return article.contentWithUtf8Encoding
        } catch (err: IOException) {
            return null
        }
    }
}