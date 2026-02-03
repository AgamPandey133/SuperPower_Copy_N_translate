package com.agampandey.superpower.core

import com.google.mlkit.common.model.DownloadConditions

import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext


class TranslationManager(private val context: android.content.Context) {

    private val languageIdentifier = com.google.mlkit.nl.languageid.LanguageIdentification.getClient()

    suspend fun downloadLanguageModel(targetLangCode: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val userTargetLang = TranslateLanguage.fromLanguageTag(targetLangCode) ?: return@withContext false
                val options = TranslatorOptions.Builder()
                    .setSourceLanguage(TranslateLanguage.ENGLISH) // Default source usually English for UI, or we download logic
                    .setTargetLanguage(userTargetLang)
                    .build()
                
                val translator = com.google.mlkit.nl.translate.Translation.getClient(options)
                val conditions = DownloadConditions.Builder().build()
                
                // Just trigger download
                translator.downloadModelIfNeeded(conditions).await()
                translator.close()
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

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
                    val conditions = DownloadConditions.Builder().build()

                    // Check if model is already downloaded
                    var isDownloaded = false
                    try {
                        isDownloaded = com.google.mlkit.common.model.RemoteModelManager.getInstance()
                            .isModelDownloaded(com.google.mlkit.nl.translate.TranslateRemoteModel.Builder(targetLang).build())
                            .await() == true
                    } catch (e: Exception) {
                        // If check fails, assume not downloaded
                        isDownloaded = false 
                    }
                    
                    // IF NOT DOWNLOADED: Try Online First
                    if (!isDownloaded) {
                         // 1. Trigger Background Download (Fire and Forget) via RemoteModelManager
                         // This ensures it continues even if we close the local translator
                         val modelToDl = com.google.mlkit.nl.translate.TranslateRemoteModel.Builder(targetLang).build()
                         com.google.mlkit.common.model.RemoteModelManager.getInstance()
                            .download(modelToDl, conditions)
                         
                         // 2. Try Online Fallback
                         // sourceLang is already a String (the code) or null
                         val sourceCode = sourceLang ?: "en"
                         val targetCode = targetLangCode
                         
                         val onlineResult = NetworkTranslator.translateOnline(text, sourceCode, targetCode)
                         if (onlineResult != null) {
                             translator.close()
                             return@withContext onlineResult + "\n\n[Online Result. Downloading offline model...]"
                         }
                         // If Online Fails, we fall through to blocking download below
                    }

                    // Blocking Download (Standard Path)
                    kotlinx.coroutines.withTimeout(60_000) { 
                        translator.downloadModelIfNeeded(conditions).await()
                    }

                    // Translate Offline
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
                     "Downloading model... (Slow Network)\nPlease wait or check connection."
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
