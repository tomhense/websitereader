package com.example.websitereader

import android.util.Log
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import net.dankito.readability4j.extended.Readability4JExtended
import org.jsoup.Jsoup

class WebsiteFetcher {
    private var okHttpClient: OkHttpClient

    class LocalizedString(val string: String, val langCode: String) {}


    constructor() {
        okHttpClient = OkHttpClient()
    }


    @Throws(IOException::class)
    private suspend fun fetchUrl(url: String): String {
        val request = Request.Builder()
            .url(url)
            .build();

        okHttpClient.newCall(request).execute().use { response ->
            return response.body?.string() ?: throw IOException("Unexpected empty response body")
        }
    }



    public suspend fun processUrl(url: String): LocalizedString? {
        return withContext(Dispatchers.IO) { // Ensuring network call is on the IO thread
            try {
                val html = fetchUrl(url) // Call to fetchUrl, assumed to be suspend
                val soup = Jsoup.parse(html)
                val lang = soup.select("html").let { if (it.hasAttr("lang")) it.attr("lang") else "en" }
                val readability4J = Readability4JExtended(url, html)
                val article = readability4J.parse()
                val articleText = article.articleContent?.text()

                if(articleText != null) {
                    LocalizedString(articleText, lang)
                } else {
                    null
                }
            } catch (err: IOException) {
                null
            }
        }
    }
}