package com.example.superpower.data

data class HistoryItem(
    val originalText: String,
    val translatedText: String,
    val languageCode: String,
    val timestamp: Long
)
