package com.xuleo.xmplus.md3.browse

import android.content.Context
import com.xuleo.xmplus.md3.core.model.SearchHistory
import org.json.JSONArray

/** The search history, kept only on this device. */
class SearchHistoryStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("search-history", Context.MODE_PRIVATE)

    fun read(): List<String> {
        val stored = preferences.getString(Key, null) ?: return emptyList()
        return try {
            val array = JSONArray(stored)
            SearchHistory.sanitize((0 until array.length()).mapNotNull { array.opt(it) as? String })
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun write(history: List<String>) {
        val editor = preferences.edit()
        if (history.isEmpty()) editor.remove(Key) else editor.putString(Key, JSONArray(history).toString())
        editor.apply()
    }

    private companion object {
        const val Key = "keywords"
    }
}
