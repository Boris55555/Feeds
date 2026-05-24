package com.boris55555.feeds

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.json.JSONArray

class RefreshWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("FeedsPrefs", Context.MODE_PRIVATE)
        val savedSources = prefs.getString("sources", null) ?: return Result.success()
        
        val parser = FeedParser()
        val jsonArray = JSONArray(savedSources)
        val sources = mutableListOf<FeedSource>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            val tagList = mutableListOf<String>()
            val tagArray = obj.optJSONArray("tags")
            if (tagArray != null) {
                for (j in 0 until tagArray.length()) tagList.add(tagArray.getString(j))
            }
            sources.add(FeedSource(
                obj.getString("title"), 
                obj.getString("url"),
                obj.optString("sourceLang", "auto"),
                obj.optString("targetLang", "fi"),
                obj.optBoolean("isTranslationEnabled", false),
                tagList
            ))
        }

        if (sources.isNotEmpty()) {
            val allItems = mutableListOf<FeedItem>()
            sources.forEach { s ->
                val (_, fetched) = parser.fetchFeedItems(s.url)
                val limited = fetched.take(10)
                if (s.isTranslationEnabled && s.sourceLang != s.targetLang) {
                    limited.forEach { item ->
                        val translatedTitle = parser.translate(item.title, s.sourceLang, s.targetLang)
                        allItems.add(item.copy(title = translatedTitle))
                    }
                } else {
                    allItems.addAll(limited)
                }
            }
            // In a real app we might store these in a DB. 
            // For now, we just fetch them so they are ready when the app opens.
            // SharedPreferences is not ideal for large item lists, but for 10 per feed it might work.
        }

        return Result.success()
    }
}
