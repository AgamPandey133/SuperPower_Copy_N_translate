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

    private lateinit var btnAction: Button
    private lateinit var tvStatus: TextView

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

        btnAction = findViewById(R.id.btn_action)
        tvStatus = findViewById(R.id.tv_status)

        btnAction.setOnClickListener {
            handleAction()
        }
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    private fun handleAction() {
        if (!PermissionUtil.hasOverlayPermission(this)) {
            PermissionUtil.requestOverlayPermission(this)
            return
        }

        // If service assume running? We don't track state well here yet.
        // Just always try to start for MVP
        requestMediaProjection()
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
        finish() // Close activity, let the floating head take over
    }

    private fun updateUI() {
        if (!PermissionUtil.hasOverlayPermission(this)) {
            btnAction.text = getString(R.string.grant_permission)
            tvStatus.text = getString(R.string.permission_required_desc)
        } else {
            btnAction.text = getString(R.string.start_service)
            tvStatus.text = "Permissions granted. Ready to activate."
        }
    }
}
