package com.example.superpower.core

import com.google.mlkit.common.model.DownloadConditions

import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await


class TranslationManager {

    // For MVP, we map everything to English
    private val options = TranslatorOptions.Builder()
        .setSourceLanguage(TranslateLanguage.HINDI) // Defaulting to Hindi -> English for demo as per common use case, or auto-detect in future
        .setTargetLanguage(TranslateLanguage.ENGLISH)
        .build()
        
    // Ideally we should use Identification first, but to keep it simple we assume a specific pair or just Generic
    // Integrating generic "Identification" is another ML Kit dependency.
    // Let's stick to a simple Model for now (Latin languages usually don't need translation if they are English).
    // Let's try to support dynamic source if possible, but ML Kit Translate requires downloading models.
    
    // Changing strategy: Since we don't know the source, and downloading all models is heavy.
    // We will just implement the structure.
    
    private val translator = com.google.mlkit.nl.translate.Translation.getClient(options)

    suspend fun translate(text: String): String {
        return try {
            val conditions = DownloadConditions.Builder()
                .requireWifi()
                .build()

            // Download model if needed
            translator.downloadModelIfNeeded(conditions).await()

            // Translate
            translator.translate(text).await()
        } catch (e: Exception) {
            e.printStackTrace()
            "Translation Error: ${e.message}"
        }
    }
    
    // Clean up when service destroys if needed (not strictly required for singleton usage)
    fun close() {
        translator.close()
    }
}
