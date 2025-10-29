package com.example.ecosort.ml

import android.util.Log

/**
 * Factory for creating the appropriate ObjectDetectorService based on build flavor.
 * This avoids importing flavor-specific classes in common code.
 */
object DetectorFactory {
    
    /**
     * Create the appropriate detector for the current build
     * 
     * @param storeType The store type from BuildConfig
     * @return ObjectDetectorService instance
     */
    fun createDetector(storeType: String): ObjectDetectorService {
        return try {
            when (storeType) {
                "GOOGLE_PLAY" -> {
                    // Use reflection to avoid direct import
                    val clazz = Class.forName("com.example.ecosort.ml.GoogleMLKitDetector")
                    clazz.newInstance() as ObjectDetectorService
                }
                "APP_GALLERY" -> {
                    // Use reflection to avoid direct import
                    val clazz = Class.forName("com.example.ecosort.ml.HmsMLKitDetector")
                    clazz.newInstance() as ObjectDetectorService
                }
                else -> {
                    Log.w("DetectorFactory", "Unknown store type: $storeType, defaulting to Google Play")
                    val clazz = Class.forName("com.example.ecosort.ml.GoogleMLKitDetector")
                    clazz.newInstance() as ObjectDetectorService
                }
            }
        } catch (e: Exception) {
            Log.e("DetectorFactory", "Failed to create detector: ${e.message}", e)
            throw IllegalStateException("Could not create object detector for store type: $storeType", e)
        }
    }
}

