package com.example.ecosort.ml

import android.graphics.Rect

/**
 * Common data class for detected objects that works with both
 * Google ML Kit and HMS ML Kit implementations.
 * 
 * This abstraction allows the app to work with both stores without
 * depending on store-specific SDK classes.
 */
data class DetectedObjectInfo(
    val boundingBox: Rect,
    val trackingId: Int? = null,
    val labels: List<Label> = emptyList()
) {
    /**
     * Label information for detected objects
     */
    data class Label(
        val text: String,
        val confidence: Float,
        val index: Int = 0
    )
}

