package com.agampandey.superpower

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.agampandey.superpower.data.LanguageModelItem
import com.agampandey.superpower.ui.ModelAdapter
import com.agampandey.superpower.util.LanguageData
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateRemoteModel

class ModelManagerActivity : AppCompatActivity() {

    private lateinit var rvModels: RecyclerView
    private lateinit var adapter: ModelAdapter
    private val modelManager = RemoteModelManager.getInstance()
    private var modelItems = mutableListOf<LanguageModelItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_model_manager)

        findViewById<android.view.View>(R.id.btn_back).setOnClickListener {
            finish()
        }

        findViewById<android.widget.Button>(R.id.btn_download_all).setOnClickListener {
            Toast.makeText(this, "Starting bulk download...", Toast.LENGTH_SHORT).show()
            for (item in modelItems) {
                if (!item.isDownloaded && !item.isDownloading) {
                    downloadModel(item)
                }
            }
        }

        rvModels = findViewById(R.id.rv_models)
        rvModels.layoutManager = LinearLayoutManager(this)
        
        adapter = ModelAdapter { item ->
            if (item.isDownloaded) {
                deleteModel(item)
            } else {
                downloadModel(item)
            }
        }
        rvModels.adapter = adapter

        loadModels()
    }

    private fun loadModels() {
        // Prepare list of all supported languages
        modelItems.clear()
        val allLangs = LanguageData.languageNames.sorted()
        
        // Check status for each
        modelManager.getDownloadedModels(TranslateRemoteModel::class.java)
            .addOnSuccessListener { downloadedModels ->
                val downloadedCodes = downloadedModels.map { it.language }.toSet()
                
                for (name in allLangs) {
                    val code = LanguageData.getCode(name)
                    // Skip if code is invalid or "auto"
                    if (code == "en") continue // English is usually built-in or base, but let's list it anyway if needed. 
                    // Actually ML Kit usually requires downloading even English. Let's include everything except maybe 'auto'.
                    
                    val isDownloaded = downloadedCodes.contains(code)
                    modelItems.add(LanguageModelItem(code, name, isDownloaded))
                }
                
                // Sort: Downloaded first, then alphabetical
                modelItems.sortByDescending { it.isDownloaded }
                adapter.updateData(modelItems.toList())
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to check models", Toast.LENGTH_SHORT).show()
            }
    }

    private fun downloadModel(item: LanguageModelItem) {
        val model = TranslateRemoteModel.Builder(item.languageCode).build()
        val conditions = DownloadConditions.Builder()
            .requireWifi()
            .build()

        // Update UI locally immediately
        item.isDownloading = true
        adapter.notifyDataSetChanged()

        modelManager.download(model, conditions)
            .addOnSuccessListener {
                Toast.makeText(this, "Downloaded ${item.languageName}", Toast.LENGTH_SHORT).show()
                item.isDownloading = false
                item.isDownloaded = true
                loadModels() // Refresh full list order
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
                item.isDownloading = false
                adapter.notifyDataSetChanged()
            }
    }

    private fun deleteModel(item: LanguageModelItem) {
        val model = TranslateRemoteModel.Builder(item.languageCode).build()
        modelManager.deleteDownloadedModel(model)
            .addOnSuccessListener {
                Toast.makeText(this, "Deleted ${item.languageName}", Toast.LENGTH_SHORT).show()
                item.isDownloaded = false
                loadModels() // Refresh
            }
            .addOnFailureListener {
                Toast.makeText(this, "Delete failed", Toast.LENGTH_SHORT).show()
            }
    }
}
