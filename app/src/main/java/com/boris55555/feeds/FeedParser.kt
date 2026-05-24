package com.boris55555.feeds

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.dankito.readability4j.extended.Readability4JExtended
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

class FeedParser {
    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun discoverFeeds(url: String): List<FeedSource> = withContext(Dispatchers.IO) {
        val normalizedUrl = normalizeUrl(url)
        val discoveredSources = mutableListOf<FeedSource>()
        try {
            val request = Request.Builder().url(normalizedUrl).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                
                val body = response.body?.string() ?: return@withContext emptyList()
                val finalUrl = response.request.url.toString()

                if (isHtml(body)) {
                    val doc = Jsoup.parse(body, finalUrl)
                    val links = doc.select("link[rel=alternate][type*=rss], link[rel=alternate][type*=atom]")
                    links.forEach { link ->
                        val href = link.attr("abs:href")
                        val title = link.attr("title").ifBlank { href }
                        if (href.isNotBlank()) {
                            discoveredSources.add(FeedSource(title, href))
                        }
                    }
                    
                    if (discoveredSources.isEmpty()) {
                        val commonPaths = listOf("/feed", "/rss", "/rss.xml", "/index.xml", "/feed.xml")
                        val baseUrl = if (finalUrl.endsWith("/")) finalUrl.removeSuffix("/") else finalUrl
                        for (path in commonPaths) {
                            val testUrl = baseUrl + path
                            val testRequest = Request.Builder().url(testUrl).build()
                            try {
                                client.newCall(testRequest).execute().use { testResponse ->
                                    if (testResponse.isSuccessful) {
                                        val testBody = testResponse.body?.string() ?: ""
                                        if (!isHtml(testBody) && isXml(testBody)) {
                                            discoveredSources.add(FeedSource("Found: $path", testUrl))
                                        }
                                    }
                                }
                            } catch (e: Exception) {}
                        }
                    }
                } else if (isXml(body)) {
                    discoveredSources.add(FeedSource("Direct Feed", finalUrl))
                }
            }
        } catch (e: Exception) { }
        discoveredSources.distinctBy { it.url }
    }

    suspend fun fetchFeedItems(url: String): Pair<String, List<FeedItem>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return@withContext "" to emptyList()
                parseRss(body, url)
            }
        } catch (e: Exception) {
            "" to emptyList()
        }
    }

    private fun isXml(content: String): Boolean {
        val trimmed = content.trim()
        return trimmed.startsWith("<?xml", ignoreCase = true) || 
               trimmed.startsWith("<rss", ignoreCase = true) ||
               trimmed.startsWith("<feed", ignoreCase = true)
    }

    private fun normalizeUrl(url: String): String {
        var n = url.trim()
        if (!n.startsWith("http://") && !n.startsWith("https://")) {
            n = "https://$n"
        }
        return n
    }

    private fun isHtml(content: String): Boolean {
        val trimmed = content.trim()
        return trimmed.startsWith("<html", ignoreCase = true) || 
               trimmed.startsWith("<!DOCTYPE html", ignoreCase = true) ||
               trimmed.contains("<head", ignoreCase = true)
    }

    private fun parseRss(xml: String, originalUrl: String): Pair<String, List<FeedItem>> {
        val items = mutableListOf<FeedItem>()
        try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))

            var eventType = parser.eventType
            var feedTitle = ""
            var currentTitle = ""
            var currentSummary = ""
            var currentDate = ""
            var currentLink = ""
            var insideItem = false

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val tagName = parser.name
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (tagName == "item" || tagName == "entry") {
                            insideItem = true
                        } else if (tagName == "title" && !insideItem) {
                            feedTitle = parser.nextText()
                        } else if (insideItem) {
                            when (tagName) {
                                "title" -> currentTitle = parser.nextText()
                                "link" -> {
                                    val href = parser.getAttributeValue(null, "href")
                                    currentLink = if (!href.isNullOrBlank()) href else parser.nextText()
                                }
                                "description", "summary", "content" -> {
                                    currentSummary = cleanHtml(parser.nextText())
                                }
                                "pubDate", "published", "updated" -> currentDate = parser.nextText()
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (tagName == "item" || tagName == "entry") {
                            if (currentTitle.isNotBlank()) {
                                items.add(FeedItem(currentTitle, currentSummary, currentDate, originalUrl, currentLink))
                            }
                            currentTitle = ""
                            currentSummary = ""
                            currentDate = ""
                            currentLink = ""
                            insideItem = false
                        }
                    }
                }
                eventType = parser.next()
            }
            return (if (feedTitle.isBlank()) originalUrl else feedTitle) to items
        } catch (e: Exception) {
            return "" to emptyList()
        }
    }

    suspend fun fetchFullContent(url: String): String = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext ""
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext ""
                val html = response.body?.string() ?: return@withContext ""
                
                // Use Readability4J (like Feeder) to extract the main article content
                val readability = Readability4JExtended(url, html)
                val article = readability.parse()
                val articleHtml = article.content ?: html
                
                val doc = Jsoup.parse(articleHtml)
                
                // Remove unwanted interactive bits and noise
                doc.select("a, script, style, iframe, ads, nav, footer, header").remove()
                
                // Preserve line breaks and structure
                doc.select("p, br, h1, h2, h3, h4, h5, h6, div").append("\\n")
                
                Jsoup.parse(doc.html()).text().replace("\\n", "\n\n").trim()
            }
        } catch (e: Exception) {
            ""
        }
    }

    suspend fun translate(text: String, from: String, to: String): String = withContext(Dispatchers.IO) {
        if (text.isBlank() || from == to) return@withContext text
        try {
            val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=$from&tl=$to&dt=t&q=${java.net.URLEncoder.encode(text, "UTF-8")}"
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext text
                val body = response.body?.string() ?: return@withContext text
                val jsonArray = org.json.JSONArray(body)
                val sentences = jsonArray.getJSONArray(0)
                val result = StringBuilder()
                for (i in 0 until sentences.length()) {
                    result.append(sentences.getJSONArray(i).getString(0))
                }
                result.toString()
            }
        } catch (e: Exception) {
            text
        }
    }

    private fun cleanHtml(html: String): String {
        val doc = Jsoup.parse(html)
        doc.select("p, br").append("\\n")
        return Jsoup.parse(doc.html()).text().replace("\\n", "\n\n").trim()
    }
}
