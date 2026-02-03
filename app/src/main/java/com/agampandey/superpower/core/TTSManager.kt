package com.agampandey.superpower.core

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class TTSManager(context: Context) {

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isInitialized = true
            }
        }
    }

    fun speak(text: String, languageCode: String, onStart: (() -> Unit)? = null, onDone: (() -> Unit)? = null, onError: ((String) -> Unit)? = null) {
        if (!isInitialized || tts == null) {
            onError?.invoke("TTS not initialized")
            return
        }

        val locale = Locale.forLanguageTag(languageCode)
        val result = tts?.setLanguage(locale)

        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
             tts?.setLanguage(Locale.ENGLISH)
        }
        
        val utteranceId = System.currentTimeMillis().toString()
        
        tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                onStart?.invoke()
            }

            override fun onDone(utteranceId: String?) {
                onDone?.invoke()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                onError?.invoke("Generic Error")
            }
            
            override fun onError(utteranceId: String?, errorCode: Int) {
                onError?.invoke("Error Code: $errorCode")
            }
        })

        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}
