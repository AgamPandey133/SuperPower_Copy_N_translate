package com.agampandey.superpower.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object NetworkTranslator {
    
    // Using MyMemory API (Free Tier)
    // Usage: https://api.mymemory.translated.net/get?q=Hello World&langpair=en|it
    
    suspend fun translateOnline(text: String, sourceLang: String, targetLang: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                // Formatting Check: MyMemory expects "en|it" format
                val langPair = "$sourceLang|$targetLang"
                val encodedText = URLEncoder.encode(text, "UTF-8")
                
                val urlString = "https://api.mymemory.translated.net/get?q=$encodedText&langpair=$langPair"
                val url = URL(urlString)
                
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000 // 5 seconds timeout
                connection.readTimeout = 5000
                
                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(response)
                    
                    // Parse: responseData -> translatedText
                    val responseData = json.optJSONObject("responseData")
                    val translatedText = responseData?.optString("translatedText")
                    
                    if (!translatedText.isNullOrEmpty()) {
                        return@withContext translatedText
                    }
                }
                return@withContext null
            } catch (e: Exception) {
                e.printStackTrace()
                return@withContext null
            }
        }
    }
}
