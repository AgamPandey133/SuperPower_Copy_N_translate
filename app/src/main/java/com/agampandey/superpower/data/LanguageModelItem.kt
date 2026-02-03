package com.agampandey.superpower.data

data class LanguageModelItem(
    val languageCode: String,
    val languageName: String,
    var isDownloaded: Boolean,
    var isDownloading: Boolean = false
)
