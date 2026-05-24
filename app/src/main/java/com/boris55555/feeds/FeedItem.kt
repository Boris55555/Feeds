package com.boris55555.feeds

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class FeedItem(
    val title: String,
    val summary: String,
    val date: String,
    val sourceUrl: String,
    val link: String = "",
    val fullContent: String? = null
) {
    val parsedDate: ZonedDateTime? by lazy {
        if (date.isBlank()) return@lazy null
        
        val formatters = listOf(
            DateTimeFormatter.RFC_1123_DATE_TIME,
            DateTimeFormatter.ISO_DATE_TIME,
            DateTimeFormatter.ISO_INSTANT,
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.ENGLISH)
        )
        
        for (formatter in formatters) {
            try {
                return@lazy ZonedDateTime.parse(date, formatter)
            } catch (e: Exception) {
                continue
            }
        }
        null
    }
}
