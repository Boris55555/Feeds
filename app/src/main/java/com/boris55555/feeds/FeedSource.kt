package com.boris55555.feeds

data class FeedSource(
    val title: String,
    val url: String,
    val sourceLang: String = "auto",
    val targetLang: String = "fi",
    val isTranslationEnabled: Boolean = false,
    val tags: List<String> = emptyList()
)
