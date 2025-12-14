package com.example.superpower.core

import com.google.mlkit.common.model.DownloadConditions

import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext


class TranslationManager(private val context: android.content.Context) {

    private val languageIdentifier = com.google.mlkit.nl.languageid.LanguageIdentification.getClient()

    suspend fun translate(text: String, targetLangCode: String): String {
        return withContext(Dispatchers.IO) {
            try {
                // 1. Use passed User Preference
                val userTargetLang = TranslateLanguage.fromLanguageTag(targetLangCode) ?: TranslateLanguage.HINDI

                // 2. Identify Source Language
                val languageCode = languageIdentifier.identifyLanguage(text).await()
                var sourceLang = TranslateLanguage.fromLanguageTag(languageCode)

                if (languageCode == "und" || sourceLang == null) {
                   sourceLang = TranslateLanguage.ENGLISH
                }
                
                // Smart Logic:
                // If the text IS the user's target language, translate it to English (Reverse lookup for convenience).
                // Otherwise, translate TO the user's target language.
                
                var targetLang = userTargetLang
                
                if (sourceLang == userTargetLang) {
                     targetLang = TranslateLanguage.ENGLISH
                }

                val options = TranslatorOptions.Builder()
                    .setSourceLanguage(sourceLang!!)
                    .setTargetLanguage(targetLang)
                    .build()

                val translator = com.google.mlkit.nl.translate.Translation.getClient(options)

                try {
                    val conditions = DownloadConditions.Builder()
                        // Removed requireWifi() to allow mobile data
                        .build()

                    // Download model if needed (with timeout)
                    kotlinx.coroutines.withTimeout(30_000) { 
                        translator.downloadModelIfNeeded(conditions).await()
                    }

                    // Translate
                    val result = translator.translate(text).await()
                    translator.close()
                    return@withContext result
                } catch (e: Exception) {
                    translator.close()
                    throw e
                }
            } catch (e: Exception) {
                e.printStackTrace()
                if (e is kotlinx.coroutines.TimeoutCancellationException) {
                     "Error: Model download timed out."
                } else {
                     "Error: ${e.message}"
                }
            }
        }
    }
    
    fun close() {
        // Nothing to close globally as we create per-request translators
    }
}
