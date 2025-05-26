package com.example.websitereader.model

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.dankito.readability4j.extended.Readability4JExtended
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException

class Article(
    val url: String,
    var lang: String?,
    val headline: String,
    val text: String,
    val wholeText: String
) {

    companion object {
        private val okHttpClient = OkHttpClient()

        suspend fun fromUrl(url: String): Article? {
            return withContext(Dispatchers.IO) {
                try {
                    val html = fetchUrl(url)
                    val soup = Jsoup.parse(html)
                    val lang = ArticleUtils.determineLanguage(soup)
                    val readability4J = Readability4JExtended(url, html)
                    val article = readability4J.parse()

                    // articleText contains trimmed and normalized text, linebreaks are also
                    // removed this is perfect for TTS but less for a preview
                    val articleText = article.articleContent?.text()

                    // wholeText contains untrimmed and unnormalized text, linebreaks are preserved,
                    // perfect for a preview. But we should remove trim it a bit and remove excessive linebreaks
                    val wholeText = article.articleContent?.wholeText()
                        ?.trim()
                        ?.replace(Regex("[ \\t]+"), " ")
                        ?.replace(Regex("\r\n"), "\n")
                        ?.replace(Regex(" *(\n+) *"), "$1")
                        ?.replace(Regex("\n{2,}"), "\n\n")

                    val headline = article.title ?: soup.title()

                    if (articleText != null && headline != null && wholeText != null) {
                        Article(url, lang, headline, articleText, wholeText)
                    } else {
                        null
                    }
                } catch (_: IOException) {
                    null
                }
            }
        }

        @Throws(IOException::class)
        private fun fetchUrl(url: String): String {
            val request = Request.Builder().url(url).build()
            okHttpClient.newCall(request).execute().use { response ->
                return response.body?.string()
                    ?: throw IOException("Unexpected empty response body")
            }
        }
    }
}

object ArticleUtils {

    /**
     * Determines the language from the html `lang` attribute and normalizes it.
     * Returns null if no valid language found.
     */
    fun determineLanguage(soup: Document): String? {
        val htmlElement = soup.selectFirst("html") ?: return null
        val langAttr = htmlElement.attr("lang").takeIf { it.isNotBlank() } ?: return null

        return when {
            Regex("^[a-z]{2}-[A-Z]{2}$").matches(langAttr) -> langAttr
            else -> {
                val langMappings = mapOf(
                    "en" to "en-US",
                    "de" to "de-DE",
                    "deu" to "de-DE",
                    "eng" to "en-US",
                )
                langMappings[langAttr]
            }
        }
    }
}