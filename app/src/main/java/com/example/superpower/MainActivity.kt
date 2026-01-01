package com.example.superpower

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.superpower.service.FloatingService
import com.example.superpower.util.PermissionUtil

class MainActivity : AppCompatActivity() {

    private lateinit var statusTv: TextView
    private lateinit var languageSpinner: android.widget.Spinner
    private lateinit var btnAction: Button
    private lateinit var rvHistory: androidx.recyclerview.widget.RecyclerView
    private lateinit var historyAdapter: com.example.superpower.ui.HistoryAdapter
    private lateinit var historyManager: com.example.superpower.util.HistoryManager

    private val languages = mapOf(
        "Hindi" to "hi",
        "English" to "en",
        "Spanish" to "es",
        "French" to "fr",
        "German" to "de",
        "Italian" to "it",
        "Japanese" to "ja",
        "Korean" to "ko",
        "Russian" to "ru",
        "Chinese" to "zh"
    )

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            startFloatingService(result.resultCode, result.data!!)
        } else {
            Toast.makeText(this, "Screen capture permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        historyManager = com.example.superpower.util.HistoryManager(this)

        statusTv = findViewById(R.id.tv_status)
        languageSpinner = findViewById(R.id.spinner_languages)
        btnAction = findViewById(R.id.btn_action)
        rvHistory = findViewById(R.id.rv_history)
        
        setupHistory()

        setupLanguageSpinner()

        btnAction.setOnClickListener {
             if (checkAndRequestPermissions()) {
                 showDisclosureDialog()
             }
        }
        
        findViewById<android.view.View>(R.id.btn_manage_models).setOnClickListener {
            startActivity(Intent(this, ModelManagerActivity::class.java))
        }

        findViewById<android.view.View>(R.id.btn_privacy).setOnClickListener {
            val intent = Intent(this, com.example.superpower.ui.PolicyActivity::class.java)
            startActivity(intent)
        }
    }
    
    private fun showDisclosureDialog() {
        android.app.AlertDialog.Builder(this)
            .setTitle("Screen Capture Disclosure")
            .setMessage("This app uses the Screen Capture API (Media Projection) to detect and translate text on your screen.\n\nThe service is only active when you tap 'Start SuperPower' and the floating button is visible.\n\nData is processed locally or transiently for translation and is NOT stored permanently or shared for any other purpose.")
            .setPositiveButton("Proceed") { _, _ ->
                requestMediaProjection()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun setupHistory() {
        historyAdapter = com.example.superpower.ui.HistoryAdapter { item ->
            historyManager.deleteItem(item)
            loadHistory()
            Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show()
        }
        rvHistory.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        rvHistory.adapter = historyAdapter
        loadHistory()
    }
    
    private fun loadHistory() {
        val list = historyManager.getHistory()
        historyAdapter.updateData(list)
    }

    override fun onResume() {
        super.onResume()
        updateUI()
        loadHistory()
    }

    private fun setupLanguageSpinner() {
        val adapter = android.widget.ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            languages.keys.toList()
        )
        languageSpinner.adapter = adapter
        
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val savedCode = prefs.getString("target_lang", "hi") // Default Hindi
        
        val keys = languages.keys.toList()
        for (i in keys.indices) {
            if (languages[keys[i]] == savedCode) {
                languageSpinner.setSelection(i)
                break
            }
        }
        
        languageSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
             override fun onItemSelected(parent: android.widget.AdapterView<*>, view: android.view.View?, position: Int, id: Long) {
                 val selectedName = keys[position]
                 val code = languages[selectedName]
                 prefs.edit().putString("target_lang", code).apply()
             }
             override fun onNothingSelected(parent: android.widget.AdapterView<*>) {}
        }
    }

    private fun checkAndRequestPermissions(): Boolean {
        if (!PermissionUtil.hasOverlayPermission(this)) {
            PermissionUtil.requestOverlayPermission(this)
            return false
        }
        return true
    }

    private fun requestMediaProjection() {
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjectionLauncher.launch(manager.createScreenCaptureIntent())
    }

    private fun startFloatingService(resultCode: Int, data: Intent) {
        val intent = Intent(this, FloatingService::class.java).apply {
            putExtra(FloatingService.EXTRA_RESULT_CODE, resultCode)
            putExtra(FloatingService.EXTRA_RESULT_DATA, data)
        }
        startForegroundService(intent)
        finish() 
    }

    private fun updateUI() {
        if (!PermissionUtil.hasOverlayPermission(this)) {
            btnAction.text = getString(R.string.grant_permission)
            statusTv.text = getString(R.string.permission_required_desc)
        } else {
            btnAction.text = getString(R.string.start_service)
            statusTv.text = "Permissions granted. Ready to activate."
        }
    }
}
