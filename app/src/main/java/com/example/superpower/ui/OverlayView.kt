package com.example.superpower.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import com.google.mlkit.vision.text.Text
import android.content.ClipboardManager
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.TextView
import com.example.superpower.R

class OverlayView(context: Context) : View(context) {

    private val paint = Paint().apply {
        color = context.getColor(R.color.highlight_color)
        style = Paint.Style.FILL
    }
    
    private val strokePaint = Paint().apply {
        color = context.getColor(R.color.highlight_stroke)
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private var textBlocks: List<Text.TextBlock> = emptyList()
    private var onTextSelected: ((String) -> Unit)? = null
    
    fun setDetectedText(blocks: List<Text.TextBlock>) {
        this.textBlocks = blocks
        invalidate()
        if (blocks.isEmpty()) {
            Toast.makeText(context, "No text found", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(context.getColor(R.color.overlay_background)) // Dim background

        // Draw Lines instead of Blocks for better granularity
        for (block in textBlocks) {
            for (line in block.lines) {
                 line.boundingBox?.let { rect ->
                    canvas.drawRect(rect, paint)
                    canvas.drawRect(rect, strokePaint)
                 }
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val x = event.x.toInt()
            val y = event.y.toInt()

            // Find the closest text line, but select its Parent Block
            var bestBlock: com.google.mlkit.vision.text.Text.TextBlock? = null
            var bestRect: Rect? = null
            var minDistance = Float.MAX_VALUE
            val touchPadding = 30 

            for (block in textBlocks) {
                for (line in block.lines) {
                    val rect = line.boundingBox ?: continue
                    
                    if (x >= rect.left - touchPadding && x <= rect.right + touchPadding &&
                        y >= rect.top - touchPadding && y <= rect.bottom + touchPadding) {
                        
                        val centerX = rect.exactCenterX()
                        val centerY = rect.exactCenterY()
                        val dist = kotlin.math.hypot(x - centerX, y - centerY)
                        
                        if (dist < minDistance) {
                            minDistance = dist
                            bestBlock = block // Select the whole block
                            bestRect = block.boundingBox // Highlight the whole block
                        }
                    }
                }
            }

            if (bestBlock != null && bestRect != null) {
                showActionPopup(bestRect, bestBlock.text)
                performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
            } else {
                onCloseRequested?.invoke()
            }
        }
        return true
    }

    var onCloseRequested: (() -> Unit)? = null

                
    private var activePopup: PopupWindow? = null

    private fun showActionPopup(rect: Rect, text: String) {
        // Dismiss old popup if any
        activePopup?.dismiss()

        // Inflate the popup layout
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.layout_action_popup, null)

        val btnCopy = view.findViewById<TextView>(R.id.btn_copy)
        val btnTranslate = view.findViewById<TextView>(R.id.btn_translate)
        val btnShare = view.findViewById<TextView>(R.id.btn_share)

        btnCopy.setOnClickListener {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = android.content.ClipData.newPlainText("Copied Text", text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "Copied to Clipboard", Toast.LENGTH_SHORT).show()
            activePopup?.dismiss()
            onCloseRequested?.invoke()
        }

        btnTranslate.setOnClickListener {
            // UI State: Loading
            btnCopy.visibility = View.GONE
            btnTranslate.visibility = View.GONE
            btnShare.visibility = View.GONE
            
            // Show Result Container
            val layoutResult = view.findViewById<View>(R.id.layout_result)
            layoutResult.visibility = View.VISIBLE
            
            val pbLoading = view.findViewById<View>(R.id.pb_translate_loading)
            val tvResult = view.findViewById<TextView>(R.id.tv_result)
            val spinnerLang = view.findViewById<android.widget.Spinner>(R.id.spinner_result_lang)
            
            pbLoading.visibility = View.VISIBLE
            tvResult.visibility = View.GONE

            // Setup Spinner
            val languageNames = com.example.superpower.util.LanguageData.languageNames
            val adapter = android.widget.ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, languageNames)
            spinnerLang.adapter = adapter
            
            // Get Default Pref
            val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val defaultCode = prefs.getString("target_lang", "hi")
            val defaultName = com.example.superpower.util.LanguageData.getName(defaultCode ?: "hi")
            spinnerLang.setSelection(languageNames.indexOf(defaultName))
            
            // Define Translation Function
            fun performTranslation(targetCode: String) {
                pbLoading.visibility = View.VISIBLE
                tvResult.visibility = View.GONE
                
                onTranslateRequested?.invoke(text, targetCode) { result ->
                     pbLoading.visibility = View.GONE
                     tvResult.text = result
                     tvResult.visibility = View.VISIBLE
                     tvResult.movementMethod = android.text.method.ScrollingMovementMethod()
                     
                     tvResult.setOnClickListener {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = android.content.ClipData.newPlainText("Translated Text", result)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Translation Copied", Toast.LENGTH_SHORT).show()
                        activePopup?.dismiss()
                        onCloseRequested?.invoke()
                    }
                    
                    val btnSpeak = view.findViewById<android.widget.ImageView>(R.id.btn_tts)
                    val pbSpeak = view.findViewById<android.view.View>(R.id.pb_tts_loading)
                    
                    btnSpeak.setOnClickListener {
                        pbSpeak.visibility = View.VISIBLE
                        btnSpeak.visibility = View.INVISIBLE // Keep layout space but invisible
                        
                        onSpeakRequested?.invoke(result, targetCode, 
                            { // onStart
                                pbSpeak.visibility = View.GONE
                                btnSpeak.visibility = View.VISIBLE
                            },
                            { // onDone
                                // Do nothing or show done state
                            },
                            { error -> // onError
                                pbSpeak.visibility = View.GONE
                                btnSpeak.visibility = View.VISIBLE
                                Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
            
            // Initial Translation
            performTranslation(defaultCode ?: "hi")
            
            // Spinner Listener
            spinnerLang.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                var isFirst = true
                override fun onItemSelected(parent: android.widget.AdapterView<*>, view: View?, position: Int, id: Long) {
                    if (isFirst) {
                        isFirst = false // Avoid double trigger on setSelection
                        return
                    }
                    val selectedName = languageNames[position]
                    val selectedCode = com.example.superpower.util.LanguageData.getCode(selectedName)
                    performTranslation(selectedCode)
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>) {}
            }
        }

        btnShare.setOnClickListener {
            val shareIntent = android.content.Intent().apply {
                action = android.content.Intent.ACTION_SEND
                putExtra(android.content.Intent.EXTRA_TEXT, text)
                type = "text/plain"
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Text").apply {
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            })
            activePopup?.dismiss()
            onCloseRequested?.invoke()
        }

        // Create the PopupWindow
        val popupWindow = PopupWindow(
            view,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true // Focusable
        )
        popupWindow.elevation = 20f
        popupWindow.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        
        // Calculate position (centered above the rect)
        // Ensure it doesn't go off-screen
        view.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val popupWidth = view.measuredWidth
        val popupHeight = view.measuredHeight

        val xPos = rect.centerX() - (popupWidth / 2)
        val yPos = rect.top - popupHeight - 20 // 20px padding above

        try {
            popupWindow.showAtLocation(this, android.view.Gravity.NO_GRAVITY, xPos, yPos)
            activePopup = popupWindow
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    var onTranslateRequested: ((String, String, (String) -> Unit) -> Unit)? = null
    var onSpeakRequested: ((String, String, () -> Unit, () -> Unit, (String) -> Unit) -> Unit)? = null

}
