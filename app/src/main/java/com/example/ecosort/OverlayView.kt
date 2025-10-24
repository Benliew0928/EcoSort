package com.example.ecosort

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.roundToInt

class OverlayView(context: Context, attrs: android.util.AttributeSet? = null) : View(context, attrs) {
    private var results: List<AnalysisResult> = emptyList()

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

    // Modify updateResults to accept these dimensions
    fun updateResults(
        newResults: List<AnalysisResult>,
        frameWidth: Int,
        frameHeight: Int
    ) {
        results = newResults
        this.previewWidth = frameWidth
        this.previewHeight = frameHeight
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (results.isEmpty() || previewWidth == 0 || previewHeight == 0) return

        val expandYFactor = 1.2f

        // TUNE THESE VALUES:
        // nudgeX: Positive value shifts the box RIGHT.
        // nudgeY: Negative value shifts the box UP (or positive to shift down).
        // Start with small values (e.g., 20.0f) and adjust until the box aligns.
        val nudgeX = 60.0f // Shift the box slightly RIGHT (increase this to move right)
        val nudgeY = -75.0f // Shift the box slightly UP (decrease this to move up)

        // 1. Calculate the Unified Scale Factor (from previous fix)
        val scaleX = width.toFloat() / previewWidth.toFloat()
        val scaleY = height.toFloat() / previewHeight.toFloat()
        val scale = Math.min(scaleX, scaleY)

        // 2. Calculate Translation (Offset) to center the smaller image
        val offsetX = (width.toFloat() - (previewWidth * scale)) / 2f
        val offsetY = (height.toFloat() - (previewHeight * scale)) / 2f

        for (r in results) {
            val originalRect = RectF(r.boundingBox)

            val originalHeight = originalRect.height() * scale
            val expandedHeight = originalHeight * expandYFactor
            val heightIncrease = (expandedHeight - originalHeight) / 2f

            // 3. APPLY SCALING, OFFSET, AND MANUAL NUDGE
            val scaledRect = RectF(
                (originalRect.left * scale) + offsetX + nudgeX,     // New left + Nudge
                (originalRect.top * scale) + offsetY + nudgeY - heightIncrease,      // New top + Nudge
                (originalRect.right * scale) + offsetX + nudgeX,    // New right + Nudge
                (originalRect.bottom * scale) + offsetY + nudgeY + heightIncrease    // New bottom + Nudge
            )

            // 4. Draw the Scaled Box
            canvas.drawRect(scaledRect, boxPaint)

            // 5. Draw the Label (using the scaled coordinates)
            val label = "${r.label} ${r.areaPercentage.roundToInt()}%"
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
