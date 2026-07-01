package com.boris55555.feeds

import java.time.LocalDateTime
import java.time.ZoneId
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
        
        val patterns = listOf(
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "EEE, dd MMM yyyy HH:mm:ss z",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd HH:mm:ss",
            "EEE, dd MMM yyyy",
            "dd MMM yyyy"
        )
        
        val locales = listOf(Locale.ENGLISH, Locale.forLanguageTag("it"), Locale.forLanguageTag("fi"), Locale.FRENCH, Locale.GERMAN)
        
        // 1. Try standard pre-defined formatters (usually English/ISO)
        val standardFormatters = listOf(
            DateTimeFormatter.RFC_1123_DATE_TIME,
            DateTimeFormatter.ISO_DATE_TIME,
            DateTimeFormatter.ISO_INSTANT
        )
        
        for (formatter in standardFormatters) {
            try { return@lazy ZonedDateTime.parse(date, formatter) } catch (e: Exception) {}
        }

        // 2. Try custom patterns with multiple locales
        for (pattern in patterns) {
            for (locale in locales) {
                try {
                    val formatter = DateTimeFormatter.ofPattern(pattern, locale)
                    if (pattern.contains("Z") || pattern.contains("z") || pattern.contains("X")) {
                        return@lazy ZonedDateTime.parse(date, formatter)
                    } else {
                        // Fallback for dates without time/zone
                        try {
                            return@lazy LocalDateTime.parse(date, formatter).atZone(ZoneId.systemDefault())
                        } catch (e: Exception) {
                            return@lazy java.time.LocalDate.parse(date, formatter).atStartOfDay(ZoneId.systemDefault())
                        }
                    }
                } catch (e: Exception) {
                    continue
                }
            }
        }
        null
    }
}
