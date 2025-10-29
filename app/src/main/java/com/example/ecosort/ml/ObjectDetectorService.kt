package com.example.ecosort.ml

import androidx.camera.core.ImageProxy
import com.google.android.gms.tasks.Task

/**
 * Abstraction interface for object detection that works with both
 * Google ML Kit and Huawei HMS ML Kit.
 * 
 * This allows the app to support both Google Play Store and Huawei AppGallery
 * without code duplication.
 */
interface ObjectDetectorService {
    
    /**
     * Initialize the object detector with appropriate settings
     * 
     * @throws Exception if initialization fails
     */
    fun initialize()
    
    /**
     * Process an image frame and detect objects
     * 
     * @param imageProxy The camera frame to analyze
     * @param rotation The rotation degrees of the image
     * @return Task that completes with list of detected objects
     */
    fun detectObjects(imageProxy: ImageProxy, rotation: Int): Task<List<DetectedObjectInfo>>
    
    /**
     * Stop/close the detector and release resources
     */
    fun stop()
    
    /**
     * Check if detector is initialized and ready
     * 
     * @return true if detector is ready to use
     */
    fun isReady(): Boolean
}

