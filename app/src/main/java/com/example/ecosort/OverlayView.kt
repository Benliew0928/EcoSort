package com.example.ecosort

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.roundToInt

class OverlayView(context: Context, attrs: android.util.AttributeSet? = null) : View(context, attrs) {
    private var results: List<AnalysisResult> = emptyList()

    // 🔑 NEW: Map to store the final, definitive label from Gemini (Key: RectF as String, Value: Final Label)
    private val finalLabels = mutableMapOf<String, String>()

    private val boxPaint = Paint().apply {
        color = Color.MAGENTA
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 36f
        typeface = Typeface.DEFAULT_BOLD
    }
    private val backgroundPaint = Paint().apply {
        color = Color.MAGENTA
        style = Paint.Style.FILL
    }

    private var previewWidth: Int = 0
    private var previewHeight: Int = 0

    // NEW: Property to hold the final classification text for non-object status (Optional global message)
    private var finalGlobalClassification: String = ""


    // Modify updateResults to accept these dimensions
    fun updateResults(
        newResults: List<AnalysisResult>,
        frameWidth: Int,
        frameHeight: Int
    ) {
        results = newResults
        this.previewWidth = frameWidth
        this.previewHeight = frameHeight
        // Note: We deliberately DO NOT clear finalLabels here, as we want the Gemini
        // result to persist on the screen for a moment.
        invalidate()
    }

    // 🔑 NEW FUNCTION: Called by ObjectDetectionActivity when Gemini result is ready
    fun updateFinalClassification(rawBoxKey: String, finalLabel: String) {
        // Map the final classification to the unique string representation of the bounding box
        finalLabels[rawBoxKey] = finalLabel
        invalidate() // Forces a redraw to show the new label
    }

    // 5. NEW FUNCTION: Used to display a global message (e.g., "Classification Failed")
    fun updateGlobalClassificationText(text: String) {
        finalGlobalClassification = text
        invalidate()
    }


    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (results.isEmpty() || previewWidth == 0 || previewHeight == 0) {
            // Draw global message if no objects are detected
            if (finalGlobalClassification.isNotEmpty()) {
                val msgPaint = Paint(textPaint).apply { color = Color.YELLOW; textSize = 60f }
                canvas.drawText(finalGlobalClassification, 50f, 150f, msgPaint)
            }
            return
        }

        // ... (Your existing tuning parameters are here) ...
        val expandYFactor = 1.2f
        val nudgeX = 60.0f
        val nudgeY = -75.0f

        // 1. Calculate the Unified Scale Factor
        val scaleX = width.toFloat() / previewWidth.toFloat()
        val scaleY = height.toFloat() / previewHeight.toFloat()
        val scale = Math.min(scaleX, scaleY)

        // 2. Calculate Translation (Offset) to center the smaller image
        val offsetX = (width.toFloat() - (previewWidth * scale)) / 2f
        val offsetY = (height.toFloat() - (previewHeight * scale)) / 2f

        for (r in results) {
            val originalRect = RectF(r.boundingBox)

            // 🔑 CREATE UNIQUE KEY: This key is used to check the finalLabels map.
            val rawBoxKey = originalRect.toString()

            // --- 1. DETERMINE THE LABEL ---
            // If Gemini has a result for this box, use it; otherwise, use the ML Kit label.
            val labelToDisplay = finalLabels[rawBoxKey] ?: r.label

            val originalHeight = originalRect.height() * scale
            val expandedHeight = originalHeight * expandYFactor
            val heightIncrease = (expandedHeight - originalHeight) / 2f

            // 3. APPLY SCALING, OFFSET, AND MANUAL NUDGE
            val scaledRect = RectF(
                (originalRect.left * scale) + offsetX + nudgeX,
                (originalRect.top * scale) + offsetY + nudgeY - heightIncrease,
                (originalRect.right * scale) + offsetX + nudgeX,
                (originalRect.bottom * scale) + offsetY + nudgeY + heightIncrease
            )

            // 4. Draw the Scaled Box
            canvas.drawRect(scaledRect, boxPaint)

            // 5. Draw the Label
            val label = "${labelToDisplay} ${r.areaPercentage.roundToInt()}%" // Use the resolved label
            val textWidth = textPaint.measureText(label)

            canvas.drawRect(
                scaledRect.left,
                scaledRect.top - textPaint.textSize - 10,
                scaledRect.left + textWidth + 20,
                scaledRect.top,
                backgroundPaint
            )
            canvas.drawText(label, scaledRect.left + 10, scaledRect.top - 10, textPaint)
        }
    }
}