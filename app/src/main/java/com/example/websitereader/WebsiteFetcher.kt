package com.example.websitereader

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.dankito.readability4j.extended.Readability4JExtended
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException

class WebsiteFetcher() {
    private var okHttpClient: OkHttpClient = OkHttpClient()

    class LocalizedString(val string: String, var langCode: String?) {}


    @Throws(IOException::class)
    private fun fetchUrl(url: String): String {
        val request = Request.Builder().url(url).build();

        okHttpClient.newCall(request).execute().use { response ->
            return response.body?.string() ?: throw IOException("Unexpected empty response body")
        }
    }

    private fun determineLanguage(soup: Document): String? {
        val langAttr = soup.select("html").let { if (it.hasAttr("lang")) it.attr("lang") else null }

        if (langAttr == null) {
            // If lang attribute is not present, return null
            return null
        } else if (Regex("^[a-z]{2}-[A-Z]{2}$").matches(langAttr)) {
            // The lang attribute should in theory be in the BCP47 format
            return langAttr
        } else {
            // Try to fix the lang attribute by trying a few mappings
            val langMappings = mapOf(
                "en" to "en-US",
                "de" to "de-DE",
                "deu" to "de-DE",
                "eng" to "en-US",
            )
            return if (langMappings.containsKey(langAttr)) {
                langMappings[langAttr]
            } else {
                null
            }
        }
    }

    suspend fun processUrl(url: String): LocalizedString? {
        return withContext(Dispatchers.IO) { // Ensuring network call is on the IO thread
            try {
                val html = fetchUrl(url) // Call to fetchUrl, assumed to be suspend
                val soup = Jsoup.parse(html)
                val lang = determineLanguage(soup)
                val readability4J = Readability4JExtended(url, html)
                val article = readability4J.parse()
                val articleText = article.articleContent?.text()

                if (articleText != null) {
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