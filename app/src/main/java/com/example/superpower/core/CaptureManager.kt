package com.example.superpower.core

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.util.DisplayMetrics
import android.view.WindowManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CaptureManager(private val context: Context) {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var windowManager: WindowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    
    // Coordination
    private var pendingCapture: CompletableDeferred<Bitmap?>? = null

    fun setMediaProjection(projection: MediaProjection) {
        // Clean up old if any
        stopSession()
        
        this.mediaProjection = projection
        this.mediaProjection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                super.onStop()
                android.util.Log.e("SuperPower", "MediaProjection Stopped by System!")
                stopSession()
                mediaProjection = null
            }
        }, null)
        
        // Start persistent session immediately
        startSession()
    }
    
    private fun startSession() {
        if (mediaProjection == null) return
        
        try {
            val metrics = DisplayMetrics()
            windowManager.defaultDisplay.getRealMetrics(metrics)
            val density = metrics.densityDpi
            val width = metrics.widthPixels
            val height = metrics.heightPixels

            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            
            imageReader?.setOnImageAvailableListener({ reader ->
                try {
                    val image = reader.acquireLatestImage()
                    if (image != null) {
                        if (pendingCapture != null && pendingCapture!!.isActive) {
                            // We are waiting for this frame!
                            val planes = image.planes
                            val buffer = planes[0].buffer
                            val pixelStride = planes[0].pixelStride
                            val rowStride = planes[0].rowStride
                            val rowPadding = rowStride - pixelStride * width

                            val bitmap = Bitmap.createBitmap(
                                width + rowPadding / pixelStride,
                                height,
                                Bitmap.Config.ARGB_8888
                            )
                            bitmap.copyPixelsFromBuffer(buffer)
                            
                            // Remove padding
                            val cropped = Bitmap.createBitmap(bitmap, 0, 0, width, height)
                            pendingCapture?.complete(cropped)
                            pendingCapture = null // Fulfilled
                        }
                        
                        // Always close to keep buffer free
                        image.close()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    pendingCapture?.completeExceptionally(e)
                }
            }, android.os.Handler(android.os.Looper.getMainLooper()))

            virtualDisplay = mediaProjection!!.createVirtualDisplay(
                "ScreenCapture",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader!!.surface,
                null,
                null
            )
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopSession() {
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.setOnImageAvailableListener(null, null)
        imageReader?.close()
        imageReader = null
    }

    suspend fun captureScreen(): Bitmap? = withContext(Dispatchers.IO) {
        if (mediaProjection == null) {
            withContext(Dispatchers.Main) {
                android.widget.Toast.makeText(context, "Permission lost! Please restart service.", android.widget.Toast.LENGTH_LONG).show()
            }
            return@withContext null
        }
        
        // Re-start session if it died?
        if (virtualDisplay == null) {
            withContext(Dispatchers.Main) { startSession() }
            delay(100)
        }

        // Wait for next frame
        val deferred = CompletableDeferred<Bitmap?>()
        pendingCapture = deferred
        
        return@withContext kotlinx.coroutines.withTimeoutOrNull(1500) {
            deferred.await()
        }
    }
}
