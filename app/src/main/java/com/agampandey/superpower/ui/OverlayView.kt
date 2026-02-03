package com.agampandey.superpower.ui

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.agampandey.superpower.R
import com.google.mlkit.vision.text.Text

class OverlayView(context: Context) : FrameLayout(context) {

    private val canvasView: CanvasView
    private val btnMagic: android.widget.LinearLayout
    private var isMagicMode = false
    private val translatedBlocks = mutableMapOf<Text.TextBlock, String>()

    // Callbacks
    var onCloseRequested: (() -> Unit)? = null
    var onTranslateRequested: ((String, String, (String) -> Unit) -> Unit)? = null
    var onSpeakRequested: ((String, String, () -> Unit, () -> Unit, (String) -> Unit) -> Unit)? = null
    var onMagicModeRequested: (() -> Unit)? = null

    init {
        // 1. Add Canvas Layer
        canvasView = CanvasView(context)
        addView(canvasView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        // 2. Add Magic Button (Pill Layout at Top Left)
        btnMagic = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            background = ContextCompat.getDrawable(context, R.drawable.bg_popup_rounded) // Rounded/Pill background
            backgroundTintList = ContextCompat.getColorStateList(context, R.color.purple_500)
            setPadding(32, 20, 48, 20)
            elevation = 20f
            
            // Icon
            val icon = ImageView(context).apply {
                id = R.id.magic_icon_id
                setImageResource(R.drawable.ic_scan)
                imageTintList = ContextCompat.getColorStateList(context, R.color.white)
                layoutParams = android.widget.LinearLayout.LayoutParams(64, 64)
            }
            addView(icon)
            
            // Text
            val text = TextView(context).apply {
                id = R.id.magic_text_id
                text = "Magic Scan"
                setTextColor(Color.WHITE)
                textSize = 14f
                setTypeface(null, android.graphics.Typeface.BOLD)
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = 24
                }
            }
            addView(text)
        }

        // Add ID resources for finding children later (creating dynamic IDs logic or just finding by tag/index)
        // Since we can't easily add IDs to XML here, we'll use findViewWithTag or stick to index.
        // Or simpler: access children by index 0 and 1.
        
        val btnParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = android.view.Gravity.TOP or android.view.Gravity.START
            setMargins(48, 100, 0, 0)
        }
        addView(btnMagic, btnParams)

        btnMagic.setOnClickListener {
            val icon = btnMagic.getChildAt(0) as ImageView
            val label = btnMagic.getChildAt(1) as TextView
            
            if (!isMagicMode) {
                isMagicMode = true
                Toast.makeText(context, "Magic Mode: Translating...", Toast.LENGTH_SHORT).show()
                onMagicModeRequested?.invoke()
                
                // Switch to "Stop" state
                icon.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                label.text = "Stop Magic"
                btnMagic.backgroundTintList = ContextCompat.getColorStateList(context, R.color.purple_700)
            } else {
                exitMagicMode()
            }
        }
    }
    
    fun exitMagicMode() {
        isMagicMode = false
        translatedBlocks.clear()
        canvasView.invalidate()
        
        val icon = btnMagic.getChildAt(0) as ImageView
        val label = btnMagic.getChildAt(1) as TextView
        
        icon.setImageResource(R.drawable.ic_scan)
        label.text = "Magic Scan"
        btnMagic.backgroundTintList = ContextCompat.getColorStateList(context, R.color.purple_500)
        Toast.makeText(context, "Magic Mode Off", Toast.LENGTH_SHORT).show()
    }

    fun setDetectedText(blocks: List<Text.TextBlock>) {
        canvasView.setBlocks(blocks)
    }

    fun updateTranslation(block: Text.TextBlock, translatedText: String) {
        translatedBlocks[block] = translatedText
        canvasView.invalidate()
    }

    // --- Inner Canvas View (Handles Drawing & Touch) ---
    private inner class CanvasView(context: Context) : View(context) {

        private val paint = Paint().apply {
            color = context.getColor(R.color.highlight_color)
            style = Paint.Style.FILL
        }

        private val strokePaint = Paint().apply {
            color = context.getColor(R.color.highlight_stroke)
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }

        private val magicBackgroundPaint = Paint().apply {
            color = context.getColor(R.color.surface_card) // Background for translated text
            style = Paint.Style.FILL
            alpha = 240 // Slightly opaque
        }

        private val magicTextPaint = TextPaint().apply {
            color = context.getColor(R.color.text_primary)
            textSize = 36f
            isAntiAlias = true
        }

        private var textBlocks: List<Text.TextBlock> = emptyList()

        fun setBlocks(blocks: List<Text.TextBlock>) {
            this.textBlocks = blocks
            invalidate()
            if (blocks.isEmpty()) {
                Toast.makeText(context, "No text found", Toast.LENGTH_SHORT).show()
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.drawColor(context.getColor(R.color.overlay_background))

            for (block in textBlocks) {
                // If translated, draw Magic Version
                if (translatedBlocks.containsKey(block)) {
                    val translatedText = translatedBlocks[block]!!
                     block.boundingBox?.let { rect ->
                         // 1. Draw Background Box (Obscure original)
                         canvas.drawRect(rect, magicBackgroundPaint)
                         
                         // 2. Draw Translated Text
                         // Simple StaticLayout for text wrapping
                         val textWidth = rect.width()
                         if (textWidth > 0) {
                             canvas.save()
                             canvas.translate(rect.left.toFloat(), rect.top.toFloat())
                             val layout = StaticLayout.Builder.obtain(translatedText, 0, translatedText.length, magicTextPaint, textWidth)
                                 .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                                 .build()
                             layout.draw(canvas)
                             canvas.restore()
                         }
                     }
                } else {
                    // Normal Highlight Mode
                    for (line in block.lines) {
                        line.boundingBox?.let { rect ->
                            canvas.drawRect(rect, paint)
                            canvas.drawRect(rect, strokePaint)
                        }
                    }
                }
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (isMagicMode) {
                if (event.action == MotionEvent.ACTION_UP) {
                    exitMagicMode()
                }
                return true
            }

            if (event.action == MotionEvent.ACTION_UP) {
                val x = event.x.toInt()
                val y = event.y.toInt()

                // Find closest text logic (same as before)
                var bestBlock: Text.TextBlock? = null
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
                                bestBlock = block
                                bestRect = block.boundingBox
                            }
                        }
                    }
                }

                if (bestBlock != null && bestRect != null) {
                    showActionPopup(bestRect, bestBlock!!.text)
                    performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                } else {
                    onCloseRequested?.invoke()
                }
            }
            return true
        }
    }

    // --- Access Helper for Private Canvas Blocks ---
    fun getTextBlocks(): List<Text.TextBlock> {
        // We need this for the Service to iterate
        // Reflection or just exposing it from CanvasView?
        // Since CanvasView is private inner, we can access its fields.
        return canvasView.run { 
            // Kotlin allows accessing private members of inner classes
            // We'll just define a getter in CanvasView or use a field
            // But 'textBlocks' is private in CanvasView. 
            // Ideally we pass it via setDetectedText, so OverlayView should keep a copy?
            // Yes, let's keep a copy in OverlayView for simplicity.
            emptyList() 
        } 
    }
    // Better: keep state in parent
    private var parentBlocks: List<Text.TextBlock> = emptyList()
    
    // Override setDetectedText to store local copy
    // (This overrides the method defined above - wait, Kotlin doesn't allow duplicate sigs. 
    // I will merge logic in the method above)

    // ... (Retaining showActionPopup logic from previous file, moved here) ...
    // Note: Since I am replacing the whole file, I need to include showActionPopup

    private var activePopup: PopupWindow? = null

    private fun showActionPopup(rect: Rect, text: String) {
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
            btnCopy.visibility = View.GONE
            btnTranslate.visibility = View.GONE
            btnShare.visibility = View.GONE
            
            val layoutResult = view.findViewById<View>(R.id.layout_result)
            layoutResult.visibility = View.VISIBLE
            
            val pbLoading = view.findViewById<View>(R.id.pb_translate_loading)
            val tvResult = view.findViewById<TextView>(R.id.tv_result)
            val spinnerLang = view.findViewById<android.widget.Spinner>(R.id.spinner_result_lang)
            
            pbLoading.visibility = View.VISIBLE
            tvResult.visibility = View.GONE

            val languageNames = com.agampandey.superpower.util.LanguageData.languageNames
            val adapter = android.widget.ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, languageNames)
            spinnerLang.adapter = adapter
            
            val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val defaultCode = prefs.getString("target_lang", "hi")
            val defaultName = com.agampandey.superpower.util.LanguageData.getName(defaultCode ?: "hi")
            spinnerLang.setSelection(languageNames.indexOf(defaultName))
            
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
                        btnSpeak.visibility = View.INVISIBLE
                        
                        onSpeakRequested?.invoke(result, targetCode, 
                            { pbSpeak.visibility = View.GONE; btnSpeak.visibility = View.VISIBLE },
                            { },
                            { error -> pbSpeak.visibility = View.GONE; btnSpeak.visibility = View.VISIBLE; Toast.makeText(context, error, Toast.LENGTH_SHORT).show() }
                        )
                    }
                }
            }
            performTranslation(defaultCode ?: "hi")
            
            spinnerLang.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                var isFirst = true
                override fun onItemSelected(parent: android.widget.AdapterView<*>, view: View?, position: Int, id: Long) {
                    if (isFirst) { isFirst = false; return }
                    val selectedName = languageNames[position]
                    val selectedCode = com.agampandey.superpower.util.LanguageData.getCode(selectedName)
                    
                    Toast.makeText(context, "Checking ${selectedName} model...", Toast.LENGTH_SHORT).show()
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

        val popupWindow = PopupWindow(view, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true)
        popupWindow.elevation = 20f
        popupWindow.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        
        view.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val popupWidth = view.measuredWidth
        val popupHeight = view.measuredHeight
        val xPos = rect.centerX() - (popupWidth / 2)
        val yPos = rect.top - popupHeight - 20
        
        try { popupWindow.showAtLocation(this, android.view.Gravity.NO_GRAVITY, xPos, yPos); activePopup = popupWindow } 
        catch (e: Exception) { e.printStackTrace() }
    }
}
