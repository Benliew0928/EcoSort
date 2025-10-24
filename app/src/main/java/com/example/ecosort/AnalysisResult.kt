// AnalysisResult.kt
package com.example.ecosort

import android.graphics.RectF

data class AnalysisResult(
    val boundingBox: RectF,
    val label: String,
    val areaPercentage: Float,
    val confidence: Float = 0f
)