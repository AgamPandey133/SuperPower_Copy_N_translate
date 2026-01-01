package com.example.superpower.util

object LanguageData {
    val languages = mapOf(
        "English" to "en",
        "Hindi" to "hi",
        "Spanish" to "es",
        "French" to "fr",
        "German" to "de",
        "Italian" to "it",
        "Japanese" to "ja",
        "Korean" to "ko",
        "Russian" to "ru",
        "Chinese" to "zh",
        "Arabic" to "ar",
        "Portuguese" to "pt",
        "Urdu" to "ur"
    )
    
    val languageNames = languages.keys.toList()
    val languageCodes = languages.values.toList()
    
    fun getCode(name: String): String = languages[name] ?: "en"
    fun getName(code: String): String = languages.entries.find { it.value == code }?.key ?: "English"
}
