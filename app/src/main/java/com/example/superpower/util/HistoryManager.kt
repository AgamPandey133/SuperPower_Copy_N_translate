package com.example.superpower.util

import android.content.Context
import com.example.superpower.data.HistoryItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class HistoryManager(private val context: Context) {

    private val gson = Gson()
    private val PREFS_NAME = "history_prefs"
    private val KEY_HISTORY = "history_list"

    fun saveTranslation(original: String, translated: String, langCode: String) {
        val list = getHistory().toMutableList()
        
        // Add new item to top
        val newItem = HistoryItem(original, translated, langCode, System.currentTimeMillis())
        list.add(0, newItem)
        
        // Limit to 50 items
        if (list.size > 50) {
            list.removeAt(list.size - 1)
        }
        
        saveList(list)
    }

    fun getHistory(): List<HistoryItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        
        val type = object : TypeToken<List<HistoryItem>>() {}.type
        return gson.fromJson(json, type)
    }
    
    fun deleteItem(item: HistoryItem) {
        val list = getHistory().toMutableList()
        list.remove(item)
        saveList(list)
    }

    fun clearHistory() {
         context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
             .edit().remove(KEY_HISTORY).apply()
    }

    private fun saveList(list: List<HistoryItem>) {
        val json = gson.toJson(list)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_HISTORY, json)
            .apply()
    }
}
