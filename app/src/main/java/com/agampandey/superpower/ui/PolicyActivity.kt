package com.agampandey.superpower.ui

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.agampandey.superpower.R
import java.io.BufferedReader
import java.io.InputStreamReader

class PolicyActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_policy)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar_policy)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        val type = intent.getStringExtra("TYPE") ?: "privacy"
        val title = if (type == "terms") "Terms & Conditions" else "Privacy Policy"
        val fileName = if (type == "terms") "terms_conditions.txt" else "privacy_policy.txt"
        
        supportActionBar?.title = title

        val contentTv = findViewById<TextView>(R.id.tv_policy_content)
        contentTv.text = readAssetFile(fileName)
    }

    private fun readAssetFile(fileName: String): String {
        return try {
            val inputStream = assets.open(fileName)
            val reader = BufferedReader(InputStreamReader(inputStream))
            val sb = StringBuilder()
            var line = reader.readLine()
            while (line != null) {
                sb.append(line).append("\n")
                line = reader.readLine()
            }
            reader.close()
            sb.toString()
        } catch (e: Exception) {
            "Error loading document."
        }
    }
}
