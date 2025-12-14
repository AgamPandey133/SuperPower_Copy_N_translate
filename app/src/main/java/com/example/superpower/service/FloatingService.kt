package com.example.superpower.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.drawable.BitmapDrawable
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.example.superpower.MainActivity
import com.example.superpower.R
import com.example.superpower.core.CaptureManager
import com.example.superpower.core.OcrAnalyzer
import com.example.superpower.core.TranslationManager
import com.example.superpower.ui.OverlayView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FloatingService : Service() {

    companion object {
        const val CHANNEL_ID = "superpower_channel"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
    }

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private var overlayView: OverlayView? = null
    private lateinit var captureManager: CaptureManager
    private val ocrAnalyzer = OcrAnalyzer()
    private val translationManager = TranslationManager()
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var resultCode: Int = 0
    private var resultData: Intent? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        captureManager = CaptureManager(this)
        startForegroundService()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            val code = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
            val data = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
            
            if (code != 0 && data != null) {
                resultCode = code
                resultData = data
                setupMediaProjection()
                showFloatingHead()
            }
        }
        return START_NOT_STICKY
    }

    private fun setupMediaProjection() {
        val mpManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = mpManager.getMediaProjection(resultCode, resultData!!)
        captureManager.setMediaProjection(projection)
    }

    private fun startForegroundService() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.service_running_channel),
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.service_notification_title))
            .setContentText(getString(R.string.service_notification_desc))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
            
        // For Android 14+ we need to specify the type
        if (Build.VERSION.SDK_INT >= 34) {
             startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
             startForeground(1, notification)
        }
    }

    private fun showFloatingHead() {
        if (floatingView != null) return

        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_widget, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 100

        // Handle Dragging via the Root View to catch all touches
        floatingView?.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            
            // Long Press Logic
            private val handler = android.os.Handler(android.os.Looper.getMainLooper())
            private val longPressRunnable = Runnable {
                // Long press detected
                performHapticFeedback()
                Toast.makeText(this@FloatingService, "Closing Application...", Toast.LENGTH_SHORT).show()
                stopSelf() // Stops the service and removes view
            }
            private var isLongPress = false

            private fun performHapticFeedback() {
                 if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                     floatingView?.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
                 } else {
                     floatingView?.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                 }
            }

            @RequiresApi(Build.VERSION_CODES.P)
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        
                        isLongPress = false
                        handler.postDelayed(longPressRunnable, 800) // 800ms for long press
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        // Only move if significantly dragged to avoid jitter preventing clicks
                        val moveDiff = Math.hypot((event.rawX - initialTouchX).toDouble(), (event.rawY - initialTouchY).toDouble())
                        if (moveDiff > 10) {
                             handler.removeCallbacks(longPressRunnable) // Cancel long press if moved
                             
                             params.x = initialX + (event.rawX - initialTouchX).toInt()
                             params.y = initialY + (event.rawY - initialTouchY).toInt()
                             windowManager.updateViewLayout(floatingView, params)
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        handler.removeCallbacks(longPressRunnable) // Cancel long press
                        
                        val diff = Math.hypot((event.rawX - initialTouchX).toDouble(), (event.rawY - initialTouchY).toDouble())
                        
                        if (diff < 50) { 
                            performCapture()
                        }
                        return true
                    }
                }
                return false
            }
        })

        windowManager.addView(floatingView, params)
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun performCapture() {
        val progressBar = floatingView?.findViewById<View>(R.id.pb_loading)
        progressBar?.visibility = View.VISIBLE
        // Don't fully hide the icon, just dim it or keep it visible so we don't lose touch focus if user taps repeatedly
        // floatingView?.visibility = View.GONE 

        serviceScope.launch {
            try {
                // 2. Capture
                val bitmap = captureManager.captureScreen()
                
                if (bitmap != null) {
                    // 3. OCR
                    val text = ocrAnalyzer.analyze(bitmap)
                    
                    // 4. Show Overlay
                    withContext(Dispatchers.Main) {
                         progressBar?.visibility = View.GONE
                         showOverlay(bitmap, text.textBlocks)
                         // floatingView?.visibility = View.VISIBLE
                    }
                } else {
                    withContext(Dispatchers.Main) {
                         progressBar?.visibility = View.GONE
                         Toast.makeText(this@FloatingService, "Capture Timeout/Failed. Try again.", Toast.LENGTH_SHORT).show()
                         // floatingView?.visibility = View.VISIBLE
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                 withContext(Dispatchers.Main) {
                     progressBar?.visibility = View.GONE
                     Toast.makeText(this@FloatingService, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                     // floatingView?.visibility = View.VISIBLE
                 }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun showOverlay(bitmap: Bitmap, textBlocks: List<com.google.mlkit.vision.text.Text.TextBlock>) {
        overlayView = OverlayView(this).apply {
            // Set the background to the captured screenshot to simulate "Freezing" the screen
            background = BitmapDrawable(resources, bitmap)
            setDetectedText(textBlocks)
            onCloseRequested = {
                removeOverlay()
            }
            onTranslateRequested = { text ->
                 serviceScope.launch {
                     val translated = translationManager.translate(text)
                     Toast.makeText(this@FloatingService, translated, Toast.LENGTH_LONG).show()
                 }
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                 WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, // Allow touches
            PixelFormat.TRANSLUCENT
        )
        // Ensure it covers full screen including status bar
        params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES

        windowManager.addView(overlayView, params)
    }

    private fun removeOverlay() {
        if (overlayView != null) {
            windowManager.removeView(overlayView)
            overlayView = null
        }
        floatingView?.visibility = View.VISIBLE
    }

    override fun onDestroy() {
        super.onDestroy()
        if (floatingView != null) windowManager.removeView(floatingView)
        if (overlayView != null) windowManager.removeView(overlayView)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
